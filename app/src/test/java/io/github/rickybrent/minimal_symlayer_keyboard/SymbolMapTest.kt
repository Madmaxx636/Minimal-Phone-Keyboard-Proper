package io.github.rickybrent.minimal_symlayer_keyboard

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolMapTest {
	private fun real() = SymbolMap.build(
		{ AltKeyMappings.getAltKeyChar(it, false) },
		{ SymKeyMappings.getMapping(it, InputMethodService.DeviceType.MP01) }
	)

	private fun cells(rows: List<SymbolRow>) = rows.flatMap { it.cells }

	private fun hint(rows: List<SymbolRow>, text: String) = cells(rows).first { it.text == text }.hint

	@Test
	fun theRowsAreLaidOutLikeATouchKeyboard() {
		val rows = real()
		assertEquals("1234567890", rows[0].cells.joinToString("") { it.text })
		assertEquals("@#\$_&-+()/", rows[1].cells.joinToString("") { it.text })
		assertEquals("=\\<>*\"':;!", rows[2].cells.joinToString("") { it.text })
	}

	@Test
	fun everySymbolTheAltKeysTypeIsOnTheMapOnce() {
		val typed = AltKeyMappings.getSupportedKeyCodes().mapNotNull { AltKeyMappings.getAltKeyChar(it, false) }.map { it.toString() }
		val onMap = cells(real()).map { it.text }
		for (symbol in typed) assertEquals("$symbol", 1, onMap.count { it == symbol })
	}

	@Test
	fun everySymbolTheSymLayerTypesIsOnTheMapOnce() {
		val onMap = cells(real()).map { it.text }
		val keys = listOf(
			KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O,
			KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_B,
			KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_PERIOD
		)
		for (key in keys) {
			val action = SymKeyMappings.getMapping(key, InputMethodService.DeviceType.MP01)!!.action as SendChar
			assertEquals(action.character, 1, onMap.count { it == action.character })
			action.shiftedCharacter?.let { shifted -> assertEquals(shifted, 1, onMap.count { it == shifted }) }
		}
	}

	@Test
	fun noSymbolIsOnTheMapTwice() {
		val texts = cells(real()).map { it.text }
		assertEquals(texts.size, texts.toSet().size)
	}

	@Test
	fun theKeyThatTypesASymbolIsWrittenUnderIt() {
		val rows = real()
		assertEquals("Alt W", hint(rows, "1"))
		assertEquals("Alt C", hint(rows, "9"))
		assertEquals("Alt \u263a", hint(rows, "0"))
		assertEquals("Alt A", hint(rows, "@"))
		assertEquals("Alt O", hint(rows, "#"))
		assertEquals("Alt Q", hint(rows, "&"))
		assertEquals("Alt N", hint(rows, "?"))
		assertEquals("Alt M", hint(rows, ","))
		assertEquals("Sym I", hint(rows, "("))
		assertEquals("Sym O", hint(rows, ")"))
		assertEquals("Sym N", hint(rows, "/"))
		assertEquals("Sym B", hint(rows, "\\"))
		assertEquals("Sym T", hint(rows, "~"))
		assertEquals("Sym .", hint(rows, ">"))
		assertEquals("Sym P", hint(rows, "\u20ac"))
		assertEquals("Sym \u21e7P", hint(rows, "\u00a3"))
	}

	@Test
	fun rowsAreCenteredAndNoWiderThanTheGrid() {
		for (row in real()) {
			assertTrue(row.cells.size <= 10)
			assertEquals(10f, row.cells.size + 2 * row.margin, 0.0001f)
		}
	}

	@Test
	fun aSymbolBothLayersTypeIsShownWithTheAltKey() {
		val rows = SymbolMap.build(
			{ if (it == KeyEvent.KEYCODE_Q) '#' else null },
			{ if (it == KeyEvent.KEYCODE_W) KeyMapping("#", SendChar("#")) else null }
		)
		assertEquals(listOf("#"), cells(rows).map { it.text })
		assertEquals("Alt Q", cells(rows).single().hint)
	}

	@Test
	fun aSymbolTheLayoutDoesNotKnowStillGetsAPlace() {
		val rows = SymbolMap.build({ if (it == KeyEvent.KEYCODE_Q) '\u03a9' else null }, { null })
		assertEquals(listOf("\u03a9"), cells(rows).map { it.text })
		assertEquals("Alt Q", cells(rows).single().hint)
	}

	@Test
	fun keysThatDoNotTypeASymbolAreNotOnTheMap() {
		// The arrows and the cut, copy and paste keys of the Sym layer are not symbols.
		val texts = cells(real()).map { it.text }
		assertTrue(texts.none { it in listOf("\u2191", "\u2190", "\u2193", "\u2192", "\u21de", "\u21df") })
	}

	@Test
	fun nothingToTypeGivesAnEmptyMap() {
		assertTrue(SymbolMap.build({ null }, { null }).isEmpty())
	}
}
