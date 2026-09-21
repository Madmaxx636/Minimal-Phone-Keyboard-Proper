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
		KeyEvent.KEYCODE_Q to arrayOf(MPSUBST_TOGGLE_ALT, '°', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf('\t', '⇥', MPSUBST_BYPASS),
		MP01_KEYCODE_EMOJI_PICKER to arrayOf(MPSUBST_TOGGLE_ALT, MPSUBST_BYPASS)
	)

	private fun extras(keyCode: Int) = AdditionalCharacters.extras(longPress[keyCode], keyCode)

	@Test
	fun pressingAgainGivesTheCharactersAfterTheAltCharacter() {
		// Holding W gives its Alt character, 1, which is not in the list. Pressing it again gives these.
		assertEquals(listOf("&", "↑"), extras(KeyEvent.KEYCODE_W))
		assertEquals(listOf("[", "{", "<", "≤", "†", "™"), extras(KeyEvent.KEYCODE_T))
	}

	@Test
	fun theMarkersThatAreNotCharactersAreLeftOut() {
		for (keyCode in longPress.keys) {
			assertTrue(extras(keyCode).none { it.isEmpty() || it[0] in '￰'..'￿' })
		}
	}

	@Test
	fun anAccentTypedOnItsOwnIsPutOnTheLetterOfItsKey() {
		// Holding N and pressing it again gives a tilde on the n.
		assertEquals(listOf("ñ", "¬", "∩"), extras(KeyEvent.KEYCODE_N))
	}

	@Test
	fun theCircumflexAndTheBacktickAreShownAsThemselves() {
		assertEquals(listOf("^"), extras(KeyEvent.KEYCODE_F))
		assertEquals(listOf("`"), extras(KeyEvent.KEYCODE_L))
	}

	@Test
	fun aCharacterThatComesTwiceIsShownOnce() {
		assertEquals(listOf("•", "·"), extras(KeyEvent.KEYCODE_G))
	}

	@Test
	fun spaceGivesTab() {
		assertEquals(listOf("⇥"), extras(KeyEvent.KEYCODE_SPACE))
	}

	@Test
	fun aKeyWithNothingMoreGivesNothing() {
		assertEquals(listOf("°"), extras(KeyEvent.KEYCODE_Q))
		assertTrue(extras(MP01_KEYCODE_EMOJI_PICKER).isEmpty())
		assertTrue(AdditionalCharacters.extras(null, KeyEvent.KEYCODE_Z).isEmpty())
	}

	@Test
	fun accentsAreOnlyThereWhenTheyAreTurnedOn() {
		assertTrue(AdditionalCharacters.accents(null, KeyEvent.KEYCODE_A).isEmpty())
	}

	@Test
	fun frenchAccentsAreComposedFromTheTemplate() {
		val french = templates.getValue("fr")
		fun accents(keyCode: Int) = AdditionalCharacters.accents(french[keyCode], keyCode)
		assertEquals(listOf("à", "â", "æ"), accents(KeyEvent.KEYCODE_A))
		assertEquals(listOf("é", "è", "ê", "ë"), accents(KeyEvent.KEYCODE_E))
		assertEquals(listOf("î", "ï"), accents(KeyEvent.KEYCODE_I))
		assertEquals(listOf("ô", "œ"), accents(KeyEvent.KEYCODE_O))
		assertEquals(listOf("ù", "û", "ü"), accents(KeyEvent.KEYCODE_U))
		assertEquals(listOf("ÿ"), accents(KeyEvent.KEYCODE_Y))
		assertEquals(listOf("ç"), accents(KeyEvent.KEYCODE_C))
		// The space key's entry for the period is not an accent.
		assertTrue(accents(KeyEvent.KEYCODE_SPACE).isEmpty())
	}

	@Test
	fun spanishAccentsAreComposedFromTheTemplate() {
		val spanish = templates.getValue("es")
		fun accents(keyCode: Int) = AdditionalCharacters.accents(spanish[keyCode], keyCode)
		assertEquals(listOf("á"), accents(KeyEvent.KEYCODE_A))
		assertEquals(listOf("é"), accents(KeyEvent.KEYCODE_E))
		assertEquals(listOf("í"), accents(KeyEvent.KEYCODE_I))
		assertEquals(listOf("ó"), accents(KeyEvent.KEYCODE_O))
		assertEquals(listOf("ú"), accents(KeyEvent.KEYCODE_U))
		assertTrue(accents(KeyEvent.KEYCODE_B).isEmpty())
	}

	@Test
	fun everyTemplateGivesOnlySingleCharacters() {
		for ((name, template) in templates) {
			for (keyCode in template.keys) {
				val all = AdditionalCharacters.accents(template[keyCode], keyCode)
				assertTrue("$name has $all", all.all { it.length == 1 })
			}
		}
	}
}
