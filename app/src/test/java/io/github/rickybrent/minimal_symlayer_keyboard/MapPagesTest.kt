package io.github.rickybrent.minimal_symlayer_keyboard

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPagesTest {
	private fun rows(extras: Map<Int, List<String>> = emptyMap(), accents: Map<Int, List<String>> = emptyMap()) = KeyboardMap.build(
		{ AltKeyMappings.getAltKeyChar(it, false) },
		{ SymKeyMappings.getMapping(it, InputMethodService.DeviceType.MP01) },
		extras = { extras[it].orEmpty() },
		accents = { accents[it].orEmpty() }
	)

	private fun key(page: MapPage, label: String) = page.rows.flatMap { it.keys }.first { it.label == label }

	@Test
	fun theFirstTwoPagesAreAltAndSym() {
		val pages = MapPages.build(rows())
		assertEquals(listOf("Alt", "Sym"), pages.map { it.title })
	}

	@Test
	fun altPageHasWhatAltTypes() {
		val page = MapPages.build(rows())[0]
		assertEquals("1", key(page, "W").symbol)
		assertEquals("2", key(page, "E").symbol)
		assertEquals("$", key(page, "P").symbol)
		assertEquals("0", key(page, "☺").symbol)
		// A key that Alt does nothing with is only marked.
		assertEquals("", key(page, "space").symbol)
		assertEquals("", key(page, "sym").symbol)
	}

	@Test
	fun symPageHasWhatSymDoes() {
		val page = MapPages.build(rows())[1]
		assertEquals("↑", key(page, "W").symbol)
		assertEquals("(", key(page, "I").symbol)
		assertEquals("€/£", key(page, "P").symbol)
		assertEquals(">", key(page, ".").symbol)
	}

	@Test
	fun noKeyHasMoreThanOneSymbolOnAPage() {
		// A page has one symbol to a key by construction: the symbol is a single string. Check that no page holds
		// two of the extras of a key.
		val pages = MapPages.build(rows(extras = mapOf(KeyEvent.KEYCODE_T to listOf("a", "b", "c"))))
		for (page in pages) {
			val texts = page.rows.flatMap { it.keys }.filter { it.label == "T" }.map { it.symbol }
			assertEquals(1, texts.size)
		}
		val shown = pages.map { key(it, "T").symbol }
		assertEquals(listOf("_", "~", "a", "b", "c"), shown)
	}

	@Test
	fun eachTimeYouPressAgainIsAPageOfItsOwn() {
		val pages = MapPages.build(rows(extras = mapOf(
			KeyEvent.KEYCODE_W to listOf("&", "↑"),
			KeyEvent.KEYCODE_T to listOf("[", "{", "<")
		)))
		assertEquals(listOf("Alt", "Sym", "Press again 1", "Press again 2", "Press again 3"), pages.map { it.title })
		assertEquals("&", key(pages[2], "W").symbol)
		assertEquals("[", key(pages[2], "T").symbol)
		assertEquals("↑", key(pages[3], "W").symbol)
		assertEquals("{", key(pages[3], "T").symbol)
		// W has no third character, so it is only marked on the third page.
		assertEquals("", key(pages[4], "W").symbol)
		assertEquals("<", key(pages[4], "T").symbol)
	}

	@Test
	fun accentsFollowOnTheirOwnPagesWhenTheyAreOn() {
		val pages = MapPages.build(rows(
			extras = mapOf(KeyEvent.KEYCODE_W to listOf("&")),
			accents = mapOf(KeyEvent.KEYCODE_E to listOf("é", "è"), KeyEvent.KEYCODE_A to listOf("à"))
		))
		assertEquals(listOf("Alt", "Sym", "Press again 1", "Accent 1", "Accent 2"), pages.map { it.title })
		assertEquals("é", key(pages[3], "E").symbol)
		assertEquals("à", key(pages[3], "A").symbol)
		assertEquals("è", key(pages[4], "E").symbol)
		assertEquals("", key(pages[4], "A").symbol)
	}

	@Test
	fun aPageThatNoKeyHasAnythingForIsLeftOut() {
		// With no symbols at all only the mic key is marked, as Alt and it start voice typing.
		val pages = MapPages.build(KeyboardMap.build({ null }, { null }))
		assertEquals(listOf("Alt"), pages.map { it.title })
		assertEquals("mic", pages[0].rows.flatMap { it.keys }.single { it.symbol.isNotEmpty() }.symbol)
	}

	@Test
	fun theKeysAreInTheSamePlacesOnEveryPage() {
		val pages = MapPages.build(rows(extras = mapOf(KeyEvent.KEYCODE_T to listOf("a", "b", "c", "d", "e"))))
		val layout = pages[0].rows.map { row -> row.margin to row.keys.map { it.label to it.weight } }
		for (page in pages) {
			assertEquals(layout, page.rows.map { row -> row.margin to row.keys.map { it.label to it.weight } })
		}
	}

	@Test
	fun everyPageSaysHowToReadIt() {
		val pages = MapPages.build(rows(extras = mapOf(KeyEvent.KEYCODE_W to listOf("&", "↑", "x")), accents = mapOf(KeyEvent.KEYCODE_E to listOf("é"))))
		assertTrue(pages.all { it.note.isNotBlank() })
		assertEquals("Hold a key, then press it again once for this.", pages[2].note)
		assertEquals("Hold a key, then press it again twice for this.", pages[3].note)
		assertEquals("Hold a key, then press it again 3 times for this.", pages[4].note)
		assertEquals("Press the vowel 2 times quickly for this.", pages[5].note)
	}
}
