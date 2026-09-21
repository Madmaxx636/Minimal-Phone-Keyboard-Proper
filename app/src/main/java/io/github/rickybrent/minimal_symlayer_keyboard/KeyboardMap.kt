package io.github.rickybrent.minimal_symlayer_keyboard

import android.view.KeyEvent

/**
 * One key on the map of the keyboard.
 * @param label What is written on the key.
 * @param alt What Alt and the key type, written in the top left corner like the printed symbols on the keys.
 * @param sym What the Sym layer does with the key, written in the bottom right corner.
 * @param weight How wide the key is, in key widths.
 */
data class MapKey(val label: String, val alt: String = "", val sym: String = "", val weight: Float = 1f)

/** A row of keys, with [margin] key widths of empty space on each side. */
data class MapRow(val keys: List<MapKey>, val margin: Float = 0f)

/**
 * The map of the keyboard: its rows and keys as they sit on the phone, with what Alt and what Sym do with each key.
 * The symbols come from the same tables that the keys type with, so the map can not disagree with the keys.
 */
object KeyboardMap {
	private fun letter(keyCode: Int) = ('A' + (keyCode - KeyEvent.KEYCODE_A)).toString()

	private val top = listOf(
		KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T,
		KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P
	)
	private val home = listOf(
		KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G,
		KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L
	)
	private val bottom = listOf(
		KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
		KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M
	)

	/**
	 * @param alt The character that Alt and a key give, or null if it gives none.
	 * @param sym What the Sym layer does with a key, or null if nothing.
	 * @return The rows of the map, top to bottom, four of them like the keyboard has.
	 */
	fun build(alt: (Int) -> Char?, sym: (Int) -> KeyMapping?): List<MapRow> {
		fun key(keyCode: Int, label: String = letter(keyCode)) =
			MapKey(label, alt(keyCode)?.toString().orEmpty(), sym(keyCode)?.display.orEmpty())

		val row1 = top.map { key(it) }
		val row2 = home.map { key(it) } + MapKey("⌫")
		val row3 = listOf(MapKey("alt")) + bottom.map { key(it) } + key(KeyEvent.KEYCODE_PERIOD, ".").copy(alt = "mic") + MapKey("↵")
		val row4 = listOf(
			MapKey("⇧"),
			key(MP01_KEYCODE_EMOJI_PICKER, "☺"),
			MapKey("space", weight = 4f),
			MapKey("sym"),
			MapKey("⇧")
		)
		return listOf(MapRow(row1), MapRow(row2), MapRow(row3), MapRow(row4, margin = 1f))
	}
}
