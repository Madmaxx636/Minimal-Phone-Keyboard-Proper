package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordUtilsTest {
	@Test
	fun trailingWord() {
		assertEquals("world", WordUtils.trailingWord("hello world"))
		assertEquals("", WordUtils.trailingWord("hello world "))
		assertEquals("", WordUtils.trailingWord("hello."))
		assertEquals("don't", WordUtils.trailingWord("I don't"))
		assertEquals("don’t", WordUtils.trailingWord("I don’t"))
		assertEquals("café", WordUtils.trailingWord("un café"))
		assertEquals("", WordUtils.trailingWord(""))
	}

	@Test
	fun trailingWordIgnoresLeadingQuote() {
		assertEquals("hello", WordUtils.trailingWord("say 'hello"))
	}

	@Test
	fun trailingWordIgnoresPartsOfLongerTokens() {
		assertEquals("", WordUtils.trailingWord("abc123d"))
		assertEquals("", WordUtils.trailingWord("snake_case"))
	}

	@Test
	fun wordBefore() {
		assertEquals("hello", WordUtils.wordBefore("hello world"))
		assertEquals("hello", WordUtils.wordBefore("hello "))
		assertEquals("hello", WordUtils.wordBefore("say hello  "))
		assertEquals("a", WordUtils.wordBefore("this is a test"))
		assertEquals("I", WordUtils.wordBefore("then I am"))
	}

	@Test
	fun wordBeforeStopsAtPunctuationAndTheStart() {
		assertEquals("", WordUtils.wordBefore("Hello. "))
		assertEquals("", WordUtils.wordBefore("Hello. World"))
		assertEquals("", WordUtils.wordBefore("hello, world"))
		assertEquals("", WordUtils.wordBefore("hello"))
		assertEquals("", WordUtils.wordBefore("hello."))
		assertEquals("", WordUtils.wordBefore(" world"))
		assertEquals("", WordUtils.wordBefore(""))
		assertEquals("", WordUtils.wordBefore("line one\nword"))
	}

	@Test
	fun matchCase() {
		assertEquals("the", WordUtils.matchCase("teh", "the"))
		assertEquals("The", WordUtils.matchCase("Teh", "the"))
		assertEquals("THE", WordUtils.matchCase("TEH", "the"))
		assertEquals("iPhone", WordUtils.matchCase("iphone", "iPhone"))
		assertEquals("Paris", WordUtils.matchCase("par", "Paris"))
		assertEquals("Paris", WordUtils.matchCase("Par", "Paris"))
	}
}

class LearnedWordsTest {
	private fun words(vararg pairs: Pair<String, Int>) = LearnedWords().also { w -> pairs.forEach { w.learn(it.first, it.second) } }

	@Test
	fun aWordIsSuggestedAfterOneUseButOnlyTrustedAfterTwo() {
		val w = LearnedWords()
		w.learn("keyboard")
		assertEquals(listOf("keyboard"), w.completions("key", 3))
		assertFalse(w.isKnown("keyboard"))
		w.learn("Keyboard")
		assertTrue(w.isKnown("KEYBOARD"))
		assertEquals(listOf("keyboard"), w.completions("key", 3))
	}

	@Test
	fun completionsAreOrderedByUse() {
		// "then" and "there" were used equally often, and "there" was used last.
		val w = words("their" to 5, "then" to 9, "there" to 9, "theory" to 2)
		assertEquals(listOf("there", "then", "their"), w.completions("the", 3))
		assertEquals(listOf("there"), w.completions("the", 1))
	}

	@Test
	fun completionsExcludeTheWordItselfAndShortPrefixes() {
		val w = words("the" to 9, "then" to 9)
		assertEquals(listOf("then"), w.completions("the", 3))
		assertEquals(emptyList<String>(), w.completions("t", 3))
	}

	@Test
	fun completionsKeepTheMostUsedCapitalization() {
		val w = words("Paris" to 4, "paris" to 1)
		assertEquals(listOf("Paris"), w.completions("par", 3))
		assertEquals(listOf("the"), words("The" to 3, "the" to 20).completions("th", 3))
	}

	@Test
	fun completionsFollowTheCapitalizationTyped() {
		val w = words("hello" to 3)
		assertEquals(listOf("Hello"), w.completions("He", 3))
		assertEquals(listOf("HELLO"), w.completions("HE", 3))
	}

	@Test
	fun ignoresWhatIsNotAWord() {
		val w = LearnedWords()
		w.learn("a", 5)
		w.learn("abc123", 5)
		w.learn("two words", 5)
		w.learn("ok", 0)
		assertEquals(0, w.size)
		assertFalse(w.isDirty)
	}

	@Test
	fun roundTripsThroughText() {
		val w = words("Paris" to 4, "paris" to 1, "hello" to 3)
		val copy = LearnedWords()
		copy.load(w.serialize())
		assertEquals(5, copy.count("paris"))
		assertEquals(3, copy.count("hello"))
		assertEquals(listOf("Paris"), copy.completions("par", 3))
		assertFalse(copy.isDirty)
	}

	@Test
	fun loadSkipsBrokenLines() {
		val w = LearnedWords()
		w.load("hello\t3\nnonsense\nbad\tx\n\nworld\t2\n")
		assertEquals(3, w.count("hello"))
		assertEquals(2, w.count("world"))
		assertEquals(2, w.size)
	}

	@Test
	fun forgetsTheLeastUsedWhenFull() {
		val w = LearnedWords(maxWords = 10)
		for (i in 0 until 10) w.learn("word" + ('a' + i), 10 + i)
		w.learn("rare", 1)
		assertTrue(w.size <= 10)
		assertEquals(0, w.count("rare"))
		assertTrue(w.count("wordj") > 0)
	}

	@Test
	fun clearMarksTheChange() {
		val w = words("hello" to 3)
		w.markSaved()
		w.clear()
		assertEquals(0, w.size)
		assertTrue(w.isDirty)
	}
}

class UnrecognizedWordTest {
	@Test
	fun aWordIsLearnedAfterBeingSeenMoreThanTwice() {
		val w = LearnedWords()
		assertFalse(w.sawUnrecognized("ubreakifix"))
		assertFalse(w.isKnown("ubreakifix"))
		assertFalse(w.sawUnrecognized("ubreakifix"))
		assertFalse(w.isKnown("ubreakifix"))
		assertTrue(w.sawUnrecognized("ubreakifix"))
		assertEquals(0, w.count("ubreakifix"))
		// The caller learns it once this returns true, the same as tapping its suggestion would.
		w.learn("ubreakifix", 3)
		assertTrue(w.isKnown("ubreakifix"))
	}

