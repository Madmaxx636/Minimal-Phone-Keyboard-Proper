package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks the built in list of common Spanish words that ships with the keyboard.
 */
class SpanishWordsDataTest {
	private val lines = File("src/main/res/raw/common_words_es.txt").readLines()
	private val base = BaseDictionary.parse(lines.joinToString("\n"))

	@Test
	fun hasTheExpectedNumberOfWords() {
		assertEquals(100000, lines.size)
	}

	@Test
	fun everyLineIsALowercaseSpanishWord() {
		val bad = lines.filter { !Regex("[a-záéíñóúü']+").matches(it) }
		assertTrue("Unexpected lines: ${bad.take(10)}", bad.isEmpty())
	}

	@Test
	fun hasNoDuplicates() {
		assertEquals(lines.size, lines.toSet().size)
	}

	@Test
	fun theMostCommonWordsComeFirst() {
		val top = lines.take(100)
		for (word in listOf("de", "que", "la", "el", "es", "en", "por", "para")) {
			assertTrue("$word is not in the top 100", word in top)
		}
	}

	@Test
	fun completesCommonPrefixesSensibly() {
		assertTrue("hola" in base.completions("hol", 3))
		assertTrue("gracias" in base.completions("gra", 3))
		assertTrue("mañana" in base.completions("mañ", 3))
	}

	@Test
	fun leavesOutBareLetters() {
		assertTrue(lines.none { it.length < 2 })
	}
}
