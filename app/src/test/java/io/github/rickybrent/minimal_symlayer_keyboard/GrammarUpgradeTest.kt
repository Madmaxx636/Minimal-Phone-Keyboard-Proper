package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules that came after the first grammar release: capitals, verb forms and a few more. */
class GrammarUpgradeTest {
	private fun fix(
		text: String,
		level: GrammarLevel = GrammarLevel.FULL,
		boundary: Char = ' ',
		startsSentence: Boolean = false,
		isWord: (String) -> Boolean = { false }
	): GrammarFix? {
		val words = text.trim().split(" ")
		return Grammar.fix(words.dropLast(1).reversed(), words.last(), boundary, level, startsSentence, isWord)
	}

	private fun replacement(text: String, level: GrammarLevel = GrammarLevel.FULL, boundary: Char = ' ', startsSentence: Boolean = false) =
		fix(text, level, boundary, startsSentence)?.replacement

	// --- Capitals ---

	@Test
	fun daysMonthsAndPlacesTakeCapitals() {
		assertEquals("Monday", replacement("see you on monday"))
		assertEquals("December", replacement("in december"))
		assertEquals("Spanish", replacement("speaks spanish"))
		assertEquals("London", replacement("I live in london"))
		assertEquals("Christmas", replacement("merry christmas"))
	}

	@Test
	fun wordsThatAreAlsoEverydayWordsAreNotCapitalized() {
		for (word in listOf("may", "march", "polish", "turkey", "apple", "orange", "china")) {
			assertNull(word, replacement("the $word"))
		}
	}

	@Test
	fun brandsGetTheirOwnSpelling() {
		assertEquals("iPhone", replacement("my iphone"))
		assertEquals("iPhone", replacement("my Iphone"))
		assertEquals("YouTube", replacement("on youtube"))
		assertNull(replacement("my iPhone"))
		assertNull(replacement("on YouTube"))
	}

	@Test
	fun theFirstWordOfASentenceTakesACapital() {
		assertEquals("Hello", replacement("hello", startsSentence = true))
		assertEquals("Don't", replacement("dont", startsSentence = true))
		assertEquals("I'm", replacement("im", startsSentence = true))
		assertEquals("A lot", replacement("alot", startsSentence = true))
		assertNull(replacement("Hello", startsSentence = true))
		assertNull(replacement("hello", startsSentence = false))
	}

	@Test
	fun aBrandAtTheStartOfASentenceKeepsItsSpelling() {
		assertEquals("iPhone", replacement("iphone", startsSentence = true))
	}

	@Test
	fun theFirstWordOfASentenceIsCapitalizedOnBasicToo() {
		assertEquals("Hello", replacement("hello", GrammarLevel.BASIC, startsSentence = true))
	}

	@Test
	fun startsSentenceIsNotFooledByEllipsesAndAbbreviations() {
		assertTrue(Grammar.startsSentence(""))
		assertTrue(Grammar.startsSentence("Hello. "))
		assertTrue(Grammar.startsSentence("Hello! "))
		assertTrue(Grammar.startsSentence("Really? "))
		assertTrue(Grammar.startsSentence("Line one\n"))
		assertFalse(Grammar.startsSentence("Hello "))
		assertFalse(Grammar.startsSentence("Well... "))
		assertFalse(Grammar.startsSentence("e.g. "))
		assertFalse(Grammar.startsSentence("U.S. "))
		assertFalse(Grammar.startsSentence("Dr. "))
		assertFalse(Grammar.startsSentence("Mr. "))
	}

	@Test
	fun changeCapitalizesOnlyWhenAsked() {
		assertEquals("Hello", Grammar.change("hello", ' ', GrammarLevel.FULL, capitalizeSentences = true)?.text)
		assertNull(Grammar.change("hello", ' ', GrammarLevel.FULL, capitalizeSentences = false))
		assertEquals("It's fine. Then", Grammar.change("It's fine. then", ' ', GrammarLevel.FULL, capitalizeSentences = true)?.text)
		assertNull(Grammar.change("well... then", ' ', GrammarLevel.FULL, capitalizeSentences = true))
	}

	// --- Caps lock ---

	@Test
	fun aCapsLockSlipIsFixedForRealWords() {
		val real = { word: String -> word in setOf("hello", "the", "okay") }
		assertEquals("Hello", fix("so hELLO", isWord = real)?.replacement)
		assertEquals("The", fix("so THe", isWord = real)?.replacement)
		assertEquals("Okay", fix("so OKay", isWord = real)?.replacement)
	}

