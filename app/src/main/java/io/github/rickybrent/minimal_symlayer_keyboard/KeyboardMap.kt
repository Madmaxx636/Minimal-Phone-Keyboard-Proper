package io.github.rickybrent.minimal_symlayer_keyboard

import android.view.KeyEvent

/**
 * One key on the map of the keyboard.
 * @param label What is written on the key.
 * @param keyCode The key that it is, or 0 for a key that has no symbol.
 * @param weight How wide the key is, in key widths.
 * @param pageSwitch Whether this is the key that the BlackBerry keyboard turns its pages with, so that it is marked
 * with the number of the other page.
 */
data class MapKey(val label: String, val keyCode: Int = 0, val weight: Float = 1f, val pageSwitch: Boolean = false)

/** A row of keys, with [margin] key widths of empty space on each side. */
data class MapRow(val keys: List<MapKey>, val margin: Float = 0f)

/** The map of the keyboard: its rows and keys as they sit on the phone. */
object KeyboardMap {
	private fun letter(keyCode: Int) = ('A' + (keyCode - KeyEvent.KEYCODE_A)).toString()

	private fun letters(vararg keyCodes: Int) = keyCodes.map { MapKey(letter(it), it) }

	/** @return The rows of the map, top to bottom, four of them like the keyboard has. */
	fun rows(): List<MapRow> {
		val row1 = letters(
			KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T,
			KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P
		)
		val row2 = letters(
			KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G,
			KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L
		) + MapKey("⌫")
		val row3 = listOf(MapKey("alt", pageSwitch = true)) + letters(
			KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
			KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M
		) + MapKey(".", SymbolPages.DOLLAR_KEY) + MapKey("↵")
		val row4 = listOf(MapKey("⇧"), MapKey("☺"), MapKey("space", weight = 4f), MapKey("sym"), MapKey("⇧"))
		return listOf(MapRow(row1), MapRow(row2), MapRow(row3), MapRow(row4, margin = 1f))
	}
}
