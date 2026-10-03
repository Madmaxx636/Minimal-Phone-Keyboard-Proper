package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardMapTest {
	@Test
	fun thereAreFourRowsLikeTheKeyboard() {
		val rows = KeyboardMap.rows()
		assertEquals(4, rows.size)
		assertEquals("QWERTYUIOP", rows[0].keys.joinToString("") { it.label })
		assertEquals("ASDFGHJKL⌫", rows[1].keys.joinToString("") { it.label })
		assertEquals(listOf("alt", "Z", "X", "C", "V", "B", "N", "M", ".", "↵"), rows[2].keys.map { it.label })
		assertEquals(listOf("⇧", "☺", "space", "sym", "⇧"), rows[3].keys.map { it.label })
	}

	@Test
	fun everyRowFillsTheSameWidth() {
		for (row in KeyboardMap.rows()) {
			assertEquals(10f, row.keys.sumOf { it.weight.toDouble() }.toFloat() + 2 * row.margin, 0.0001f)
		}
	}

	@Test
	fun thereIsNoRowOfNumbers() {
		val labels = KeyboardMap.rows().flatMap { it.keys }.map { it.label }
		assertTrue(labels.none { it.length == 1 && it[0].isDigit() })
	}

	@Test
	fun theKeyOfTheAltPositionIsThePageKey() {
		val pageKeys = KeyboardMap.rows().flatMap { it.keys }.filter { it.pageSwitch }
		assertEquals(listOf("alt"), pageKeys.map { it.label })
	}

	@Test
	fun theDollarSignIsOnTheKeyAfterM() {
		val third = KeyboardMap.rows()[2].keys
		assertEquals(SymbolPages.DOLLAR_KEY, third[third.size - 2].keyCode)
	}
}
