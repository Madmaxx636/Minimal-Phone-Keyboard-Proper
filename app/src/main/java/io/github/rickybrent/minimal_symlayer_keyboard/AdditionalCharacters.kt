package io.github.rickybrent.minimal_symlayer_keyboard

import android.view.KeyEvent
import java.text.Normalizer

/**
 * Works out the additional characters of a key from the same tables that the keyboard types with, so that the map
 * of them can not disagree with what the keys do.
 */
object AdditionalCharacters {
	// An accent that is typed as a key of its own is put on the letter of the key it follows: ~ and n make ñ.
	private val combining = mapOf('`' to '̀', '´' to '́', '^' to '̂', '~' to '̃', '¨' to '̈')

	// Markers in the tables that are not characters, but tell what the key does next.
	private val markers = setOf(
		MPSUBST_BYPASS, MPSUBST_NOTHING, MPSUBST_NOMETA, MPSUBST_SHIFT, MPSUBST_TOGGLE_SHIFT, MPSUBST_STR_DOTSPACE,
		// Holding a key gives the character that Alt and the key give first, which the map shows on its own.
		MPSUBST_TOGGLE_ALT, MPSUBST_ALT
	)

	/**
	 * @param table What holding a key gives first and pressing it again gives next (the second level of multipress).
	 * @return What pressing the key again gives, in order and without repeats. What holding it gives first is the
	 * character of Alt and the key, so it is not in the list.
	 */
	fun extras(table: Array<Char>?, keyCode: Int): List<String> = characters(table, keyCode)

	/**
	 * @param table What pressing a key twice quickly gives, and pressing it again gives next (the first level of
	 * multipress, the accents of the language chosen), or null if accents are turned off.
	 * @return The accented letters, in the order that they come, without repeats.
	 */
	fun accents(table: Array<Char>?, keyCode: Int): List<String> = characters(table, keyCode)

	private fun characters(table: Array<Char>?, keyCode: Int): List<String> {
		if (table == null) return emptyList()
		val letter = if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) 'a' + (keyCode - KeyEvent.KEYCODE_A) else ' '
		val result = LinkedHashSet<String>()
		for (entry in table) {
			val text = when {
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
