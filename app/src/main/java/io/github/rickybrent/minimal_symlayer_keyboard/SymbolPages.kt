package io.github.rickybrent.minimal_symlayer_keyboard

import android.view.KeyEvent

/**
 * What the keys type after a tap of Sym, laid out like the symbol keyboard of a BlackBerry: a page of numbers and
 * punctuation, and a page of other symbols, on the same keys. The map of the keyboard shows these pages and the keys
 * type from them, so the map can not disagree with the keys. These are all the symbols there are, nothing else.
 */
object SymbolPages {
	class Page(val title: String, val note: String, val symbols: Map<Int, String>)

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

	/** The key after M, which the BlackBerry keyboard gives to the dollar sign. */
	const val DOLLAR_KEY = KeyEvent.KEYCODE_PERIOD

	private fun page(title: String, note: String, topRow: List<String>, homeRow: List<String>, bottomRow: List<String>): Page {
		val symbols = LinkedHashMap<Int, String>()
		top.forEachIndexed { i, keyCode -> symbols[keyCode] = topRow[i] }
		home.forEachIndexed { i, keyCode -> symbols[keyCode] = homeRow[i] }
		bottom.forEachIndexed { i, keyCode -> symbols[keyCode] = bottomRow[i] }
		symbols[DOLLAR_KEY] = "$"
		return Page(title, note, symbols)
	}

	val pages: List<Page> = listOf(
		page(
			"Numbers", "Press a key to type what is on it.",
			listOf("#", "1", "2", "3", "(", ")", "_", "-", "+", "@"),
			listOf("*", "4", "5", "6", "/", ":", ";", "'", "\""),
			listOf("7", "8", "9", "?", "!", ",", ".")
		),
		page(
			"Symbols", "Press a key to type what is on it.",
			listOf("~", "`", "{", "}", "[", "]", "<", ">", "^", "%"),
			listOf("=", "÷", "±", "•", "\\", "|", "&", "“", "”"),
			listOf("¥", "€", "£", "¿", "¡", "«", "»")
		)
	)

	/** @return What [keyCode] types on page [page] (counting from 0), or null if it types nothing there. */
	fun symbol(page: Int, keyCode: Int): String? = pages.getOrNull(page)?.symbols?.get(keyCode)
}
