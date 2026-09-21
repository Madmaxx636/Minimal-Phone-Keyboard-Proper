package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinToneTest {
	private fun emoji(character: String) = Emoji(character, "People & Body", "hand", "name", emptyList())

	private val wave = emoji("👋")
	private val waveLight = emoji("👋🏻")
	private val waveMedium = emoji("\uD83D\uDC4B\uD83C\uDFFD")
	private val waveDark = emoji("👋🏿")
	private val smile = emoji("😀")
	// A pointing hand: the base has a variation selector but its variants don't.
	private val point = emoji("\u261D\uFE0F")
	private val pointDark = emoji("☝🏿")
	// Two different tones in one emoji.
	private val handshakeMixed = emoji("🫱🏻" + "\u200D" + "🫲🏿")
	// Bare modifier, as in the Component category.
	private val modifierLight = emoji("🏻")

	private val all = listOf(smile, wave, waveLight, waveMedium, waveDark, point, pointDark, handshakeMixed, modifierLight)

	@Test
	fun allKeepsEverything() {
		assertEquals(all, SkinTone.filter(all, SkinTone.ALL))
	}

	@Test
	fun defaultHidesVariantsButKeepsBareModifiers() {
		assertEquals(listOf(smile, wave, point, modifierLight), SkinTone.filter(all, SkinTone.DEFAULT))
	}

	@Test
	fun unknownPreferenceActsLikeDefault() {
		assertEquals(SkinTone.filter(all, SkinTone.DEFAULT), SkinTone.filter(all, "purple"))
	}

	@Test
	fun toneReplacesTheBaseEmoji() {
		val result = SkinTone.filter(all, "dark")
		assertEquals(listOf(smile, waveDark, pointDark, modifierLight), result)
	}

	@Test
	fun baseIsKeptWhenThereIsNoVariantInTheTone() {
		// There is no light variant of the pointing hand in the list, so the base stays.
		val result = SkinTone.filter(all, "light")
		assertEquals(listOf(smile, waveLight, point, modifierLight), result)
	}

	@Test
	fun mixedToneEmojiOnlyShowWithAll() {
		for (tone in listOf("light", "medium-light", "medium", "medium-dark", "dark", SkinTone.DEFAULT)) {
			assertTrue(tone, handshakeMixed !in SkinTone.filter(all, tone))
		}
		assertTrue(handshakeMixed in SkinTone.filter(all, SkinTone.ALL))
	}

	@Test
	fun tonesInFindsEachModifier() {
		assertEquals(setOf(0x1F3FB, 0x1F3FF), SkinTone.tonesIn(handshakeMixed.character))
		assertEquals(emptySet<Int>(), SkinTone.tonesIn(smile.character))
	}
}