	@Test
	fun countingIsCaseInsensitiveAndIgnoresShortOrInvalidWords() {
		val w = LearnedWords()
		w.sawUnrecognized("Ubreakifix")
		w.sawUnrecognized("UBREAKIFIX")
		assertTrue(w.sawUnrecognized("ubreakifix"))
		assertFalse(w.sawUnrecognized("a"))
		assertFalse(w.sawUnrecognized("two words"))
	}

	@Test
	fun onlyTriggersOnceThenStartsOverIfSeenAgain() {
		val w = LearnedWords()
		w.sawUnrecognized("zerith")
		w.sawUnrecognized("zerith")
		assertTrue(w.sawUnrecognized("zerith"))
		assertFalse(w.sawUnrecognized("zerith"))
		assertFalse(w.sawUnrecognized("zerith"))
		assertTrue(w.sawUnrecognized("zerith"))
	}

	@Test
	fun countsSurviveASaveAndLoad() {
		val w = LearnedWords()
		w.sawUnrecognized("zerith")
		val copy = LearnedWords()
		copy.load(w.serialize())
		// One more use after the reload is the third time overall, which is enough to learn it.
		assertFalse(copy.sawUnrecognized("zerith"))
		assertTrue(copy.sawUnrecognized("zerith"))
	}

	@Test
	fun doesNotPileUpForever() {
		val w = LearnedWords()
		// A long run of distinct words that are each only seen once should not grow forever or crash.
		for (i in 0 until LearnedWords.MAX_UNRECOGNIZED + 500) assertFalse(w.sawUnrecognized("word" + i))
		// A word typed a normal number of times still works as expected afterwards.
		w.sawUnrecognized("zerith")
		w.sawUnrecognized("zerith")
		assertTrue(w.sawUnrecognized("zerith"))
	}
}

class LearnTextTest {
	@Test
	fun learnsEveryWordWithItsCount() {
		val w = LearnedWords()
		assertEquals(6, w.learnText("the cat and the dog and"))
		assertEquals(2, w.count("the"))
		assertEquals(2, w.count("and"))
		assertEquals(1, w.count("cat"))
		assertEquals(listOf("the"), w.completions("th", 3))
	}

	@Test
	fun learnsWhichWordFollowsWhich() {
		val w = LearnedWords()
		w.learnText("good morning and good night and good morning")
		assertEquals(listOf("morning", "night"), w.nextWords("good", 3))
		assertEquals(listOf("good"), w.nextWords("and", 3))
	}

	@Test
	fun punctuationAndLineBreaksEndARun() {
		val w = LearnedWords()
		w.learnText("Hello there. Bye now, friend\nlater on")
		assertEquals(listOf("there"), w.nextWords("hello", 3))
		assertEquals(emptyList<String>(), w.nextWords("there", 3))
		assertEquals(listOf("now"), w.nextWords("bye", 3))
		assertEquals(emptyList<String>(), w.nextWords("now", 3))
		assertEquals(emptyList<String>(), w.nextWords("friend", 3))
		assertEquals(listOf("on"), w.nextWords("later", 3))
	}

	@Test
	fun spacesAndTabsKeepARunGoing() {
		val w = LearnedWords()
		w.learnText("one   two\tthree\u00a0four")
		assertEquals(listOf("two"), w.nextWords("one", 3))
		assertEquals(listOf("three"), w.nextWords("two", 3))
		assertEquals(listOf("four"), w.nextWords("three", 3))
	}

	@Test
	fun keepsApostrophesInsideWordsAndDropsQuotes() {
		val w = LearnedWords()
		w.learnText("I don't know 'maybe' it\u2019s fine")
		assertEquals(1, w.count("don't"))
		assertEquals(1, w.count("maybe"))
		assertEquals(1, w.count("it\u2019s"))
		assertEquals(listOf("don't"), w.nextWords("I", 3))
	}

	@Test
	fun shortWordsMakePairsButAreNotCompleted() {
		val w = LearnedWords()
		w.learnText("this is a test")
		assertEquals(listOf("a"), w.nextWords("is", 3))
		assertEquals(listOf("test"), w.nextWords("a", 3))
		assertEquals(0, w.count("a"))
	}

	@Test
	fun skipsNumbersAndTokensThatAreNotWords() {
		val w = LearnedWords()
		assertEquals(3, w.learnText("call 555 1234 abc123def snake_case now ok"))
		assertEquals(0, w.count("abc"))
		assertEquals(0, w.count("snake"))
		assertEquals(0, w.count("case"))
		assertEquals(1, w.count("now"))
		// A skipped token also ends the run, so nothing follows "call".
		assertEquals(emptyList<String>(), w.nextWords("call", 3))
		assertEquals(listOf("ok"), w.nextWords("now", 3))
	}

	@Test
	fun addsToWhatIsAlreadyLearned() {
		val w = LearnedWords()
		w.learn("keyboard", 2)
		w.learnText("keyboard layout")
		assertEquals(3, w.count("keyboard"))
		assertTrue(w.isDirty)
	}

	@Test
	fun survivesASaveAndLoad() {
		val w = LearnedWords()
		w.learnText("the quick brown fox jumps over the lazy dog. The quick one.")
		val copy = LearnedWords()
		copy.load(w.serialize())
		assertEquals(w.size, copy.size)
		assertEquals(w.pairSize, copy.pairSize)
		assertEquals(listOf("quick"), copy.nextWords("the", 3).take(1))
	}

	@Test
	fun bigTextsAreFast() {
		val text = (0 until 200_000).joinToString(" ") { "word" + (it % 5000).toString(36).map { c -> 'a' + (c.code % 26) }.joinToString("") }
		val start = System.nanoTime()
		val w = LearnedWords()
		w.learnText(text)
		val millis = (System.nanoTime() - start) / 1_000_000
		assertTrue("took $millis ms", millis < 5_000)
		assertTrue(w.size > 0)
	}
}

class NextWordTest {
	private fun learned(vararg pairs: Triple<String, String, Int>) = LearnedWords().also { w ->
		pairs.forEach { w.learnPair(it.first, it.second, it.third) }
	}

