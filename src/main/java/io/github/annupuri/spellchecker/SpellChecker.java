/**
 * SpellChecker.java
 */
package io.github.annupuri.spellchecker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * SpellChecker
 */
public class SpellChecker implements AutoCloseable {
	/** Logger. */
	private static final Logger LOGGER = Logger.getLogger(SpellChecker.class.getName());
	/** Program arguments. */
	private final String[] args;
	/** Dictionary. */
	private final Dictionary dictionary = new Dictionary();

	/**
	 * Constructor.
	 *
	 * @param args the program arguments.
	 */
	public SpellChecker(String[] args) {
		super();
		this.args = args;
	}

	/**
	 * Releases the resources held by the underlying dictionary.
	 */
	@Override
	public void close() {
		dictionary.close();
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%4$s] %2$s: %5$s%n");
		LOGGER.info("----------------------------------------");
		LOGGER.info("Start.");
		int returnCode = 0;
		final long start = System.currentTimeMillis();
		try (SpellChecker spellChecker = new SpellChecker(args)) {
			spellChecker.exec();
		} catch (Exception e) {
			LOGGER.severe("Error occurred.");
			e.printStackTrace();
			returnCode = -1;
		} finally {
			final long end = System.currentTimeMillis();
			LOGGER.info("End. Running time=" + (end - start));
			LOGGER.info("----------------------------------------");
		}
		System.exit(returnCode);
	}

	public void exec() {
		if (ArrayUtils.isEmpty(args)) {
			LOGGER.warning("One or more arguments must be specified.");
			LOGGER.info("Usage1: java -jar spellchecker.jar (one or more keyword(s))");
			LOGGER.info("Usage2: java -jar spellchecker.jar (a file path)");
			return;
		}
		dictionary.loadFromDefaultSources();

		if (args.length == 1) {
			final File file = new File(args[0]);
			if (file.exists()) {
				FileReader fr;
				try {
					fr = new FileReader(file);
				} catch (FileNotFoundException e) {
					LOGGER.severe("Cannot find or open " + args[0] + ".");
					ExceptionUtils.wrapAndThrow(e);
					return;
				}
				final BufferedReader br = new BufferedReader(fr);
				try {
					for (int i = 1; br.ready(); i++) {
						final String keywordLine = br.readLine();
						final String[] keywords = keywordLine.split(" ");
						search(keywords, i);
					}
				} catch (IOException e) {
					LOGGER.severe("Cannot read " + args[0] + ".");
					ExceptionUtils.wrapAndThrow(e);
					return;
				}
				try {
					br.close();
				} catch (IOException e) {
					LOGGER.severe("Cannot close " + args[0] + ".");
					ExceptionUtils.wrapAndThrow(e);
					return;
				}
				return;
			}
		}
		search(args, 1);
	}

	private void search(String[] keywords, int lineNumber) {
		for (String keyword : keywords) {
			search(keyword, lineNumber);
		}
	}

	private void search(String keyword, int lineNumber) {
		final Map<Integer, Set<String>> hitWordsMap = dictionary.search(keyword);
		if (MapUtils.isEmpty(hitWordsMap)) {
			LOGGER.info("Line" + lineNumber + ": " + keyword + " (not in dictionary)");
			return;
		}
		final Set<String> matchWords = hitWordsMap.remove(keyword.length());
		if (!CollectionUtils.isEmpty(matchWords)) {
			LOGGER.info("Line" + lineNumber + ": " + keyword + " (match with " + matchWords + ")");
		}
		for (Map.Entry<Integer, Set<String>> hitWordsMapEntry : hitWordsMap.entrySet()) {
			LOGGER.info("Line" + lineNumber + ": " + keyword + " (diff by "
					+ (hitWordsMapEntry.getKey() - keyword.length()) + " char from "
					+ hitWordsMapEntry.getValue() + ")");
		}
	}
}
