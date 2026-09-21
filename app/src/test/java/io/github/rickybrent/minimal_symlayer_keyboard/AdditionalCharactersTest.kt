package io.github.rickybrent.minimal_symlayer_keyboard

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdditionalCharactersTest {
	// A few keys as the keyboard has them for holding a key and pressing it again.
	private val longPress: Map<Int, Array<Char>> = hashMapOf(
		KeyEvent.KEYCODE_W to arrayOf(MPSUBST_TOGGLE_ALT, '&', '↑', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
		KeyEvent.KEYCODE_T to arrayOf(MPSUBST_TOGGLE_ALT, '[', '{', '<', '≤', '†', '™', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
		KeyEvent.KEYCODE_N to arrayOf(MPSUBST_TOGGLE_ALT, '~', '¬', '∩', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
		KeyEvent.KEYCODE_F to arrayOf(MPSUBST_TOGGLE_ALT, MPSUBST_CIRCUMFLEX, MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
		KeyEvent.KEYCODE_L to arrayOf(MPSUBST_TOGGLE_ALT, MPSUBST_BACKTICK, MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
		KeyEvent.KEYCODE_G to arrayOf(MPSUBST_TOGGLE_ALT, '•', '•', '·', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf('\t', '⇥', MPSUBST_BYPASS)
	)

	private fun rows(accents: Map<Int, Array<Char>>? = null, alt: (Int) -> Char? = { AltKeyMappings.getAltKeyChar(it, false) }) =
		AdditionalCharacters.build(longPress, accents, alt)

	private fun cell(rows: List<CharacterRow>, label: String) = rows.flatMap { it.cells }.first { it.label == label }

	@Test
	fun theRowsFollowTheKeyboard() {
		val rows = rows()
		assertEquals(4, rows.size)
		assertEquals("QWERTYUIOP", rows[0].cells.joinToString("") { it.label })
		assertEquals("ASDFGHJKL", rows[1].cells.joinToString("") { it.label })
		assertEquals("ZXCVBNM", rows[2].cells.joinToString("") { it.label })
		assertEquals(listOf("Space"), rows[3].cells.map { it.label })
	}

	@Test
	fun everyRowFillsTheSameWidth() {
		for (row in rows()) {
			assertEquals(10f, row.cells.sumOf { it.weight.toDouble() }.toFloat() + 2 * row.margin, 0.0001f)
		}
	}

	@Test
	fun holdingAKeyGivesTheAltCharacterFirst() {
		assertEquals(listOf("1", "&", "↑"), cell(rows(), "W").extras)
		assertEquals(listOf("_", "[", "{", "<", "≤", "†", "™"), cell(rows(), "T").extras)
	}

	@Test
	fun theMarkersThatAreNotCharactersAreLeftOut() {
		val all = rows().flatMap { it.cells }.flatMap { it.extras + it.accents }
		assertTrue(all.none { it.isEmpty() || it[0] in '￰'..'￿' })
	}

	@Test
	fun anAccentTypedOnItsOwnIsPutOnTheLetterOfItsKey() {
		// Holding N and pressing it again gives a tilde on the n.
		assertEquals(listOf("?", "ñ", "¬", "∩"), cell(rows(), "N").extras)
	}

	@Test
	fun theCircumflexAndTheBacktickAreShownAsThemselves() {
		assertEquals(listOf("6", "^"), cell(rows(), "F").extras)
		assertEquals(listOf("\"", "`"), cell(rows(), "L").extras)
	}

	@Test
	fun aCharacterThatComesTwiceIsShownOnce() {
		assertEquals(listOf("=", "•", "·"), cell(rows(), "G").extras)
	}

	@Test
	fun spaceGivesTab() {
		assertEquals(listOf("⇥"), cell(rows(), "Space").extras)
	}

	@Test
	fun aKeyWithNothingExtraIsStillOnTheMap() {
		val q = cell(rows(), "Q")
		assertTrue(q.extras.isEmpty())
		assertTrue(q.accents.isEmpty())
	}

	@Test
	fun aMissingAltCharacterIsSkipped() {
		assertEquals(listOf("&", "↑"), cell(rows(alt = { null }), "W").extras)
	}

	@Test
	fun accentsAreOnlyThereWhenTheyAreTurnedOn() {
		assertTrue(rows(accents = null).flatMap { it.cells }.all { it.accents.isEmpty() })
	}

	@Test
	fun frenchAccentsAreComposedFromTheTemplate() {
		val rows = rows(accents = templates.getValue("fr"))
		assertEquals(listOf("à", "â", "æ"), cell(rows, "A").accents)
		assertEquals(listOf("é", "è", "ê", "ë"), cell(rows, "E").accents)
		assertEquals(listOf("î", "ï"), cell(rows, "I").accents)
		assertEquals(listOf("ô", "œ"), cell(rows, "O").accents)
		assertEquals(listOf("ù", "û", "ü"), cell(rows, "U").accents)
		assertEquals(listOf("ÿ"), cell(rows, "Y").accents)
		assertEquals(listOf("ç"), cell(rows, "C").accents)
		// The space key's entry for the period is not an accent.
		assertTrue(cell(rows, "Space").accents.isEmpty())
	}

	@Test
	fun spanishAccentsAreComposedFromTheTemplate() {
		val rows = rows(accents = templates.getValue("es"))
		assertEquals(listOf("á"), cell(rows, "A").accents)
		assertEquals(listOf("é"), cell(rows, "E").accents)
		assertEquals(listOf("í"), cell(rows, "I").accents)
		assertEquals(listOf("ó"), cell(rows, "O").accents)
		assertEquals(listOf("ú"), cell(rows, "U").accents)
		assertTrue(cell(rows, "B").accents.isEmpty())
	}

	@Test
	fun everyTemplateGivesOnlySingleCharacters() {
		for ((name, template) in templates) {
			val all = rows(accents = template).flatMap { it.cells }.flatMap { it.accents }
			assertTrue("$name has $all", all.all { it.length == 1 })
		}
	}
}