	@Test
	fun mostUsedFirst() {
		val w = learned(Triple("i", "am", 3), Triple("i", "was", 5), Triple("i", "will", 1))
		assertEquals(listOf("was", "am", "will"), w.nextWords("i", 3))
		assertEquals(listOf("was"), w.nextWords("i", 1))
	}

	@Test
	fun tiesGoToTheMostRecent() {
		val w = LearnedWords()
		w.learnPair("good", "morning")
		w.learnPair("good", "night")
		w.learnPair("good", "luck")
		assertEquals(listOf("luck", "night", "morning"), w.nextWords("good", 3))
		// Typing an older one again makes it the most recent.
		w.learnPair("good", "morning")
		assertEquals(listOf("morning", "luck", "night"), w.nextWords("good", 3))
	}

	@Test
	fun unknownWordHasNoNextWords() {
		val w = learned(Triple("i", "am", 3))
		assertEquals(emptyList<String>(), w.nextWords("you", 3))
		assertEquals(emptyList<String>(), w.nextWords("i", 0))
	}

	@Test
	fun ignoresCaseOfThePreviousWordAndKeepsTheCapitalizationOfTheNext() {
		val w = LearnedWords()
		w.learnPair("Live", "in")
		w.learnPair("in", "Paris")
		w.learnPair("then", "I")
		assertEquals(listOf("in"), w.nextWords("LIVE", 3))
		assertEquals(listOf("Paris"), w.nextWords("in", 3))
		assertEquals(listOf("I"), w.nextWords("Then", 3))
	}

	@Test
	fun shortWordsCanBePredicted() {
		val w = learned(Triple("this", "is", 2), Triple("is", "a", 2))
		assertEquals(listOf("a"), w.nextWords("is", 3))
	}

	@Test
	fun ignoresWhatIsNotAWord() {
		val w = LearnedWords()
		w.learnPair("", "word")
		w.learnPair("word", "")
		w.learnPair("two words", "x")
		w.learnPair("a1", "b")
		w.learnPair("...", "b")
		w.learnPair("hello", "world", 0)
		assertEquals(0, w.pairSize)
		assertFalse(w.isDirty)
	}

	@Test
	fun pairsDoNotCountAsWords() {
		val w = learned(Triple("hello", "world", 4))
		assertEquals(0, w.size)
		assertFalse(w.isKnown("hello"))
		assertTrue(w.isDirty)
	}

	@Test
	fun roundTripsThroughText() {
		val w = LearnedWords()
		w.learn("hello", 3)
		w.learnPair("good", "morning", 2)
		w.learnPair("good", "night")
		w.learnPair("then", "I")
		val copy = LearnedWords()
		copy.load(w.serialize())
		assertEquals(3, copy.pairSize)
		assertEquals(3, copy.count("hello"))
		assertEquals(listOf("morning", "night"), copy.nextWords("good", 3))
		assertEquals(listOf("I"), copy.nextWords("then", 3))
		assertFalse(copy.isDirty)
		// What is learned after loading is more recent than what was loaded.
		copy.learnPair("good", "luck", 2)
		assertEquals("luck", copy.nextWords("good", 3)[0])
	}

	@Test
	fun loadSkipsBrokenPairs() {
		val w = LearnedWords()
		w.load("a\tb\tx\t1\tb\na\tb\t1\ty\tb\none two three\tb\t1\t1\tb\nfine\tpair\t2\t5\tpair\n")
		assertEquals(1, w.pairSize)
		assertEquals(listOf("pair"), w.nextWords("fine", 3))
	}

	@Test
	fun forgetsTheLeastUsedPairsWhenFull() {
		val w = LearnedWords(maxWords = 100, maxPairs = 10)
		for (i in 0 until 10) w.learnPair("first", "word" + ('a' + i), 10 + i)
		w.learnPair("first", "rare")
		assertTrue(w.pairSize <= 10)
		assertFalse("rare" in w.nextWords("first", 20))
		assertTrue("wordj" in w.nextWords("first", 20))
	}

	@Test
	fun clearForgetsPairsToo() {
		val w = learned(Triple("i", "am", 3))
		w.markSaved()
		w.clear()
		assertEquals(0, w.pairSize)
		assertEquals(emptyList<String>(), w.nextWords("i", 3))
		assertTrue(w.isDirty)
	}
}

class SuggestionBuilderTest {
	private val learned = LearnedWords().also {
		it.learn("keyboard", 4)
		it.learn("keyed", 3)
		it.learn("Lane", 3)
	}

	@Test
	fun nothingForShortWords() {
		assertEquals(emptyList<Suggestion>(), SuggestionBuilder.build("a", null, learned))
		assertEquals(emptyList<Suggestion>(), SuggestionBuilder.build("", null, learned))
	}

	@Test
	fun completionsWhenThereIsNoTypo() {
		val result = SuggestionBuilder.build("key", SpellResult(false, emptyList()), learned)
		assertEquals(listOf(Suggestion("keyboard", Suggestion.Kind.COMPLETION), Suggestion("keyed", Suggestion.Kind.COMPLETION)), result)
	}

	@Test
	fun completionsWithoutASpellChecker() {
		val result = SuggestionBuilder.build("key", null, learned)
		assertEquals(listOf("keyboard", "keyed"), result.map { it.text })
	}

	@Test
	fun typoOffersTheTypedWordThenCorrections() {
		val result = SuggestionBuilder.build("teh", SpellResult(true, listOf("the", "ten", "tech", "tea")), null)
		assertEquals(
			listOf(
				Suggestion("teh", Suggestion.Kind.TYPED),
				Suggestion("the", Suggestion.Kind.CORRECTION),
				Suggestion("ten", Suggestion.Kind.CORRECTION)
			),
			result
		)
	}

	@Test
	fun correctionsFollowTheCapitalizationTyped() {
		val result = SuggestionBuilder.build("Teh", SpellResult(true, listOf("the")), null)
		assertEquals("The", result[1].text)
	}

	@Test
	fun typedWordIsLeftOutWhenNotLearning() {
		val result = SuggestionBuilder.build("teh", SpellResult(true, listOf("the")), null, offerTyped = false)
		assertEquals(listOf(Suggestion("the", Suggestion.Kind.CORRECTION)), result)
	}

	@Test
	fun aLearnedWordIsNotATypo() {
		val result = SuggestionBuilder.build("lane", SpellResult(true, listOf("lame", "line")), learned)
		assertTrue(result.none { it.kind == Suggestion.Kind.TYPED || it.kind == Suggestion.Kind.CORRECTION })
	}

