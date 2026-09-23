package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks the built in list of common French words that ships with the keyboard.
 */
class FrenchWordsDataTest {
	private val lines = File("src/main/res/raw/common_words_fr.txt").readLines()
	private val base = BaseDictionary.parse(lines.joinToString("\n"))

	@Test
	fun hasTheExpectedNumberOfWords() {
		assertEquals(140112, lines.size)
	}

	@Test
	fun everyLineIsALowercaseFrenchWord() {
		val bad = lines.filter { !Regex("[a-zàâäæçèéêëîïôœùûüÿ']+").matches(it) }
		assertTrue("Unexpected lines: ${bad.take(10)}", bad.isEmpty())
	}

	@Test
	fun hasNoDuplicates() {
		assertEquals(lines.size, lines.toSet().size)
	}

	@Test
	fun theMostCommonWordsComeFirst() {
		val top = lines.take(100)
		for (word in listOf("de", "le", "la", "que", "et", "un", "les")) {
			assertTrue("$word is not in the top 100", word in top)
		}
	}

	@Test
	fun completesCommonPrefixesSensibly() {
		assertTrue("bonjour" in base.completions("bonj", 3))
		assertTrue("merci" in base.completions("mer", 5))
		assertTrue("demain" in base.completions("dem", 5))
	}

	@Test
	fun leavesOutBareLetters() {
		assertTrue(lines.none { it.length < 2 })
	}
}
