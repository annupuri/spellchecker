/**
 * DictionaryTest.java
 */
package io.github.annupuri.spellchecker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DictionaryTest {

	private Dictionary dictionary;

	@BeforeEach
	void setUp() {
		dictionary = new Dictionary();
		final String words = String.join("\n", "cat", "Cat", "cats", "dog", "concatenate");
		dictionary.load(new InputStreamReader(
				new ByteArrayInputStream(words.getBytes(StandardCharsets.UTF_8)),
				StandardCharsets.UTF_8));
	}

	@AfterEach
	void tearDown() {
		dictionary.close();
	}

	@Test
	void matchesAreGroupedByWordLength() {
		final Map<Integer, Set<String>> hits = dictionary.search("cat");

		assertEquals(Set.of(3, 4, 11), hits.keySet());
		assertEquals(Set.of("cat", "Cat"), hits.get(3));
		assertEquals(Set.of("cats"), hits.get(4));
		assertEquals(Set.of("concatenate"), hits.get(11));
	}

	@Test
	void matchingIsCaseInsensitive() {
		assertEquals(dictionary.search("cat"), dictionary.search("CAT"));
	}

	@Test
	void shorterWordsAreNeverConsidered() {
		// "cat" (length 3) cannot contain the 4-char keyword, so it must be skipped
		final Map<Integer, Set<String>> hits = dictionary.search("cats");

		assertEquals(Set.of(4), hits.keySet());
		assertEquals(Set.of("cats"), hits.get(4));
	}

	@Test
	void unknownKeywordYieldsNoHits() {
		assertTrue(dictionary.search("xyzzy").isEmpty());
	}

	@Test
	void emptyKeywordIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> dictionary.search(""));
	}

	@Test
	void bundledDictionaryLoadsFromGzip() {
		try (Dictionary bundled = new Dictionary()) {
			bundled.loadBundled();

			final Map<Integer, Set<String>> hits = bundled.search("mango");
			assertEquals(Set.of("mango"), hits.get(5));
			assertEquals(Set.of("mangos"), hits.get(6));
		}
	}

	@Test
	void parallelBucketMergeKeepsAscendingLengthOrder() {
		try (Dictionary bundled = new Dictionary()) {
			bundled.loadBundled();

			// "a" spans many length buckets, so this exercises the multi-worker merge path.
			final var order = new ArrayList<>(bundled.search("a").keySet());
			final var ascending = new ArrayList<>(order);
			Collections.sort(ascending);
			assertEquals(ascending, order, "buckets must merge in ascending word-length order");
			for (int run = 0; run < 20; run++) {
				assertEquals(order, new ArrayList<>(bundled.search("a").keySet()),
						"result order must be identical on every run");
			}
		}
	}
}