	@Test
	fun correctionsAreNotRepeated() {
		val result = SuggestionBuilder.build("keybord", SpellResult(true, listOf("keyboard", "Keyboard", "keybord")), learned)
		assertEquals(listOf("keybord", "keyboard"), result.map { it.text })
	}

	@Test
	fun fillsRemainingSlotsWithCompletions() {
		val result = SuggestionBuilder.build("keyb", SpellResult(true, listOf("keyboard")), learned)
		// "keyboard" is both, so it is only offered once, as a completion.
		assertEquals(listOf("keyb", "keyboard"), result.map { it.text })
		assertEquals(Suggestion.Kind.COMPLETION, result[1].kind)
	}

	@Test
	fun learnedWordsComeBeforeCorrectionsForAnUnfinishedWord() {
		val known = LearnedWords().also { it.learn("this", 3) }
		val result = SuggestionBuilder.build("th", SpellResult(true, listOf("the", "that", "then")), known)
		assertEquals(
			listOf(
				Suggestion("th", Suggestion.Kind.TYPED),
				Suggestion("this", Suggestion.Kind.COMPLETION),
				Suggestion("the", Suggestion.Kind.CORRECTION)
			),
			result
		)
	}

	@Test
	fun nextWordsAfterASpace() {
		val w = LearnedWords()
		w.learnPair("good", "morning", 2)
		w.learnPair("good", "night")
		assertEquals(
			listOf(Suggestion("morning", Suggestion.Kind.NEXT_WORD), Suggestion("night", Suggestion.Kind.NEXT_WORD)),
			SuggestionBuilder.buildNext("good", w)
		)
		assertEquals(emptyList<Suggestion>(), SuggestionBuilder.buildNext("bad", w))
		assertEquals(emptyList<Suggestion>(), SuggestionBuilder.buildNext("good", null))
		assertEquals(1, SuggestionBuilder.buildNext("good", w, slots = 1).size)
	}

	@Test
	fun commonWordsFillWhatTheUserHasNotTyped() {
		val base = BaseDictionary(listOf("help", "hello", "hell", "held", "helmet"))
		val result = SuggestionBuilder.build("hel", SpellResult(false, emptyList()), null, base = base)
		assertEquals(listOf("help", "hello", "hell"), result.map { it.text })
		assertTrue(result.all { it.kind == Suggestion.Kind.COMPLETION })
	}

	@Test
	fun theUsersWordsComeBeforeCommonWordsAndAreNotRepeated() {
		val base = BaseDictionary(listOf("help", "hello", "hell", "held"))
		val learned = LearnedWords().also { it.learn("hello", 3); it.learn("helix", 2) }
		val result = SuggestionBuilder.build("hel", SpellResult(false, emptyList()), learned, base = base)
		assertEquals(listOf("hello", "helix", "help"), result.map { it.text })
	}

	@Test
	fun commonWordsComeBeforeCorrections() {
		val base = BaseDictionary(listOf("that", "this", "then"))
		val result = SuggestionBuilder.build("th", SpellResult(true, listOf("the", "thy")), null, base = base)
		assertEquals(listOf("th", "that", "this"), result.map { it.text })
		assertEquals(Suggestion.Kind.TYPED, result[0].kind)
	}

	@Test
	fun correctionsShowWhenNoWordStartsWithWhatWasTyped() {
		val base = BaseDictionary(listOf("receive", "recipe"))
		val result = SuggestionBuilder.build("recieve", SpellResult(true, listOf("receive", "relieve")), null, base = base)
		assertEquals(listOf("recieve", "receive", "relieve"), result.map { it.text })
	}

	@Test
	fun withoutACommonWordListNothingChanges() {
		assertEquals(emptyList<Suggestion>(), SuggestionBuilder.build("hel", SpellResult(false, emptyList()), null))
	}
}

class BaseDictionaryTest {
	private val base = BaseDictionary.parse("the\nthat\n\n  this  \nthen\nthey\nzebra\n")

	@Test
	fun parsesOneWordPerLineIgnoringBlanksAndPadding() {
		assertEquals(listOf("the", "that", "this", "then", "they"), base.completions("th", 9))
	}

	@Test
	fun mostCommonFirst() {
		assertEquals(listOf("the", "that", "this"), base.completions("th", 3))
		assertEquals(listOf("the"), base.completions("th", 1))
	}

	@Test
	fun excludesTheWordItselfAndShortPrefixes() {
		assertEquals(listOf("then", "they"), base.completions("the", 9))
		assertEquals(emptyList<String>(), base.completions("t", 3))
		assertEquals(emptyList<String>(), base.completions("th", 0))
		assertEquals(emptyList<String>(), base.completions("xy", 3))
	}

	@Test
	fun followsTheCapitalizationTyped() {
		assertEquals(listOf("The", "That"), base.completions("Th", 2))
		assertEquals(listOf("THE"), base.completions("TH", 1))
	}

	@Test
	fun canLeaveSomeWordsOut() {
		assertEquals(listOf("that", "this"), base.completions("th", 2) { it == "the" })
	}
}

class ContextTest {
	@Test
	fun wordsBefore() {
		assertEquals(listOf("you", "are"), WordUtils.wordsBefore("how are you doing", 2))
		assertEquals(listOf("you"), WordUtils.wordsBefore("how are you doing", 1))
		assertEquals(listOf("doing", "you", "are"), WordUtils.wordsBefore("how are you doing ", 3))
		assertEquals(listOf("hello"), WordUtils.wordsBefore("hello ", 2))
		assertEquals(emptyList<String>(), WordUtils.wordsBefore("hello", 2))
		assertEquals(emptyList<String>(), WordUtils.wordsBefore("", 2))
	}

	@Test
	fun wordsBeforeStopAtPunctuation() {
		assertEquals(emptyList<String>(), WordUtils.wordsBefore("Hello. how", 2))
		assertEquals(listOf("there"), WordUtils.wordsBefore("hi, there you", 2))
		assertEquals(listOf("b"), WordUtils.wordsBefore("a\nb c", 2))
	}

	@Test
	fun wordBeforeIsTheFirstOfWordsBefore() {
		for (text in listOf("hello world", "hello ", "a b c ", "Hi. yes", "x")) {
			assertEquals(text, WordUtils.wordsBefore(text, 1).firstOrNull() ?: "", WordUtils.wordBefore(text))
		}
	}

