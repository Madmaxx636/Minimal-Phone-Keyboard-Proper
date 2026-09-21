package io.github.rickybrent.minimal_symlayer_keyboard

import android.view.KeyEvent

/**
 * One symbol on the map of symbols: what it types, and which key types it.
 * @param hint How to type it on the keyboard: "Alt O" is Alt and then the O key, "Sym I" is Sym and then I.
 */
data class SymbolCell(val text: String, val hint: String)

/** A row of symbols, with [margin] symbol widths of empty space on each side. */
data class SymbolRow(val cells: List<SymbolCell>, val margin: Float)

/**
 * The symbols that the keyboard can type with Alt and with Sym, laid out like the symbols page of a touch keyboard,
 * with the key that types each one written under it. The symbols and the keys come from the same tables that the
 * keys type with, so the map can not disagree with what the keys do.
 */
object SymbolMap {
	// The order a touch keyboard has them in: numbers, then the marks used most, then the brackets and the rest.
	private val order = listOf("1234567890", "@#\$_&-+()/", "=\\<>*\"':;!", "?%,[]{}~`^", "|\u20ac\u00a3")

	private const val COLUMNS = 10

	// The keys that have something to type with Alt or Sym, top left to bottom right.
	private val keys = listOf(
		KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T,
		KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P,
		KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G,
		KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L,
		KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
		KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M, MP01_KEYCODE_EMOJI_PICKER, KeyEvent.KEYCODE_PERIOD
	)

	private fun keyName(keyCode: Int): String = when (keyCode) {
		MP01_KEYCODE_EMOJI_PICKER -> "\u263a"
		KeyEvent.KEYCODE_PERIOD -> "."
		else -> ('A' + (keyCode - KeyEvent.KEYCODE_A)).toString()
	}

	/**
	 * @param alt The character that Alt and a key give, or null if it gives none.
	 * @param sym What the Sym layer does with a key, or null if nothing.
	 * @return The rows of the map, top to bottom. Every symbol that some key types is on it once.
	 */
	fun build(alt: (Int) -> Char?, sym: (Int) -> KeyMapping?): List<SymbolRow> {
		// Where each symbol is typed. What is found first is kept, so Alt goes before Sym.
		val hints = LinkedHashMap<String, String>()
		for (key in keys) {
			alt(key)?.let { hints.putIfAbsent(it.toString(), "Alt ${keyName(key)}") }
		}
		for (key in keys) {
			val action = sym(key)?.action as? SendChar ?: continue
			hints.putIfAbsent(action.character, "Sym ${keyName(key)}")
			action.shiftedCharacter?.let { hints.putIfAbsent(it, "Sym \u21e7${keyName(key)}") }
		}

		val rows = ArrayList<List<SymbolCell>>()
		val placed = HashSet<String>()
		for (line in order) {
			val cells = line.map { it.toString() }.filter { it in hints }.map { SymbolCell(it, hints.getValue(it)) }
			if (cells.isNotEmpty()) rows.add(cells)
			placed.addAll(cells.map { it.text })
		}
		// Whatever the tables have that the order does not mention still gets a place.
		hints.keys.filter { it !in placed }.chunked(COLUMNS).forEach { chunk ->
			rows.add(chunk.map { SymbolCell(it, hints.getValue(it)) })
		}
		return rows.map { SymbolRow(it, (COLUMNS - it.size) / 2f) }
	}
}
