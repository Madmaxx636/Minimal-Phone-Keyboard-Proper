package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalDictionaryTest {
	@Test
	fun shortcutsAreParsed() {
		val dictionary = PersonalDictionary.parse("omw = on my way\nbrb=be right back!\n  ty   =   thank you  ")
		assertEquals("on my way", dictionary.expansion("omw"))
		assertEquals("be right back!", dictionary.expansion("brb"))
		assertEquals("thank you", dictionary.expansion("ty"))
		assertNull(dictionary.expansion("nope"))
	}

	@Test
	fun shortcutsIgnoreCapitalization() {
		val dictionary = PersonalDictionary.parse("omw = on my way")
		assertEquals("on my way", dictionary.expansion("OMW"))
		assertEquals("on my way", dictionary.expansion("Omw"))
	}

	@Test
	fun anExpansionMayHaveAnEqualsSign() {
		assertEquals("a = b", PersonalDictionary.parse("eq = a = b").expansion("eq"))
	}

	@Test
	fun wordsAreParsed() {
		val dictionary = PersonalDictionary.parse("Kubernetes\nMcDonald, Zoë\n# a note\n")
		assertTrue(dictionary.contains("Kubernetes"))
		assertTrue(dictionary.contains("kubernetes"))
		assertTrue(dictionary.contains("mcdonald"))
		assertTrue(dictionary.contains("Zoë"))
		assertFalse(dictionary.contains("note"))
	}

	@Test
	fun aShortcutCountsAsKnown() {
		assertTrue(PersonalDictionary.parse("omw = on my way").contains("omw"))
	}

	@Test
	fun badLinesAreSkipped() {
		val dictionary = PersonalDictionary.parse("= nothing\nkey =\nhas space = fine\nok = yes\n# comment = ignored")
		assertNull(dictionary.expansion(""))
		assertNull(dictionary.expansion("key"))
		assertNull(dictionary.expansion("has"))
		assertEquals("yes", dictionary.expansion("ok"))
		assertNull(dictionary.expansion("comment"))
	}

	@Test
	fun completionsKeepTheCapitalsOfTheWord() {
		val dictionary = PersonalDictionary.parse("Kubernetes\nkubectl\nKafka")
		assertEquals(listOf("Kubernetes", "kubectl"), dictionary.completions("ku", 5))
		assertEquals(listOf("Kubernetes", "Kubectl"), dictionary.completions("Ku", 5))
		assertEquals(listOf("Kubernetes"), dictionary.completions("kuber", 5))
		assertTrue(dictionary.completions("k", 5).isEmpty())
		assertTrue(dictionary.completions("zz", 5).isEmpty())
	}

	@Test
	fun emptyTextIsEmpty() {
		assertTrue(PersonalDictionary.parse("").isEmpty)
		assertTrue(PersonalDictionary.parse(null).isEmpty)
		assertTrue(PersonalDictionary.parse("  \n# only a note").isEmpty)
		assertFalse(PersonalDictionary.parse("a = b").isEmpty)
	}
}