	@Test
	fun sentenceStarts() {
		assertTrue(WordUtils.isSentenceStart(""))
		assertTrue(WordUtils.isSentenceStart("   "))
		assertTrue(WordUtils.isSentenceStart("Hello. "))
		assertTrue(WordUtils.isSentenceStart("Really? "))
		assertTrue(WordUtils.isSentenceStart("Wow! "))
		assertTrue(WordUtils.isSentenceStart("first line\n"))
		assertTrue(WordUtils.isSentenceStart("wait\u2026 "))
		assertFalse(WordUtils.isSentenceStart("Hello "))
		assertFalse(WordUtils.isSentenceStart("Hello, "))
		assertFalse(WordUtils.isSentenceStart("a"))
	}
}

class PhraseTest {
	@Test
	fun twoWordContextComesFirst() {
		val w = LearnedWords()
		w.learnPair("morning", "everyone", 1)
		w.learnPair("morning", "sunshine", 5)
		w.learnTriple("good", "morning", "everyone", 1)
		// After "good morning" what followed that phrase is a better guide than what followed "morning".
		assertEquals(listOf("everyone", "sunshine"), w.nextWords("morning", 3, before = "good"))
		// Without the phrase only the pair counts.
		assertEquals(listOf("sunshine", "everyone"), w.nextWords("morning", 3))
	}

	@Test
	fun phraseIgnoresCaseAndDoesNotRepeatWords() {
		val w = LearnedWords()
		w.learnTriple("Good", "MORNING", "everyone")
		w.learnPair("morning", "everyone")
		assertEquals(listOf("everyone"), w.nextWords("morning", 3, before = "good"))
	}

	@Test
	fun learnTextLearnsPhrases() {
		val w = LearnedWords()
		w.learnText("see you later. see you soon. see you later")
		assertEquals(listOf("later", "soon"), w.nextWords("you", 3, before = "see"))
		// A phrase does not run over the end of a sentence.
		assertEquals(emptyList<String>(), w.nextWords("later", 3, before = "you"))
	}

	@Test
	fun phrasesSurviveASaveAndLoad() {
		val w = LearnedWords()
		w.learnTriple("good", "morning", "everyone", 2)
		val copy = LearnedWords()
		copy.load(w.serialize())
		assertEquals(1, copy.pairSize)
		assertEquals(listOf("everyone"), copy.nextWords("morning", 3, before = "good"))
	}

	@Test
	fun forgettingAPairForgetsThePhrasesThatEndInIt() {
		val w = LearnedWords()
		w.learnPair("morning", "everyone")
		w.learnTriple("good", "morning", "everyone")
		assertTrue(w.forgetPair("morning", "everyone"))
		assertEquals(emptyList<String>(), w.nextWords("morning", 3, before = "good"))
		assertEquals(0, w.pairSize)
	}
}

class StarterAndForgetTest {
	@Test
	fun startersAreReadBackFromStart() {
		val w = LearnedWords()
		w.learnStarter("Hey")
		w.learnStarter("Hey")
		w.learnStarter("So")
		assertEquals(listOf("Hey", "So"), w.nextWords(LearnedWords.START, 3))
		val copy = LearnedWords()
		copy.load(w.serialize())
		assertEquals(listOf("Hey", "So"), copy.nextWords(LearnedWords.START, 3))
	}

	@Test
	fun forgettingAWordForgetsItsPairsAndPhrasesToo() {
		val w = LearnedWords()
		w.learn("morning", 3)
		w.learn("good", 3)
		w.learnPair("good", "morning")
		w.learnPair("morning", "sunshine")
		w.learnTriple("very", "good", "morning")
		w.learnStarter("Morning")
		assertTrue(w.forgetWord("Morning"))
		assertEquals(0, w.count("morning"))
		assertEquals(3, w.count("good"))
		assertEquals(emptyList<String>(), w.nextWords("good", 3))
		assertEquals(emptyList<String>(), w.nextWords("morning", 3))
		assertEquals(emptyList<String>(), w.nextWords("good", 3, before = "very"))
		assertEquals(emptyList<String>(), w.nextWords(LearnedWords.START, 3))
		assertEquals(0, w.pairSize)
		assertFalse(w.forgetWord("morning"))
	}

	@Test
	fun forgettingAPairKeepsBothWords() {
		val w = LearnedWords()
		w.learn("good", 2)
		w.learn("morning", 2)
		w.learnPair("good", "morning")
		w.learnPair("good", "night")
		assertTrue(w.forgetPair("good", "morning"))
		assertEquals(listOf("night"), w.nextWords("good", 3))
		assertEquals(2, w.count("morning"))
		assertFalse(w.forgetPair("good", "morning"))
	}

	@Test
	fun forgettingMarksTheChange() {
		val w = LearnedWords()
		w.learn("hello", 2)
		w.markSaved()
		assertFalse(w.forgetWord("nothing"))
		assertFalse(w.isDirty)
		assertTrue(w.forgetWord("hello"))
		assertTrue(w.isDirty)
	}

	@Test
	fun listsWordsMostUsedFirst() {
		val w = LearnedWords()
		w.learn("bravo", 2)
		w.learn("Alpha", 5)
		w.learn("alpha", 1)
		w.learn("charlie", 2)
		assertEquals(
			listOf(LearnedWord("Alpha", 6), LearnedWord("bravo", 2), LearnedWord("charlie", 2)),
			w.words()
		)
	}
}

class HabitTest {
	@Test
	fun aCorrectionBecomesAHabitAfterTwo() {
		val w = LearnedWords()
		w.learnCorrection("im", "I'm")
		assertEquals(null, w.correctionFor("im"))
		w.learnCorrection("IM", "I'm")
		assertEquals("I'm", w.correctionFor("im"))
	}

	@Test
	fun choosingACorrectionOnPurposeIsEnough() {
		val w = LearnedWords()
		w.learnCorrection("teh", "the", 2)
		assertEquals("the", w.correctionFor("Teh"))
	}

	@Test
	fun aDifferentFixHasToOutweighTheOld() {
		val w = LearnedWords()
		w.learnCorrection("ur", "your", 3)
		w.learnCorrection("ur", "you're", 1)
		assertEquals("your", w.correctionFor("ur"))
		w.learnCorrection("ur", "you're", 3)
		w.learnCorrection("ur", "you're", 3)
		assertEquals("you're", w.correctionFor("ur"))
	}

