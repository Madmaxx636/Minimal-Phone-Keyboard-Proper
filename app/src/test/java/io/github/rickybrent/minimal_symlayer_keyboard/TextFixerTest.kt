package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Types text the way the keyboard sees it and checks what the whole chain of fixes makes of it.
 */
class TextFixerTest {
	private val dictionary = BaseDictionary.parse(File("src/main/res/raw/common_words.txt").readText())
	private val hints = NextWordHints.parse(File("src/main/res/raw/next_words.txt").readText())
	private val learned = LearnedWords()
	private val fixer = TextFixer(learned, dictionary = { dictionary }, hints = { hints })
	private val neverAfter = "@#/\\._-:+=~"

	private fun typo(vararg corrections: String) = SpellResult(true, corrections.toList())
	private val fine = SpellResult(false, emptyList())

	/**
	 * What the text before the cursor becomes when [text] has just been finished by [boundary], or null if it stays.
	 * [spell] is what the spell checker says about the last word; [fromSystem] is whether it is the system's opinion.
	 */
	private fun fix(
		text: String,
		spell: SpellResult? = null,
		boundary: Char = ' ',
		settings: TextFixer.Settings = TextFixer.Settings(),
		fromSystem: Boolean = true
	): String? {
		val word = WordUtils.trailingWord(text)
		val isTypo = spell?.isTypo == true && !learned.isKnown(word) && !settings.personal.contains(word)
		val systemSaysFine = fromSystem && spell?.isTypo == false
		return fixer.fix(settings, text, boundary, word, spell, systemSaysFine, isTypo, neverAfter)?.text
	}

	private fun settings(
		autoCorrect: AutoCorrectLevel = AutoCorrectLevel.MEDIUM,
		grammar: GrammarLevel = GrammarLevel.FULL,
		autoSpace: Boolean = true,
		splitWords: Boolean = true,
		capitalize: Boolean = true,
		personal: PersonalDictionary = PersonalDictionary.EMPTY
	) = TextFixer.Settings(autoCorrect, grammar, autoSpace, splitWords, capitalize, personal)

	// --- Words that ran together ---

	@Test
	fun lovehahahaBecomesTwoWords() {
		assertEquals("I love hahaha", fix("I lovehahaha", typo()))
		assertEquals("so much love hahaha", fix("so much lovehahaha", typo()))
	}

	@Test
	fun aWordThatStartsAMessageKeepsItsCapital() {
		assertEquals("Love hahaha", fix("Lovehahaha", typo()))
		// And a sentence start is capitalized as any word would be.
		assertEquals("Love hahaha", fix("lovehahaha", typo()))
	}

	@Test
	fun splittingCanBeTurnedOff() {
		assertNull(fix("I lovehahaha", typo(), settings = settings(splitWords = false)))
	}

	@Test
	fun aWordTheSpellCheckerKnowsIsNotSplit() {
		assertNull(fix("I lovehahaha", fine))
	}

	@Test
	fun withoutAnOpinionAnUnknownWordIsStillSplit() {
		assertEquals("I love hahaha", fix("I lovehahaha", null, fromSystem = false))
	}

	@Test
	fun splitWordsAreNotLearnedAsTypos() {
		val result = fixer.fix(settings(), "I lovehahaha", ' ', "lovehahaha", typo(), false, true, neverAfter)!!
		assertNull(result.typo)
		assertNull(result.correction)
	}

	@Test
	fun aTypoThatCouldBeTwoRareWordsIsSpelledNotSplit() {
		assertEquals("It is different", fix("It is diffrent", typo("different")))
		assertEquals("See you tomorrow", fix("See you tommorow", typo("tomorrow")))
		assertEquals("It is different", fix("It is diffrent", typo()))
	}

	@Test
	fun wordsThatRunTogetherAreSplitBeforeSpelling() {
		assertEquals("Good morning", fix("goodmorning", typo("good morning", "goodmorning's")))
	}

	// --- Space after punctuation ---

	@Test
	fun aSpaceIsPutAfterAFullStop() {
		assertEquals("hello. World", fix("hello.world", fine))
		assertEquals("Hello. World", fix("Hello.World", fine))
	}

	@Test
	fun theNextSentenceIsOnlyCapitalizedIfThatIsWanted() {
		assertEquals("hello. world", fix("hello.world", fine, settings = settings(capitalize = false)))
	}

	@Test
	fun aSpaceIsPutAfterACommaWithoutACapital() {
		assertEquals("yes, please", fix("yes,please", fine))
		assertEquals("Yes, please", fix("Yes,please", fine))
	}

	@Test
	fun autoSpaceCanBeTurnedOff() {
		assertNull(fix("hello.world", fine, settings = settings(autoSpace = false, capitalize = false, grammar = GrammarLevel.OFF)))
	}

	@Test
	fun addressesAndFileNamesAreLeftAlone() {
		assertNull(fix("example.com", typo()))
		assertNull(fix("mail me at john@example.com", typo()))
		assertNull(fix("open notes.txt", typo()))
		assertNull(fix("see www.example.com", typo()))
	}

	@Test
	fun aTypoAfterTheMissingSpaceIsFixedToo() {
		assertEquals("hello. World", fix("hello.wrold", typo("world")))
	}

	// --- Spelling ---

	@Test
	fun aTypoIsFixed() {
		assertEquals("I think the", fix("I think teh", typo("the", "tech")))
		assertEquals("It was definitely", fix("It was definately", typo("definitely")))
	}

