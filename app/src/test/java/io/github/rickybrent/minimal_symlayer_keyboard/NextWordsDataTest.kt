package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks the built in guesses at the next word that ship with the keyboard.
 */
class NextWordsDataTest {
	private val text = File("src/main/res/raw/next_words.txt").readText()
	private val lines = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split("\t") }
	private val hints = NextWordHints.parse(text)

	@Test
	fun hasStartersAndGeneralWords() {
		assertTrue(hints.starters(3).size == 3)
		assertTrue(hints.generic(3).size == 3)
		assertTrue("I" in hints.starters(50))
	}

	@Test
	fun everyEntryHasAtLeastThreeWords() {
		val short = lines.filter { it.size < 4 }.map { it[0] }
		assertTrue("Entries with fewer than 3 words: $short", short.isEmpty())
	}

	@Test
	fun everyWordIsASingleWord() {
		val bad = lines.flatMap { it }.filter { !Regex("[A-Za-z']+|\\^|\\*").matches(it) }
		assertTrue("Not single words: $bad", bad.isEmpty())
	}

	@Test
	fun noEntryRepeatsAWord() {
		val repeated = lines.filter { line -> line.drop(1).map { it.lowercase() }.let { it.size != it.toSet().size } }.map { it[0] }
		assertTrue("Entries that repeat a word: $repeated", repeated.isEmpty())
	}

	@Test
	fun theKeysAreLowercaseAndUnique() {
		val keys = lines.map { it[0] }.filter { it != "^" && it != "*" }
		assertTrue("Keys that are not lowercase: ${keys.filter { it != it.lowercase() }}", keys.all { it == it.lowercase() })
		assertEquals(keys.size, keys.toSet().size)
	}

	@Test
	fun coversTheMostCommonWords() {
		for (word in listOf("i", "you", "the", "and", "to", "it's", "thanks", "good", "hello", "hey", "what", "going")) {
			assertTrue("$word has no entry", hints.after(word, 1).isNotEmpty())
		}
	}

	@Test
	fun givesSensibleGuesses() {
		assertEquals("morning", hints.after("good", 1).single())
		assertEquals("you", hints.after("thank", 1).single())
		assertTrue("don't" in hints.after("i", 20))
	}
}