	@Test
	fun canBeForgotten() {
		val w = LearnedWords()
		w.learnCorrection("teh", "the", 2)
		assertTrue(w.forgetCorrection("TEH"))
		assertEquals(null, w.correctionFor("teh"))
		assertFalse(w.forgetCorrection("teh"))
	}

	@Test
	fun forgettingAWordForgetsTheHabitsAroundIt() {
		val w = LearnedWords()
		w.learnCorrection("teh", "the", 2)
		w.learnCorrection("thier", "their", 2)
		assertTrue(w.forgetWord("the"))
		assertEquals(null, w.correctionFor("teh"))
		assertEquals("their", w.correctionFor("thier"))
	}

	@Test
	fun ignoresWhatIsNotACorrection() {
		val w = LearnedWords()
		w.learnCorrection("a", "an", 5)
		w.learnCorrection("same", "SAME", 5)
		w.learnCorrection("two words", "x", 5)
		w.learnCorrection("ok", "", 5)
		assertEquals(emptyList<LearnedCorrection>(), w.corrections())
		assertFalse(w.isDirty)
	}

	@Test
	fun survivesASaveAndLoad() {
		val w = LearnedWords()
		w.learnCorrection("teh", "the", 3)
		w.learnCorrection("wierd", "weird", 1)
		val copy = LearnedWords()
		copy.load(w.serialize())
		assertEquals("the", copy.correctionFor("teh"))
		assertEquals(listOf(LearnedCorrection("teh", "the", 3)), copy.corrections())
		assertFalse(copy.isDirty)
	}
}

class RecencyTest {
	@Test
	fun recentUseWinsATieBetweenCompletions() {
		val w = LearnedWords()
		w.learn("apple", 2)
		w.learn("apply", 2)
		assertEquals(listOf("apply", "apple"), w.completions("app", 2))
		w.learn("apple", 0 + 1) // used again, now more than "apply"
		assertEquals(listOf("apple", "apply"), w.completions("app", 2))
	}

	@Test
	fun oldCountsFadeSoNewHabitsCatchUp() {
		val w = LearnedWords()
		w.learnPair("i", "will", 40)
		// Enough new learning to trigger the halving of everything.
		for (i in 0 until LearnedWords.AGE_EVERY) w.learn("filler", 1)
		w.learnPair("i", "won't", 30)
		// 40 has been halved to 20, so the newer habit of 30 now leads.
		assertEquals(listOf("won't", "will"), w.nextWords("i", 2))
	}

	@Test
	fun importingDoesNotAge() {
		val w = LearnedWords()
		w.learnPair("i", "will", 40)
		w.learnText((0 until LearnedWords.AGE_EVERY + 100).joinToString(" ") { "filler" })
		assertEquals(40, w.nextWords("i", 1).size * 40)
		w.learnPair("i", "won't", 30)
		assertEquals(listOf("will", "won't"), w.nextWords("i", 2))
	}
}

class NextWordHintsTest {
	private val hints = NextWordHints.parse(
		"# comment\n\n^\tI\tThe\tHey\n*\tthe\tto\nhello\tthere\teveryone\tworld\nbroken\nGOOD\tmorning\tluck\n"
	)

	@Test
	fun readsEachKind() {
		assertEquals(listOf("I", "The"), hints.starters(2))
		assertEquals(listOf("the", "to"), hints.generic(5))
		assertEquals(listOf("there", "everyone"), hints.after("hello", 2))
		assertEquals(listOf("morning", "luck"), hints.after("Good", 5))
		assertEquals(emptyList<String>(), hints.after("broken", 5))
		assertEquals(emptyList<String>(), hints.after("unknown", 5))
	}

	@Test
	fun anEmptyFileGivesNothing() {
		val none = NextWordHints.parse("")
		assertEquals(emptyList<String>(), none.starters(3))
		assertEquals(emptyList<String>(), none.generic(3))
		assertEquals(emptyList<String>(), none.after("hello", 3))
	}
}

class PopulatedSuggestionsTest {
	private val hints = NextWordHints.parse("^\tI\tThe\tHey\tThanks\n*\tthe\tto\tand\nhello\tthere\teveryone\tworld\ngood\tmorning\tluck\tidea\n")

	private fun texts(list: List<Suggestion>) = list.map { it.text }

	@Test
	fun anEmptyFieldOffersStarters() {
		val result = SuggestionBuilder.buildStart(null, hints = hints)
		assertEquals(listOf("I", "The", "Hey"), texts(result))
		assertTrue(result.all { it.kind == Suggestion.Kind.NEXT_WORD && !it.leadingSpace })
	}

	@Test
	fun yourOwnStartersComeFirstAndAreCapitalized() {
		val w = LearnedWords()
		w.learnStarter("yo")
		w.learnStarter("yo")
		w.learnStarter("hey")
		assertEquals(listOf("Yo", "Hey", "I"), texts(SuggestionBuilder.buildStart(w, hints = hints)))
	}

	@Test
	fun startersAreNotRepeated() {
		val w = LearnedWords()
		w.learnStarter("The")
		assertEquals(listOf("The", "I", "Hey"), texts(SuggestionBuilder.buildStart(w, hints = hints)))
	}

	@Test
	fun afterASpaceThereAreAlwaysNextWords() {
		assertEquals(listOf("there", "everyone", "world"), texts(SuggestionBuilder.buildNext("hello", null, hints = hints)))
		// A word without hints of its own still gets general ones.
		assertEquals(listOf("the", "to", "and"), texts(SuggestionBuilder.buildNext("zebra", null, hints = hints)))
		assertEquals(listOf("the", "to", "and"), texts(SuggestionBuilder.buildNext("", null, hints = hints)))
	}

	@Test
	fun whatYouTypedBeforeComesFirstThenTheHintsFillTheRest() {
		val w = LearnedWords()
		w.learnPair("hello", "friend", 3)
		assertEquals(listOf("friend", "there", "everyone"), texts(SuggestionBuilder.buildNext("hello", w, hints = hints)))
	}

	@Test
	fun phraseContextIsUsed() {
		val w = LearnedWords()
		w.learnTriple("very", "good", "luck", 2)
		assertEquals(listOf("luck", "morning", "idea"), texts(SuggestionBuilder.buildNext("good", w, hints = hints, before = "very")))
	}

	@Test
	fun aFinishedWordWithNothingToCompleteOffersWhatCouldFollow() {
		val result = SuggestionBuilder.build("hello", SpellResult(false, emptyList()), null, hints = hints)
		assertEquals(listOf("there", "everyone", "world"), texts(result))
		assertTrue(result.all { it.kind == Suggestion.Kind.NEXT_WORD && it.leadingSpace })
	}