	@Test
	fun nothingIsChangedForNamesAndAcronyms() {
		val real = { word: String -> word in setOf("hello", "the", "okay") }
		for (word in listOf("iOS", "iPad", "IPad", "eBay", "PDFs", "McDonald", "HELLO", "PhD", "USA")) {
			assertNull(word, fix("so $word", isWord = real))
		}
		// Not a real word: left alone.
		assertNull(fix("so hELLO"))
	}

	// --- Contractions and joined words ---

	@Test
	fun moreContractionsGetTheirApostrophe() {
		assertEquals("I've", replacement("ive"))
		assertEquals("should've", replacement("we shouldve"))
		assertEquals("y'all", replacement("hey yall"))
		assertEquals("o'clock", replacement("at five oclock"))
		assertEquals("ain't", replacement("it aint"))
	}

	@Test
	fun moreJoinedWordsAreSplit() {
		assertEquals("of course", replacement("well ofcourse"))
		assertEquals("thank you", replacement("well thankyou"))
		assertEquals("a little", replacement("just alittle"))
		assertEquals("in front", replacement("stand infront"))
	}

	// --- Verb forms ---

	@Test
	fun theVerbAfterHaveTakesItsParticiple() {
		assertEquals("gone", replacement("I have went"))
		assertEquals("eaten", replacement("she has ate"))
		assertEquals("seen", replacement("we had saw"))
		assertEquals("taken", replacement("I've took"))
		assertEquals("done", replacement("they haven't did"))
		assertEquals("written", replacement("I could've wrote"))
		assertEquals("Gone", replacement("I have Went"))
	}

	@Test
	fun aCorrectParticipleOrAnotherVerbIsUntouched() {
		assertNull(replacement("I have gone"))
		assertNull(replacement("I went"))
		assertNull(replacement("the saw"))
		assertNull(replacement("I have seen"))
		assertNull(replacement("I have a"))
	}

	@Test
	fun verbFormsAreOnlyFixedOnFull() {
		assertNull(replacement("I have went", GrammarLevel.BASIC))
	}

	// --- We're, were and where ---

	@Test
	fun wereGoingAtTheStartOfASentenceIsWereGoing() {
		assertEquals("we're going", replacement("were going"))
		assertEquals("We're going", replacement("Were going"))
		assertEquals("we're not", replacement("but were not"))
		assertEquals("we're going", replacement("so were going"))
	}

	@Test
	fun wereAfterASubjectIsLeftAlone() {
		assertNull(replacement("they were going"))
		assertNull(replacement("we were going"))
		assertNull(replacement("and were going"))
		assertNull(replacement("Were all"))
		assertNull(replacement("Were you"))
	}

	@Test
	fun whereAfterTheyOrWeIsWere() {
		assertEquals("they were", replacement("they where"))
		assertEquals("we were", replacement("we where"))
		assertNull(replacement("show you where"))
		assertNull(replacement("tell me where"))
		assertNull(replacement("they were"))
	}

	// --- Subject and verb ---

	@Test
	fun hasAfterHeOnlyWhenTheClauseStartsThere() {
		assertEquals("he has", replacement("he have"))
		assertEquals("he has", replacement("and he have"))
		assertEquals("she has", replacement("because she have"))
		assertNull(replacement("Does he have"))
		assertNull(replacement("Can she have"))
		assertNull(replacement("would it have"))
		assertNull(replacement("Did he have"))
	}

	@Test
	fun iTakesHaveAndAm() {
		assertEquals("i have", replacement("i has"))
		assertEquals("I have", replacement("I has"))
		assertEquals("I am", replacement("I is"))
		assertEquals("I am", replacement("I are"))
		assertNull(replacement("I am"))
		assertNull(replacement("I have"))
	}

	// --- Too and supposed to ---

	@Test
	fun meToAtTheEndOfASentenceIsMeToo() {
		assertEquals("too", replacement("me to", boundary = '.'))
		assertEquals("too", replacement("you to", boundary = '!'))
		assertNull(replacement("me to", boundary = ' '))
		assertNull(replacement("give it to", boundary = '.'))
		assertNull(replacement("I want to", boundary = '.'))
	}

	@Test
	fun supposeToIsSupposedTo() {
		assertEquals("supposed to", replacement("I was suppose to"))
		assertEquals("supposed to", replacement("they are suppose to"))
		assertEquals("supposed to", replacement("I'm suppose to"))
		assertNull(replacement("I suppose to"))
		assertNull(replacement("I suppose so"))
	}
}
