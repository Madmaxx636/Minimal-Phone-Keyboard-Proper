package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks [SkinTone.filter] against the emoji data that the picker really uses.
 */
class SkinToneDataTest {
	private val emojis: List<Emoji> = EmojiData.deduplicate(
		File("src/main/res/raw/all_emojis.txt").readLines().mapNotNull { line ->
			val parts = line.split("\t")
			if (parts.size == 5) Emoji(parts[0], parts[1], parts[2], parts[3], parts[4].split("|")) else null
		}
	)

	private val tones = listOf("light", "medium-light", "medium", "medium-dark", "dark")
	private val toneCodePoint = mapOf("light" to 0x1F3FB, "medium-light" to 0x1F3FC, "medium" to 0x1F3FD, "medium-dark" to 0x1F3FE, "dark" to 0x1F3FF)

	private fun isVariant(e: Emoji) = SkinTone.tonesIn(e.character).isNotEmpty() && e.category != "Component"

	/** The emoji without skin tone modifiers. */
	private fun baseKey(e: Emoji): String {
		val key = StringBuilder()
		// The modifiers are outside the BMP, so this has to look at code points rather than chars.
		EmojiData.key(e.character).codePoints().forEach { if (it !in 0x1F3FB..0x1F3FF) key.appendCodePoint(it) }
		return key.toString()
	}

	@Test
	fun dataWasLoaded() {
		assertTrue("Expected the full emoji list, got ${emojis.size}", emojis.size > 3000)
	}

	@Test
	fun defaultShowsNoVariants() {
		val shown = SkinTone.filter(emojis, SkinTone.DEFAULT)
		assertTrue(shown.none { isVariant(it) })
		// Everything without a tone is still there.
		assertEquals(emojis.count { !isVariant(it) }, shown.size)
	}

	@Test
	fun aToneShowsOnlyThatTone() {
		for (tone in tones) {
			val shown = SkinTone.filter(emojis, tone)
			val expected = setOf(toneCodePoint.getValue(tone))
			assertTrue(tone, shown.filter { isVariant(it) }.all { SkinTone.tonesIn(it.character) == expected })
		}
	}

	@Test
	fun eachEmojiIsShownOrReplacedByExactlyOneVariant() {
		for (tone in tones) {
			val shown = SkinTone.filter(emojis, tone)
			val shownBases = shown.filter { !isVariant(it) }.map { baseKey(it) }.toSet()
			val shownVariants = shown.filter { isVariant(it) }.groupBy { baseKey(it) }
			for (base in emojis.filter { !isVariant(it) }) {
				val key = baseKey(base)
				val variants = shownVariants[key].orEmpty().size
				val baseShown = key in shownBases
				assertTrue("$tone: ${base.name} has base=$baseShown and $variants variants", baseShown != (variants > 0) && variants <= 1)
			}
		}
	}

	@Test
	fun noEmojiIsShownTwice() {
		for (pref in tones + listOf(SkinTone.DEFAULT, SkinTone.ALL)) {
			val shown = SkinTone.filter(emojis, pref).map { it.character }
			assertEquals(pref, shown.size, shown.toSet().size)
		}
	}

	@Test
	fun theThumbsUpFollowsThePreference() {
		val base = "👍"
		fun thumbs(pref: String) = SkinTone.filter(emojis, pref).map { it.character }.filter { it.startsWith(base) }
		assertEquals(listOf(base), thumbs(SkinTone.DEFAULT))
		assertEquals(listOf(base + "🏽"), thumbs("medium"))
		assertEquals(6, thumbs(SkinTone.ALL).size)
	}

	@Test
	fun allShowsEverything() {
		assertEquals(emojis, SkinTone.filter(emojis, SkinTone.ALL))
	}
}
