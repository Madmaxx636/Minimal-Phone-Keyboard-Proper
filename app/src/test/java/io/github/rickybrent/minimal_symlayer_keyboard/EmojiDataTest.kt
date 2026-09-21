package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmojiDataTest {
	private fun emoji(character: String, name: String = "name") = Emoji(character, "cat", "sub", name, emptyList())

	@Test
	fun keyIgnoresTheVariationSelector() {
		assertEquals(EmojiData.key("❤"), EmojiData.key("❤️"))
		assertEquals("🤷‍♀", EmojiData.key("🤷‍♀️"))
	}

	@Test
	fun keepsTheFormWithTheVariationSelectorInTheFirstPosition() {
		val plain = emoji("🤷‍♀", "plain")
		val selector = emoji("🤷‍♀️", "selector")
		val other = emoji("😀")

		// Whichever comes first in the data, the form with the selector is the one that is kept.
		assertEquals(listOf(selector, other), EmojiData.deduplicate(listOf(plain, selector, other)))
		assertEquals(listOf(selector, other), EmojiData.deduplicate(listOf(selector, plain, other)))
		assertEquals(listOf(other, selector), EmojiData.deduplicate(listOf(other, plain, selector)))
	}

	@Test
	fun keepsDifferentEmojiAndSkinTones() {
		val list = listOf(emoji("👍"), emoji("👍🏽"), emoji("❤️"), emoji("👎"))
		assertEquals(list, EmojiData.deduplicate(list))
	}

	@Test
	fun theRealDataHasNoDuplicatesAfterwards() {
		val all = File("src/main/res/raw/all_emojis.txt").readLines().mapNotNull { line ->
			val parts = line.split("\t")
			if (parts.size == 5) Emoji(parts[0], parts[1], parts[2], parts[3], parts[4].split("|")) else null
		}
		val deduplicated = EmojiData.deduplicate(all)
		assertEquals(deduplicated.size, deduplicated.map { EmojiData.key(it.character) }.toSet().size)
		assertTrue("${all.size} -> ${deduplicated.size}", deduplicated.size < all.size)
		// Nothing but duplicates is lost: every emoji is still there in some form.
		assertEquals(all.map { EmojiData.key(it.character) }.toSet(), deduplicated.map { EmojiData.key(it.character) }.toSet())
	}
}
