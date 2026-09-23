package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks the built in list of common English words that ships with the keyboard.
 */
class EnglishWordsDataTest {
	private val lines = File("src/main/res/raw/common_words_en.txt").readLines()
	private val base = BaseDictionary.parse(lines.joinToString("\n"))

	@Test
	fun hasTheExpectedNumberOfWords() {
		assertEquals(162070, lines.size)
	}

	@Test
	fun everyLineIsALowercaseWordOrContraction() {
		val bad = lines.filter { !Regex("[a-z]+('[a-z]+)?").matches(it) }
		assertTrue("Unexpected lines: ${bad.take(10)}", bad.isEmpty())
	}

	@Test
	fun hasNoDuplicates() {
		assertEquals(lines.size, lines.toSet().size)
	}

	@Test
	fun theMostCommonWordsComeFirst() {
		val top = lines.take(100)
		for (word in listOf("the", "you", "to", "and", "that", "is", "it's", "don't")) {
			assertTrue("$word is not in the top 100", word in top)
		}
	}

	@Test
	fun completesCommonPrefixesSensibly() {
		assertTrue("help" in base.completions("hel", 3))
		assertTrue("hello" in base.completions("hel", 3))
		assertEquals("don't", base.completions("don", 1).single())
		assertTrue("should" in base.completions("sh", 3))
		assertTrue("tomorrow" in base.completions("tom", 3))
	}

	@Test
	fun leavesOutFragmentsOfContractionsAndBareLetters() {
		for (fragment in listOf("didn", "doesn", "isn", "couldn", "wouldn", "ll", "ve")) {
			assertTrue("$fragment should be left out", fragment !in lines)
		}
		assertTrue(lines.none { it.length < 2 })
	}
}
