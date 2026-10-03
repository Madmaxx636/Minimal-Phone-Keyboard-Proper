package io.github.rickybrent.minimal_symlayer_keyboard

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPagesTest {
	private val pages = MapPages.build()

	private fun letters(page: MapPage, row: Int) = page.rows[row].keys.filter { it.label.length == 1 && it.label[0].isLetter() }

	private fun symbolsOf(page: MapPage, row: Int) = letters(page, row).joinToString("") { it.symbol }

	@Test
	fun thereAreTwoPagesAndNothingElse() {
		assertEquals(listOf("Numbers", "Symbols"), pages.map { it.title })
	}

	@Test
	fun theNumbersPageIsTheBlackBerryOne() {
		val page = pages[0]
		assertEquals("#123()_-+@", symbolsOf(page, 0))
		assertEquals("*456/:;'\"", symbolsOf(page, 1))
		assertEquals("789?!,.", symbolsOf(page, 2))
	}

	@Test
	fun theSymbolsPageIsTheBlackBerryOne() {
		val page = pages[1]
		assertEquals("~`{}[]<>^%", symbolsOf(page, 0))
		assertEquals("=÷±•\\|&“”", symbolsOf(page, 1))
		assertEquals("¥€£¿¡«»", symbolsOf(page, 2))
	}

	@Test
	fun theDollarSignIsOnBothPagesOnTheKeyAfterM() {
		for (page in pages) {
			val third = page.rows[2].keys
			assertEquals(".", third[third.size - 2].label)
			assertEquals("$", third[third.size - 2].symbol)
		}
	}

	@Test
	fun everyLetterKeyHasASymbolOnBothPages() {
		for (page in pages) {
			for (row in 0..2) assertTrue(letters(page, row).all { it.symbol.isNotEmpty() })
		}
	}

	@Test
	fun noKeyHasMoreThanOneSymbol() {
		for (page in pages) for (key in page.rows.flatMap { it.keys }) assertTrue(key.symbol.length <= 1)
	}

	@Test
	fun keysThatAreNotForSymbolsHaveNone() {
		for (page in pages) {
			for (label in listOf("⌫", "↵", "⇧", "☺", "space", "sym")) {
				assertTrue(page.rows.flatMap { it.keys }.filter { it.label == label }.all { it.symbol.isEmpty() })
			}
		}
	}

	@Test
	fun thePageKeyNamesTheOtherPage() {
		assertEquals("2/2", pages[0].rows[2].keys[0].label)
		assertEquals("1/2", pages[1].rows[2].keys[0].label)
	}

	@Test
	fun theMapShowsWhatTheKeysType() {
		for ((index, page) in pages.withIndex()) {
			val keyCodes = KeyboardMap.rows().flatMap { it.keys }
			val shown = page.rows.flatMap { it.keys }
			for ((key, pageKey) in keyCodes.zip(shown)) {
				assertEquals(SymbolPages.symbol(index, key.keyCode).orEmpty(), pageKey.symbol)
			}
		}
	}

	@Test
	fun theKeysTypeNothingOnAPageThatIsNotThere() {
		assertNull(SymbolPages.symbol(2, KeyEvent.KEYCODE_Q))
		assertNull(SymbolPages.symbol(0, KeyEvent.KEYCODE_SPACE))
	}

	@Test
	fun everySymbolThereIsIsInTheTwoPages() {
		val typed = SymbolPages.pages.flatMap { it.symbols.values }.toSet()
		val expected = "#123()_-+@*456/:;'\"789?!,.$~`{}[]<>^%=÷±•\\|&“”¥€£¿¡«»".map { it.toString() }.toSet()
		assertEquals(expected, typed)
	}
}