	@Test
	fun aTypoIsFixedWithoutTheSpellCheckerOfferingAnything() {
		assertEquals("I think the", fix("I think teh", typo()))
		assertEquals("It was definitely", fix("It was definately", typo()))
	}

	@Test
	fun aTypoStartingASentenceGetsACapital() {
		assertEquals("The", fix("teh", typo("the")))
		assertEquals("Hello", fix("helo", typo("hello")))
	}

	@Test
	fun theFixRemembersWhatWasFixed() {
		val result = fixer.fix(settings(), "I think teh", ' ', "teh", typo("the"), true, true, neverAfter)!!
		assertEquals("teh", result.typo)
		assertEquals("the", result.correction)
		assertTrue(result.learn)
	}

	@Test
	fun aTypoThatIsNotFixedHasNoGrammar() {
		// "their" is left as it is because "qwertyx" could not be fixed, so nothing is done to the words before it.
		assertNull(fix("their qwertyx", typo()))
	}

	@Test
	fun handlesAreLeftAlone() {
		assertNull(fix("thanks @teh", typo("the")))
		assertNull(fix("#teh", typo("the")))
	}

	@Test
	fun offDoesNothing() {
		val off = settings(AutoCorrectLevel.OFF, GrammarLevel.OFF, autoSpace = false, splitWords = false, capitalize = false)
		assertNull(fix("I think teh", typo("the"), settings = off))
		assertNull(fix("lovehahaha", typo(), settings = off))
		assertNull(fix("hello.world", fine, settings = off))
		assertNull(fix("their is", fine, settings = off))
	}

	@Test
	fun theUsersHabitIsFollowed() {
		learned.learnCorrection("fone", "phone", 3)
		// The spell checker has nothing to say about it, but it is what the user wants.
		assertEquals("Call my phone", fix("Call my fone", typo()))
	}

	// --- Grammar on top ---

	@Test
	fun grammarIsFixedAfterTheSpelling() {
		assertEquals("there is", fix("their is", fine))
		assertEquals("so there are", fix("so their are", fine))
	}

	@Test
	fun grammarAndSpellingWorkTogether() {
		assertEquals("and they're going", fix("and their going", fine))
		// Only the last word is looked at, the "i" was for its own turn.
		assertEquals("i don't", fix("i dont", typo("dont")))
	}

	@Test
	fun theFirstWordOfASentenceGetsACapital() {
		assertEquals("Hello", fix("hello", fine))
		assertEquals("It's fine. Then", fix("It's fine. then", fine))
		assertNull(fix("hello", fine, settings = settings(capitalize = false)))
	}

	@Test
	fun anEllipsisOrAbbreviationDoesNotStartASentence() {
		assertNull(fix("well... then", fine))
		assertNull(fix("see Dr. smith", fine))
	}

	// --- Shortcuts ---

	private val shortcuts = PersonalDictionary.parse("omw = on my way\nbrb = be right back!\nKubernetes")

	@Test
	fun aShortcutIsExpanded() {
		assertEquals("Okay, on my way", fix("Okay, omw", typo("own"), settings = settings(personal = shortcuts)))
		assertEquals("be right back!", fix("brb", typo(), settings = settings(personal = shortcuts, capitalize = false)))
	}

	@Test
	fun aShortcutTypedWithACapitalKeepsIt() {
		assertEquals("On my way", fix("Omw", typo("own"), settings = settings(personal = shortcuts)))
	}

	@Test
	fun aShortcutIsNotLearned() {
		val result = fixer.fix(settings(personal = shortcuts), "so omw", ' ', "omw", typo("own"), true, true, neverAfter)!!
		assertFalse(result.learn)
		assertNull(result.typo)
	}

	@Test
	fun aShortcutIsNotExpandedAfterAnAtSign() {
		assertNull(fix("mail @omw", typo("own"), settings = settings(personal = shortcuts)))
	}

	@Test
	fun aWordTheUserAddedIsNeverFixed() {
		assertNull(fix("I use Kubernetes", typo("Kubernetes's"), settings = settings(personal = shortcuts)))
	}

	// --- Caps lock and names ---

	@Test
	fun aCapsLockSlipIsFixedForARealWord() {
		assertEquals("So Hello", fix("So hELLO", fine))
		assertEquals("So The", fix("So THe", fine))
	}

	@Test
	fun namesAndAcronymsAreLeftAlone() {
		assertNull(fix("I have an iPad", fine))
		assertNull(fix("so IPad", fine))
		assertNull(fix("using PDFs", fine))
		assertNull(fix("hello NASA", fine))
	}

	@Test
	fun daysAndPlacesGetCapitals() {
		assertEquals("see you Monday", fix("see you monday", fine))
		assertEquals("I live in London", fix("I live in london", fine))
		assertEquals("I like my iPhone", fix("I like my iphone", fine))
	}

	// --- Things that must stay as they are ---

	@Test
	fun ordinaryEnglishIsUntouched() {
		for (text in listOf(
			"I went to the store", "Does he have", "Can she have", "Would it have", "Were you there", "They were going",
			"their working conditions", "over there", "I have gone", "hello world", "We are here", "That is fine",
			"Thanks for the help", "See you soon", "My name is Sam", "It is what it is"
		)) {
			assertNull("\"$text\" became \"${fix(text, fine)}\"", fix(text, fine))
		}
	}
}
