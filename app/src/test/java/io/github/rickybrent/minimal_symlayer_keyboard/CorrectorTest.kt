package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CorrectorTest {
	private val dictionary = BaseDictionary.parse(File("src/main/res/raw/common_words_en.txt").readText())
	private val hints = NextWordHints.parse(File("src/main/res/raw/next_words.txt").readText())

	private fun choose(
		word: String,
		level: AutoCorrectLevel = AutoCorrectLevel.MEDIUM,
		system: List<String> = emptyList(),
		learned: LearnedWords? = null,
		previous: String = "",
		atSentenceStart: Boolean = false
	): String? = Corrector.choose(
		word, level, atSentenceStart, isKnown = false, isCommon = dictionary.contains(word),
		system = system, dictionary = dictionary, learned = learned, previous = previous, hints = hints
	)

	// --- Keyboard and cost ---

	@Test
	fun neighbouringKeysAreNear() {
		assertTrue(Corrector.keysNear('a', 's'))
		assertTrue(Corrector.keysNear('q', 'w'))
		assertTrue(Corrector.keysNear('a', 'q'))
		assertTrue(Corrector.keysNear('a', 'w'))
		assertTrue(Corrector.keysNear('s', 'e'))
		assertTrue(Corrector.keysNear('g', 'v'))
		assertTrue(Corrector.keysNear('m', 'k'))
		assertFalse(Corrector.keysNear('q', 's'))
		assertFalse(Corrector.keysNear('s', 'q'))
		assertFalse(Corrector.keysNear('a', 'p'))
		assertFalse(Corrector.keysNear('q', 'z'))
		assertFalse(Corrector.keysNear('1', '2'))
	}

	@Test
	fun cheapSlipsCostLess() {
		assertEquals(0.0, Corrector.cost("hello", "hello"), 0.0001)
		// Swapped letters.
		assertEquals(0.6, Corrector.cost("teh", "the"), 0.0001)
		assertEquals(0.6, Corrector.cost("wierd", "weird"), 0.0001)
		// A vowel for a vowel.
		assertEquals(0.7, Corrector.cost("definately", "definitely"), 0.0001)
		// A doubled letter left out or added.
		assertEquals(0.6, Corrector.cost("occured", "occurred"), 0.0001)
		assertEquals(0.6, Corrector.cost("helllo", "hello"), 0.0001)
		// A key next to the right one.
		assertEquals(0.5, Corrector.cost("hwllo", "hello"), 0.0001)
		assertEquals(0.5, Corrector.cost("hellp", "hello"), 0.0001)
		assertEquals(0.6, Corrector.cost("helo", "hello"), 0.0001)
		// A missing letter costs a whole one, a letter from the far side of the keyboard a little more.
		assertEquals(1.0, Corrector.cost("hllo", "hello"), 0.0001)
		assertEquals(1.2, Corrector.cost("hellz", "hello"), 0.0001)
	}

	@Test
	fun aWrongFirstLetterCostsMore() {
		assertEquals(2.0, Corrector.cost("xbc", "abc"), 0.0001)
		assertEquals(1.2, Corrector.cost("abq", "abc"), 0.0001)
	}

	// --- Choosing ---

	@Test
	fun theCommonTyposAreFixed() {
		val expected = mapOf(
			"teh" to "the", "recieve" to "receive", "definately" to "definitely", "wierd" to "weird",
			"seperate" to "separate", "becuase" to "because", "occured" to "occurred", "untill" to "until",
			"tommorow" to "tomorrow", "accomodate" to "accommodate", "goverment" to "government", "thier" to "their",
			"freind" to "friend", "beleive" to "believe", "wich" to "which", "adn" to "and", "hte" to "the",
			"taht" to "that", "thsi" to "this", "yuo" to "you", "realy" to "really",
			"probaly" to "probably", "diffrent" to "different", "intresting" to "interesting", "peice" to "piece",
			"tomorow" to "tomorrow", "recieved" to "received", "definitly" to "definitely", "thnk" to "think",
			"wiht" to "with", "fromt" to "from", "whta" to "what", "abuot" to "about", "beacuse" to "because",
			"cuold" to "could", "shoudl" to "should", "woudl" to "would", "peopel" to "people", "thigns" to "things",
			"alwasy" to "always", "shcool" to "school", "sitll" to "still", "jsut" to "just", "konw" to "know"
		)
		val wrong = expected.filter { (typo, fix) -> choose(typo) != fix }.map { (typo, fix) -> "$typo -> ${choose(typo)} (wanted $fix)" }
		assertTrue("Not fixed: $wrong", wrong.isEmpty())
	}

	@Test
	fun theLowestLevelFixesASingleSlip() {
		assertEquals("the", choose("teh", AutoCorrectLevel.LOW))
		assertEquals("definitely", choose("definately", AutoCorrectLevel.LOW))
		assertEquals("receive", choose("recieve", AutoCorrectLevel.LOW))
		assertEquals("occurred", choose("occured", AutoCorrectLevel.LOW))
	}

	@Test
	fun higherLevelsReachFurther() {
		// Two slips in "intrestng" are beyond LOW.
		assertNull(choose("intrestng", AutoCorrectLevel.LOW))
		assertEquals("interesting", choose("intrestng", AutoCorrectLevel.MEDIUM))
	}

	@Test
	fun aWrongFirstLetterTakesABolderLevel() {
		assertNull(choose("gello", AutoCorrectLevel.LOW))
		assertEquals("hello", choose("gello", AutoCorrectLevel.MEDIUM))
	}

	@Test
	fun twoSlipsAndAWrongFirstLetterTakeTheHighestLevel() {
		// With only one word to pick from, so that nothing else with the same first letter gets in the way.
		val small = BaseDictionary(listOf("hello"))
		fun rank(level: AutoCorrectLevel) = Corrector.rank("gelli", 2, level, emptyList(), small, null).map { it.word }
		assertTrue(rank(AutoCorrectLevel.LOW).isEmpty())
		assertTrue(rank(AutoCorrectLevel.MEDIUM).isEmpty())
		assertEquals(listOf("hello"), rank(AutoCorrectLevel.HIGH))
	}

	@Test
	fun theFirstTwoLettersSwappedIsOneSlipAtEveryLevel() {
		assertEquals("the", choose("hte", AutoCorrectLevel.LOW))
		assertEquals("there", choose("htere", AutoCorrectLevel.LOW))
	}

	@Test
	fun theCapitalizationOfTheTypoIsKept() {
		assertEquals("The", choose("Teh", atSentenceStart = true))
	}

	@Test
	fun shoutingIsOnPurpose() {
		assertNull(choose("TEH", atSentenceStart = true))
		assertNull(choose("TEH", AutoCorrectLevel.HIGH))
	}

	@Test
	fun aCapitalInTheMiddleOfASentenceIsAName() {
		assertNull(choose("Teh"))
		assertEquals("The", choose("Teh", AutoCorrectLevel.HIGH))
	}

	@Test
	fun slangLaughterAndStretchedWordsAreLeftAlone() {
		for (word in listOf("lol", "lmao", "omg", "idk", "hahaha", "hehe", "sooo", "yesss", "pleaseee", "omw", "tbh", "thx", "pls", "nah")) {
			assertNull(word, choose(word, AutoCorrectLevel.HIGH))
		}
	}

	@Test
	fun aWordThatIsCommonOrKnownIsNeverChanged() {
		assertNull(choose("hello"))
		assertNull(Corrector.choose("beeper", AutoCorrectLevel.HIGH, false, isKnown = true, isCommon = false, system = listOf("beeper"), dictionary = dictionary, learned = null))
	}

	@Test
	fun shortWordsAreLeftAlone() {
		assertNull(choose("hm"))
		assertNull(choose("tj"))
	}

	@Test
	fun offNeverFixes() {
		assertNull(choose("teh", AutoCorrectLevel.OFF))
	}

	@Test
	fun theSpellCheckerAsCandidateGetsAHeadStart() {
		// Its suggestion is not among the common words, so only it can offer it.
		assertEquals("Kubernetes", choose("Kubernets", AutoCorrectLevel.HIGH, system = listOf("Kubernetes"), atSentenceStart = true))
	}

	@Test
	fun theSpellCheckersWholeListIsWeighedNotJustTheFirst() {
		// The checker puts a rare word first, but the common one it also lists is the likelier slip.
		assertEquals("the", choose("teh", system = listOf("tech", "the")))
	}

	@Test
	fun suggestionsThatAreTooFarOrHaveSpacesAreIgnored() {
		assertNull(choose("qwertyx", system = listOf("something else entirely", "quarterly")))
		assertEquals("their", choose("thier", system = listOf("thie r", "their")))
	}

	@Test
	fun theUsersOwnWordsAreOffered() {
		val learned = LearnedWords()
		learned.learn("Beeper", 3)
		assertEquals("Beeper", choose("beepr", learned = learned))
		assertEquals("Beeper", choose("Beepr", learned = learned, atSentenceStart = true))
	}

	@Test
	fun aWordTypedOnlyOnceIsNotATarget() {
		val learned = LearnedWords()
		learned.learn("Beeper", 1)
		assertFalse("Beeper" == choose("beepr", learned = learned))
	}

	@Test
	fun theWordBeforeBreaksATie() {
		// "hte" could be "the" or "he" (or "hate") but after "in" the likeliest is "the".
		val ranked = Corrector.rank("hte", 1, AutoCorrectLevel.MEDIUM, emptyList(), dictionary, null, previous = "in", hints = hints)
		assertEquals("the", ranked.first().word)
	}

	@Test
	fun rankReturnsTheLikeliestFirst() {
		val ranked = Corrector.rank("wierd", 2, AutoCorrectLevel.MEDIUM, emptyList(), dictionary, null)
		assertNotNull(ranked.firstOrNull())
		assertEquals("weird", ranked.first().word)
		assertTrue(ranked.zipWithNext().all { (a, b) -> a.score >= b.score })
	}

	@Test
	fun nothingIsOfferedForAWordThatHasNothingNear() {
		assertTrue(Corrector.rank("qzxvbnm", 2, AutoCorrectLevel.HIGH, emptyList(), dictionary, null).isEmpty())
	}
}
