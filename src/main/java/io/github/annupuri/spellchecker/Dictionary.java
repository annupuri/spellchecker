/**
 * Dictionary.java
 */
package io.github.annupuri.spellchecker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Dictionary
 */
public class Dictionary implements AutoCloseable {
	/** Logger. */
	private static final Logger LOGGER = Logger.getLogger(Dictionary.class.getName());
	/**
	 * System property naming an explicit dictionary file; when set it takes precedence
	 * over every other default source.
	 */
	public static final String DICTIONARY_PROPERTY = "spellchecker.dictionary";
	/**
	 * System property overriding the number of worker threads used by {@link #search(String)};
	 * defaults to the number of available processors, clamped to at least one.
	 */
	public static final String SEARCH_THREADS_PROPERTY = "spellchecker.searchThreads";
	/** Classpath name of the gzipped word list bundled in the jar. */
	static final String BUNDLED_RESOURCE = "words.gz";
	/** Conventional Unix system word list, used when present. */
	private static final Path OS_DICTIONARY = Paths.get("/usr/share/dict/words");
	/** Word cache, bucketed by word length. */
	private final ConcurrentSkipListMap<Integer, Set<String>> wordsCache = new ConcurrentSkipListMap<Integer, Set<String>>();
	/** Bounded pool that scans length buckets in parallel; one shared instance per dictionary. */
	private final ExecutorService searchPool = newSearchPool();

	/**
	 * Constructor.
	 */
	public Dictionary() {
		super();
	}

