package io.github.rickybrent.minimal_symlayer_keyboard

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardMapTest {
	private fun real() = KeyboardMap.build(
		{ AltKeyMappings.getAltKeyChar(it, false) },
		{ SymKeyMappings.getMapping(it, InputMethodService.DeviceType.MP01) }
	)

	private fun key(rows: List<MapRow>, label: String) = rows.flatMap { it.keys }.first { it.label == label }

	@Test
	fun thereAreFourRowsLikeTheKeyboard() {
		val rows = real()
		assertEquals(4, rows.size)
		assertEquals("QWERTYUIOP", rows[0].keys.joinToString("") { it.label })
		assertEquals("ASDFGHJKL⌫", rows[1].keys.joinToString("") { it.label })
		assertEquals(listOf("alt", "Z", "X", "C", "V", "B", "N", "M", ".", "↵"), rows[2].keys.map { it.label })
		assertEquals(listOf("⇧", "☺", "space", "sym", "⇧"), rows[3].keys.map { it.label })
	}

	@Test
	fun everyRowFillsTheSameWidth() {
		for (row in real()) {
			assertEquals(10f, row.keys.sumOf { it.weight.toDouble() }.toFloat() + 2 * row.margin, 0.0001f)
		}
	}

	@Test
	fun thereIsNoRowOfNumbers() {
		// The numbers are on W E R, S D F and Z X C, and are written on those keys.
		val labels = real().flatMap { it.keys }.map { it.label }
		assertTrue(labels.none { it.length == 1 && it[0].isDigit() })
	}

	@Test
	fun theNumbersAreWrittenOnTheKeysThatTypeThem() {
		val rows = real()
		assertEquals("1", key(rows, "W").alt)
		assertEquals("2", key(rows, "E").alt)
		assertEquals("3", key(rows, "R").alt)
		assertEquals("4", key(rows, "S").alt)
		assertEquals("5", key(rows, "D").alt)
		assertEquals("6", key(rows, "F").alt)
		assertEquals("7", key(rows, "Z").alt)
		assertEquals("8", key(rows, "X").alt)
		assertEquals("9", key(rows, "C").alt)
		assertEquals("0", key(rows, "☺").alt)
	}

	@Test
	fun theAltSymbolOfEveryKeyIsWhatTheKeyTypes() {
		for (key in real().flatMap { it.keys }) {
			if (key.label.length != 1 || !key.label[0].isLetter()) continue
			val keyCode = KeyEvent.KEYCODE_A + (key.label[0] - 'A')
			assertEquals(key.label, AltKeyMappings.getAltKeyChar(keyCode, false)?.toString().orEmpty(), key.alt)
		}
	}

	@Test
	fun theSymLayerIsWrittenOnEveryKeyThatHasOne() {
		val rows = real()
		assertEquals("↑", key(rows, "W").sym)
		assertEquals("←", key(rows, "A").sym)
		assertEquals("↓", key(rows, "S").sym)
		assertEquals("→", key(rows, "D").sym)
		assertEquals("(", key(rows, "I").sym)
		assertEquals(")", key(rows, "O").sym)
		assertEquals("€/£", key(rows, "P").sym)
		assertEquals("~", key(rows, "T").sym)
		assertEquals(">", key(rows, ".").sym)
	}

	@Test
	fun everyKeyOfTheSymLayerIsOnTheMap() {
		val keys = listOf(
			KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T,
			KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P,
			KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G,
			KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L,
			KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
			KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_PERIOD
		)
		val shown = real().flatMap { it.keys }.map { it.sym }
		for (keyCode in keys) {
			val display = SymKeyMappings.getMapping(keyCode, InputMethodService.DeviceType.MP01)!!.display
			assertTrue(display, display in shown)
		}
	}

	@Test
	fun theKeysThatHaveNoSymbolsAreJustLabelled() {
		val rows = real()
		for (label in listOf("alt", "⌫", "↵", "⇧", "space", "sym")) {
			val key = key(rows, label)
			assertEquals("", key.alt)
			assertEquals("", key.sym)
		}
	}

	@Test
	fun theMicKeyIsMarkedAsOne() {
		assertEquals("mic", key(real(), ".").alt)
	}

	@Test
	fun nothingToTypeStillGivesTheKeyboard() {
		val rows = KeyboardMap.build({ null }, { null })
		assertEquals(4, rows.size)
		assertTrue(rows.flatMap { it.keys }.all { it.alt.isEmpty() || it.alt == "mic" })
	}
}
