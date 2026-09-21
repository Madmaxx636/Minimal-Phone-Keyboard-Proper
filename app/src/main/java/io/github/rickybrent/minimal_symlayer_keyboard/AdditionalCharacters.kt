package io.github.rickybrent.minimal_symlayer_keyboard

import android.view.KeyEvent
import java.text.Normalizer

/**
 * One key on the map of additional characters.
 * @param label The key: a letter, or "Space".
 * @param extras What holding the key gives first and pressing it again gives next, in that order.
 * @param accents What pressing the key twice quickly gives, if accents are turned on, in that order.
 * @param weight How wide the key is, in key widths.
 */
data class CharacterCell(val label: String, val extras: List<String>, val accents: List<String>, val weight: Float = 1f)

/** A row of keys, with [margin] key widths of empty space on each side, as the rows sit on the keyboard. */
data class CharacterRow(val cells: List<CharacterCell>, val margin: Float)

/**
 * Works out what to show on the map of additional characters from the same tables that the keyboard types with,
 * so that the map can not disagree with what the keys do.
 */
object AdditionalCharacters {
	private val letterRows = listOf(
		listOf(
			KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T,
			KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P
		),
		listOf(
			KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G,
			KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L
		),
		listOf(
			KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
			KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M
		)
	)

	// An accent that is typed as a key of its own is put on the letter of the key it follows: ~ and n make ñ.
	private val combining = mapOf('`' to '̀', '´' to '́', '^' to '̂', '~' to '̃', '¨' to '̈')

	// Markers in the tables that are not characters, but tell what the key does next.
	private val markers = setOf(
		MPSUBST_BYPASS, MPSUBST_NOTHING, MPSUBST_NOMETA, MPSUBST_SHIFT, MPSUBST_TOGGLE_SHIFT, MPSUBST_STR_DOTSPACE
	)

	/**
	 * @param longPress What holding a key and pressing it again gives, by key (the second level of multipress).
	 * @param accents What pressing a key twice quickly gives, by key, or null if accents are turned off.
	 * @param alt The character that Alt and the key give, which is what holding the key gives first.
	 * @return The rows of the map, top to bottom.
	 */
	fun build(longPress: Map<Int, Array<Char>>, accents: Map<Int, Array<Char>>?, alt: (Int) -> Char?): List<CharacterRow> {
		val rows = ArrayList<CharacterRow>()
		for (row in letterRows) {
			val cells = row.map { keyCode ->
				val letter = ('a' + (keyCode - KeyEvent.KEYCODE_A))
				CharacterCell(
					label = letter.uppercase(),
					extras = characters(longPress[keyCode], keyCode, letter, alt),
					accents = if (accents == null) emptyList() else characters(accents[keyCode], keyCode, letter, alt)
				)
			}
			rows.add(CharacterRow(cells, (10 - cells.size) / 2f))
		}
		val tab = characters(longPress[KeyEvent.KEYCODE_SPACE], KeyEvent.KEYCODE_SPACE, ' ', alt)
		rows.add(CharacterRow(listOf(CharacterCell("Space", tab, emptyList(), weight = 5f)), 2.5f))
		return rows
	}

	private fun characters(table: Array<Char>?, keyCode: Int, letter: Char, alt: (Int) -> Char?): List<String> {
		if (table == null) return emptyList()
		val result = LinkedHashSet<String>()
		for (entry in table) {
			val text = when {
				entry == MPSUBST_TOGGLE_ALT || entry == MPSUBST_ALT -> alt(keyCode)?.toString()
				entry == MPSUBST_BACKTICK -> "`"
				entry == MPSUBST_CIRCUMFLEX -> "^"
				entry in markers -> null
				entry == '\t' -> "⇥"
				entry in combining -> withAccent(entry, letter)
				else -> entry.toString()
			}
			if (text != null) result.add(text)
		}
		return result.toList()
	}

	/** @return [letter] with [accent] on it as one character, or the accent alone if there is no such letter. */
	private fun withAccent(accent: Char, letter: Char): String {
		val composed = Normalizer.normalize("$letter${combining.getValue(accent)}", Normalizer.Form.NFC)
		return if (composed.length == 1) composed else accent.toString()
	}
}