	@Test
	fun completionsComeBeforeWhatCouldFollow() {
		val base = BaseDictionary(listOf("hello", "helloes", "hellos"))
		val result = SuggestionBuilder.build("hello", SpellResult(false, emptyList()), null, base = base, hints = hints)
		assertEquals(listOf("helloes", "hellos", "there"), texts(result))
		assertEquals(listOf(false, false, true), result.map { it.leadingSpace })
	}

	@Test
	fun aHalfTypedWordIsNotFollowedByAnything() {
		// "hel" is not known to be a word, so nothing is made up to follow it.
		assertEquals(emptyList<Suggestion>(), SuggestionBuilder.build("hel", null, null, hints = hints))
		assertEquals(emptyList<Suggestion>(), SuggestionBuilder.build("hel", SpellResult(true, emptyList()), null, offerTyped = false, hints = hints))
	}

	@Test
	fun aWordFromTheCommonListCountsAsValidEvenWithoutASpellChecker() {
		val base = BaseDictionary(listOf("hello"))
		val result = SuggestionBuilder.build("hello", null, null, base = base, hints = hints)
		assertEquals(listOf("there", "everyone", "world"), texts(result))
	}

	@Test
	fun theCommonListKnowsWhatItContains() {
		val base = BaseDictionary(listOf("hello", "world"))
		assertTrue(base.contains("Hello"))
		assertFalse(base.contains("hel"))
	}
}

class AutoCorrectTest {
	private fun choose(
		word: String,
		corrections: List<String>,
		level: AutoCorrectLevel,
		start: Boolean = false,
		known: Boolean = false,
		common: Boolean = false
	) = AutoCorrect.choose(word, corrections, level, start, known, common)

	@Test
	fun editDistanceCountsSlips() {
		assertEquals(0, AutoCorrect.editDistance("the", "the"))
		assertEquals(1, AutoCorrect.editDistance("teh", "the"))       // swapped letters
		assertEquals(1, AutoCorrect.editDistance("thee", "the"))      // extra letter
		assertEquals(1, AutoCorrect.editDistance("th", "the"))        // missing letter
		assertEquals(1, AutoCorrect.editDistance("thr", "the"))       // wrong letter
		assertEquals(1, AutoCorrect.editDistance("recieve", "receive"))
		assertEquals(3, AutoCorrect.editDistance("kitten", "sitting"))
		assertEquals(5, AutoCorrect.editDistance("", "hello"))
	}

	@Test
	fun offNeverCorrects() {
		assertEquals(null, choose("teh", listOf("the"), AutoCorrectLevel.OFF))
	}

	@Test
	fun lowFixesASingleSlipOnly() {
		assertEquals("the", choose("teh", listOf("the"), AutoCorrectLevel.LOW))
		assertEquals("receive", choose("recieve", listOf("receive"), AutoCorrectLevel.LOW))
		// Two changes away is more than low goes for.
		assertEquals(null, choose("definatly", listOf("definitely"), AutoCorrectLevel.LOW))
		// A single wrong letter is fine.
		assertEquals("definitely", choose("definately", listOf("definitely"), AutoCorrectLevel.LOW))
	}

	@Test
	fun lowNeedsTheSameFirstLetter() {
		assertEquals(null, choose("rhe", listOf("the"), AutoCorrectLevel.LOW))
		assertEquals("the", choose("rhe", listOf("the"), AutoCorrectLevel.MEDIUM))
	}

	@Test
	fun mediumFixesTwoSlips() {
		assertEquals("definitely", choose("definatly", listOf("definitely"), AutoCorrectLevel.MEDIUM))
		// A first letter that is wrong AND another slip is too much for medium.
		assertEquals(null, choose("wecieve", listOf("receive"), AutoCorrectLevel.MEDIUM))
	}

	@Test
	fun highTakesTheTopSuggestionEvenFarOff() {
		assertEquals("receive", choose("wecieve", listOf("receive"), AutoCorrectLevel.HIGH))
		assertEquals("tomorrow", choose("tomorow", listOf("tomorrow"), AutoCorrectLevel.HIGH))
		// Even high does not go past three changes.
		assertEquals(null, choose("abcdefgh", listOf("zzzzzzzz"), AutoCorrectLevel.HIGH))
	}

	@Test
	fun theClosestOfTheFirstFewIsChosen() {
		// "thing" is two changes away and "their" one, so the second suggestion is the closer.
		assertEquals("their", choose("thier", listOf("thing", "their", "thick"), AutoCorrectLevel.MEDIUM))
		// Of equally close ones the first is taken.
		assertEquals("thief", choose("thier", listOf("thief", "their"), AutoCorrectLevel.MEDIUM))
		// Only the first three suggestions are looked at.
		assertEquals(null, choose("thier", listOf("x", "y", "z", "their"), AutoCorrectLevel.MEDIUM))
	}

	@Test
	fun shortWordsAreLeftAlone() {
		assertEquals(null, choose("hw", listOf("how"), AutoCorrectLevel.HIGH))
		// Three letters allow a single change, so a swap is fine but a bigger change is not.
		assertEquals("the", choose("teh", listOf("the"), AutoCorrectLevel.HIGH))
		assertEquals(null, choose("xyz", listOf("abc"), AutoCorrectLevel.HIGH))
	}

	@Test
	fun wordsTheUserOrTheDictionaryKnowAreLeftAlone() {
		assertEquals(null, choose("teh", listOf("the"), AutoCorrectLevel.HIGH, known = true))
		assertEquals(null, choose("teh", listOf("the"), AutoCorrectLevel.HIGH, common = true))
	}

	@Test
	fun namesAcronymsAndCamelCaseAreLeftAlone() {
		assertEquals(null, choose("NASA", listOf("nasal"), AutoCorrectLevel.HIGH))
		assertEquals(null, choose("iPhone", listOf("phone"), AutoCorrectLevel.HIGH))
		// A capital mid-sentence is probably a name, except for the boldest level.
		assertEquals(null, choose("Teh", listOf("the"), AutoCorrectLevel.MEDIUM))
		assertEquals("The", choose("Teh", listOf("the"), AutoCorrectLevel.MEDIUM, start = true))
		assertEquals("The", choose("Teh", listOf("the"), AutoCorrectLevel.HIGH))
	}