	/**
	 * Creates the fixed-size worker pool for {@link #search(String)}. The size comes from the
	 * {@value #SEARCH_THREADS_PROPERTY} system property, or the available processor count when
	 * unset, and is never less than one. Workers are daemon threads so they never block JVM exit.
	 *
	 * @return the worker pool.
	 */
	private static ExecutorService newSearchPool() {
		final Integer configured = Integer.getInteger(SEARCH_THREADS_PROPERTY);
		final int threads = Math.max(1,
				configured != null ? configured : Runtime.getRuntime().availableProcessors());
		LOGGER.fine(() -> "search thread pool size=" + threads);
		final AtomicInteger counter = new AtomicInteger();
		return Executors.newFixedThreadPool(threads, runnable -> {
			final Thread thread = new Thread(runnable, "dictionary-search-" + counter.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		});
	}

	/**
	 * Shuts the search worker pool down. After this call {@link #search(String)} must not be used.
	 */
	@Override
	public void close() {
		searchPool.shutdown();
	}

	/**
	 * For manual testing.
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%4$s] %2$s: %5$s%n");
		try (Dictionary dictionary = new Dictionary()) {
			dictionary.loadFromDefaultSources();
			final Map<Integer, Set<String>> resultWordsMap = dictionary.search("mango");
			LOGGER.info("resultWordsMap=" + resultWordsMap);
		}
	}

	/**
	 * Loads words from a file.
	 *
	 * @param filePath path to the file.
	 */
	public void load(URL filePath) {
		try {
			load(new File(filePath.toURI()));
		} catch (Exception e) {
			LOGGER.severe("Cannot close " + filePath + ".");
			ExceptionUtils.wrapAndThrow(e);
		}
	}

	/**
	 * Loads words from a file.
	 *
	 * @param filePath path to the file.
	 */
	public void load(String filePath) {
		load(new File(filePath));
	}

	/**
	 * Loads words from a file.
	 *
	 * @param file the file.
	 */
	public void load(File file) {
		try {
			load(new FileReader(file));
		} catch (IOException e) {
			ExceptionUtils.wrapAndThrow(e);
		}
	}

	/**
	 * Loads words from an input stream.
	 *
	 * @param isr the input stream.
	 */
	public void load(InputStreamReader isr) {
		final long start = System.currentTimeMillis();
		try {
			final BufferedReader br = new BufferedReader(isr);
			for (int i = 1; br.ready(); i++) {
				final String word = br.readLine();
				final int lineNumber = i;
				LOGGER.fine(() -> "Line" + lineNumber + "=" + word);
				if (StringUtils.isEmpty(word)) {
					LOGGER.severe("Line" + i + " is empty.");
					continue;
				}
				final Integer wordLength = word.length();
				Set<String> words = wordsCache.get(wordLength);
				if (words == null) {
					words = new LinkedHashSet<String>();
					wordsCache.put(wordLength, words);
				}
				words.add(word);
			}
			LOGGER.fine(() -> "wordCache=" + wordsCache);
			br.close();
		} catch (IOException e) {
			ExceptionUtils.wrapAndThrow(e);
		} finally {
			final long end = System.currentTimeMillis();
			LOGGER.info("Running time=" + (end - start));
		}
	}

	/**
	 * Loads words from the first available default source, in order of precedence:
	 * <ol>
	 * <li>the file named by the {@value #DICTIONARY_PROPERTY} system property, if set;</li>
	 * <li>a file named {@code words} in the current working directory;</li>
	 * <li>{@code /usr/share/dict/words}, if it exists;</li>
	 * <li>the gzipped {@code words.gz} word list bundled on the classpath.</li>
	 * </ol>
	 */
	public void loadFromDefaultSources() {
		final String override = System.getProperty(DICTIONARY_PROPERTY);
		if (StringUtils.isNotBlank(override)) {
			LOGGER.info("Loading dictionary from " + DICTIONARY_PROPERTY + "=" + override);
			load(new File(override));
			return;
		}
		final File workingDirFile = new File("words");
		if (workingDirFile.isFile()) {
			LOGGER.info("Loading dictionary from ./words");
			load(workingDirFile);
			return;
		}
		if (Files.isRegularFile(OS_DICTIONARY)) {
			LOGGER.info("Loading dictionary from " + OS_DICTIONARY);
			load(OS_DICTIONARY.toFile());
			return;
		}
		LOGGER.info("Loading bundled dictionary (" + BUNDLED_RESOURCE + ")");
		loadBundled();
	}

	/**
	 * Loads words from the gzipped {@code words.gz} resource bundled on the classpath.
	 */
	void loadBundled() {
		final InputStream in = getClass().getClassLoader().getResourceAsStream(BUNDLED_RESOURCE);
		if (in == null) {
			throw new IllegalStateException(
					"Bundled dictionary " + BUNDLED_RESOURCE + " not found on the classpath.");
		}
		try (InputStreamReader reader = new InputStreamReader(new GZIPInputStream(in),
				StandardCharsets.UTF_8)) {
			load(reader);
		} catch (IOException e) {
			ExceptionUtils.wrapAndThrow(e);
		}
	}

	/**
	 * Searches the dictionary for a keyword.
	 *
	 * @param keyword the keyword.
	 * @return the search result.
	 */
	public Map<Integer, Set<String>> search(String keyword) {
		final long start = System.currentTimeMillis();
		final Map<Integer, Set<String>> hitWordsMap = new LinkedHashMap<Integer, Set<String>>();
		try {
			if (StringUtils.isEmpty(keyword)) {
				throw new IllegalArgumentException("Keyword(" + keyword + ") is empty.");
			}
			final Integer wordLength = keyword.length();
			final ConcurrentNavigableMap<Integer, Set<String>> wordsMap = wordsCache
					.tailMap(wordLength);
			if (MapUtils.isEmpty(wordsMap)) {
				return hitWordsMap;
			}
			final List<SearchCommand> searchCommands = new ArrayList<Dictionary.SearchCommand>(
					wordsMap.size());
			for (Map.Entry<Integer, Set<String>> wordsMapEntry : wordsMap.entrySet()) {
				searchCommands.add(new SearchCommand(wordsMapEntry.getKey(), wordsMapEntry
						.getValue(), keyword));
			}
			LOGGER.fine(() -> "searchCommands.size=" + searchCommands.size());
			if (searchCommands.size() == 1) {
				// Single bucket: run inline and skip the pool round-trip.
				collectInto(hitWordsMap, searchCommands.get(0).call());
			} else {
				// invokeAll blocks until every bucket has been scanned and returns the futures
				// in submission order, so results merge in ascending word-length order no matter
				// which worker finishes first. The pool is bounded, so at most poolSize buckets
				// are scanned at once.
				for (Future<Map<Integer, Set<String>>> future : searchPool.invokeAll(searchCommands)) {
					collectInto(hitWordsMap, future.get());
				}
			}
		} catch (ExecutionException e) {
			ExceptionUtils.wrapAndThrow(e.getCause() != null ? e.getCause() : e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			ExceptionUtils.wrapAndThrow(e);
		} catch (Exception e) {
			ExceptionUtils.wrapAndThrow(e);
		} finally {
			final long end = System.currentTimeMillis();
			LOGGER.fine(() -> "Running time=" + (end - start));
		}
		return hitWordsMap;
	}

	/**
	 * Merges a per-bucket result map into the accumulating result map, ignoring empty results.
	 *
	 * @param target the accumulating result map.
	 * @param subMap  a single bucket's result.
	 */
	private static void collectInto(Map<Integer, Set<String>> target,
			Map<Integer, Set<String>> subMap) {
		if (!MapUtils.isEmpty(subMap)) {
			target.putAll(subMap);
		}
	}

	/**
	 * SearchCommand
	 */
	private static class SearchCommand implements Callable<Map<Integer, Set<String>>> {
		private final Integer wordLength;
		private final Set<String> words;
		private final String keyword;

		/**
		 * Constructor.
		 * 
		 * @param wordLength
		 * @param words
		 * @param keyword
		 */
		public SearchCommand(Integer wordLength, Set<String> words, String keyword) {
			super();
			this.wordLength = wordLength;
			this.words = words;
			this.keyword = keyword.toLowerCase();
		}

		/**
		 * Scans this bucket for words that contain the keyword as a substring. Reads only
		 * immutable state (the cached word set), so it is safe to run on a worker thread.
		 *
		 * @return a single-entry map {@code {wordLength -> matches}}, or an empty map when
		 *         nothing matches.
		 */
		@Override
		public Map<Integer, Set<String>> call() {
			if (CollectionUtils.isEmpty(words)) {
				return Collections.emptyMap();
			}
			final Set<String> hitWords = new LinkedHashSet<String>();
			for (String word : words) {
				if (word.toLowerCase().contains(keyword)) {
					hitWords.add(word);
				}
			}
			if (CollectionUtils.isEmpty(hitWords)) {
				return Collections.emptyMap();
			}
			final Map<Integer, Set<String>> hitWordsMap = new LinkedHashMap<Integer, Set<String>>();
			hitWordsMap.put(wordLength, hitWords);
			return Collections.unmodifiableMap(hitWordsMap);
		}
	}
}