	@Test
	fun theFixFollowsTheCapitalizationTyped() {
		assertEquals("The", choose("Teh", listOf("the"), AutoCorrectLevel.LOW, start = true))
		assertEquals("the", choose("teh", listOf("the"), AutoCorrectLevel.LOW))
	}

	@Test
	fun skipsPhrasesAndTheWordItself() {
		assertEquals(null, choose("teh", listOf("the world"), AutoCorrectLevel.HIGH))
		assertEquals(null, choose("teh", listOf("teh", "TEH"), AutoCorrectLevel.HIGH))
		assertEquals(null, choose("teh", emptyList(), AutoCorrectLevel.HIGH))
	}

	@Test
	fun apostrophesAreFixedToo() {
		assertEquals("don't", choose("dont", listOf("don't"), AutoCorrectLevel.LOW))
	}

	@Test
	fun levelsComeFromThePreference() {
		assertEquals(AutoCorrectLevel.LOW, AutoCorrectLevel.fromPreference("low"))
		assertEquals(AutoCorrectLevel.HIGH, AutoCorrectLevel.fromPreference("high"))
		assertEquals(AutoCorrectLevel.OFF, AutoCorrectLevel.fromPreference("off"))
		assertEquals(AutoCorrectLevel.MEDIUM, AutoCorrectLevel.fromPreference("nonsense"))
		assertEquals(AutoCorrectLevel.MEDIUM, AutoCorrectLevel.fromPreference(null))
	}
}

class BuiltInSpellTest {
	private val dictionary = BaseDictionary(listOf("the", "that", "receive", "weird", "friend", "because", "hello", "help", "tomorrow", "their"))

	@Test
	fun nearestFindsWordsOneChangeAwayWithTheSameFirstLetter() {
		assertEquals(listOf("the"), dictionary.nearest("teh", 1, 3))
		assertEquals(listOf("receive"), dictionary.nearest("recieve", 1, 3))
		assertEquals(listOf("weird"), dictionary.nearest("wierd", 1, 3))
		// "rhe" would be "the", but it starts with a different letter.
		assertEquals(emptyList<String>(), dictionary.nearest("rhe", 1, 3))
	}

	@Test
	fun nearestPutsTheClosestFirstThenTheMostCommon() {
		val words = BaseDictionary(listOf("thing", "then", "the", "them"))
		// "thn": "then" and "the" are one change away, "thing" and "them" two. Closest first, then in list order.
		assertEquals(listOf("then", "the"), words.nearest("thn", 1, 5))
		assertEquals(listOf("then", "the", "thing", "them"), words.nearest("thn", 2, 5))
		assertEquals(listOf("then"), words.nearest("thn", 1, 1))
	}

	@Test
	fun nearestNeverReturnsTheWordItself() {
		assertEquals(emptyList<String>(), dictionary.nearest("hello", 0, 3))
		assertTrue("hello" !in dictionary.nearest("hello", 2, 5))
	}

	@Test
	fun aCommonWordIsNotATypo() {
		assertEquals(SpellResult(false, emptyList()), AutoCorrect.builtInSpell("hello", dictionary, false))
		assertEquals(SpellResult(false, emptyList()), AutoCorrect.builtInSpell("Hello", dictionary, false))
	}

	@Test
	fun aWordOneLetterFromACommonOneIsATypo() {
		val result = AutoCorrect.builtInSpell("teh", dictionary, false)
		assertTrue(result.isTypo)
		assertEquals(listOf("the"), result.corrections)
		assertEquals(listOf("Recieve".let { "Receive" }), AutoCorrect.builtInSpell("Recieve", dictionary, false).corrections)
	}

	@Test
	fun aWordTheUserHasUsedIsNotATypo() {
		assertEquals(false, AutoCorrect.builtInSpell("teh", dictionary, wasUsed = true).isTypo)
	}

	@Test
	fun aWordWithNoCommonWordNearIsLeftAlone() {
		// A rare word or a name can't be told from a typo, so it isn't taken for one.
		assertEquals(SpellResult(false, emptyList()), AutoCorrect.builtInSpell("zyxwv", dictionary, false))
		assertEquals(SpellResult(false, emptyList()), AutoCorrect.builtInSpell("ab", dictionary, false))
	}

	@Test
	fun aWiderMaxDistanceCatchesMoreTypos() {
		// "wierdd" is two changes from "weird", too far for the default distance of one.
		assertEquals(SpellResult(false, emptyList()), AutoCorrect.builtInSpell("wierdd", dictionary, false))
		val result = AutoCorrect.builtInSpell("wierdd", dictionary, false, maxDistance = 2)
		assertTrue(result.isTypo)
		assertEquals(listOf("weird"), result.corrections)
	}

	@Test
	fun fixesTypoesEndToEndAtEachLevel() {
		fun fix(word: String, level: AutoCorrectLevel): String? {
			val spell = AutoCorrect.builtInSpell(word, dictionary, false)
			return AutoCorrect.choose(word, spell.corrections, level, true, isKnown = false, isCommon = dictionary.contains(word))
		}
		assertEquals("the", fix("teh", AutoCorrectLevel.LOW))
		assertEquals("receive", fix("recieve", AutoCorrectLevel.MEDIUM))
		assertEquals("friend", fix("freind", AutoCorrectLevel.HIGH))
		assertEquals(null, fix("teh", AutoCorrectLevel.OFF))
		assertEquals(null, fix("hello", AutoCorrectLevel.HIGH))
	}

	@Test
	fun theRealCommonWordsFixTheUsualTypos() {
		val real = BaseDictionary.parse(java.io.File("src/main/res/raw/common_words.txt").readText())
		fun fix(word: String): String? {
			val spell = AutoCorrect.builtInSpell(word, real, false)
			return AutoCorrect.choose(word, spell.corrections, AutoCorrectLevel.MEDIUM, true, isKnown = false, isCommon = real.contains(word))
		}
		for ((typo, right) in listOf("teh" to "the", "recieve" to "receive", "wierd" to "weird", "freind" to "friend",
			"becuase" to "because", "adress" to "address", "thier" to "their", "seperate" to "separate", "definately" to "definitely")) {
			assertEquals(typo, right, fix(typo)?.lowercase())
		}
		// Ordinary words are not touched.
		for (word in listOf("hello", "going", "tomorrow", "because", "thanks", "phone", "message", "yeah", "okay", "the")) {
			assertEquals(word, null, fix(word))
		}
	}
}

