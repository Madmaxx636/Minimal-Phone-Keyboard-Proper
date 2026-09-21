package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GrammarTest {
	/**
	 * Type [text] one word at a time as the keyboard sees it, and return what the last word turns into:
	 * the text that would replace the words from the fix's start, or null if nothing changes.
	 */
	private fun fix(text: String, level: GrammarLevel = GrammarLevel.FULL, boundary: Char = ' '): String? {
		val words = text.trim().split(" ")
		val word = words.last()
		val previous = words.dropLast(1).reversed()
		val fix = Grammar.fix(previous, word, boundary, level) ?: return null
		// The text that the fix would leave for the words it covers.
		return fix.replacement
	}

	private fun assertFixed(expected: String, text: String, level: GrammarLevel = GrammarLevel.FULL) =
		assertEquals("\"$text\"", expected, fix(text, level))

	private fun assertLeftAlone(text: String, level: GrammarLevel = GrammarLevel.FULL) =
		assertNull("\"$text\" should not change but became ${fix(text, level)}", fix(text, level))

	// --- Basic ---

	@Test
	fun aLoneIIsCapitalized() {
		assertFixed("I", "i")
		assertFixed("I", "and i")
		assertLeftAlone("I")
		assertLeftAlone("and I")
		assertNull(fix("so do i", boundary = '.'))
	}

	@Test
	fun contractionsGetTheirApostrophe() {
		assertFixed("don't", "dont")
		assertFixed("Don't", "Dont")
		assertFixed("can't", "i cant")
		assertFixed("I'm", "im")
		assertFixed("you're", "youre")
		assertFixed("that's", "thats")
		assertFixed("I'm", "i'm")
		assertLeftAlone("don't")
		assertLeftAlone("I'm")
		assertLeftAlone("it's")
	}

	@Test
	fun wordsThatAreOftenRunTogether() {
		assertFixed("a lot", "alot")
		assertFixed("A lot", "Alot")
		assertFixed("as well", "aswell")
		assertFixed("at least", "atleast")
		assertFixed("no one", "noone")
	}

	@Test
	fun aDoubledWordIsRemoved() {
		assertFixed("the", "the the")
		assertFixed("The", "The the")
		assertFixed("to", "want to to")
		// Some words are doubled on purpose.
		assertLeftAlone("that that")
		assertLeftAlone("had had")
		assertLeftAlone("no no")
		assertLeftAlone("very very")
	}

	@Test
	fun couldOfIsCouldHave() {
		assertFixed("could have", "could of")
		assertFixed("Should have", "Should of")
		assertFixed("must have", "must of")
		assertLeftAlone("kind of")
		assertLeftAlone("out of")
	}

	@Test
	fun aOrAnAgreesWithTheSound() {
		assertFixed("an apple", "a apple")
		assertFixed("a banana", "an banana")
		assertFixed("An egg", "A egg")
		assertFixed("an hour", "a hour")
		assertFixed("an honest", "a honest")
		assertFixed("a university", "an university")
		assertFixed("a user", "an user")
		assertFixed("a one", "an one")
		assertFixed("a european", "an european")
		assertLeftAlone("an apple")
		assertLeftAlone("a banana")
		assertLeftAlone("an hour")
		assertLeftAlone("a university")
	}

	@Test
	fun aOrAnLeavesAcronymsNumbersAndNamesAlone() {
		assertLeftAlone("a FBI")
		assertLeftAlone("an NASA")
		assertLeftAlone("a 8")
		assertLeftAlone("a iPhone")
		assertLeftAlone("a x")
		assertLeftAlone("a o'clock")
	}

	@Test
	fun basicDoesNotTouchHomophonesOrAgreement() {
		assertLeftAlone("there car", GrammarLevel.BASIC)
		assertLeftAlone("your welcome", GrammarLevel.BASIC)
		assertLeftAlone("he don't", GrammarLevel.BASIC)
	}

	@Test
	fun offDoesNothing() {
		assertLeftAlone("i", GrammarLevel.OFF)
		assertLeftAlone("dont", GrammarLevel.OFF)
		assertLeftAlone("there car", GrammarLevel.OFF)
	}

	@Test
	fun shoutingAndCodeAreLeftAlone() {
		assertLeftAlone("DONT")
		assertLeftAlone("THERE CAR")
		assertLeftAlone("iPhone")
		assertLeftAlone("myVariable")
	}

	// --- Their, there, they're ---

	@Test
	fun theirIsThereBeforeAVerbOfBeing() {
		assertFixed("there is", "their is")
		assertFixed("there are", "their are")
		assertFixed("There was", "Their was")
		assertFixed("there is", "they're is")
		assertLeftAlone("there is")
		assertLeftAlone("their car")
	}

	@Test
	fun thereIsTheirBeforeSomethingOwned() {
		assertFixed("their car", "there car")
		assertFixed("their own", "there own")
		assertFixed("Their kids", "There kids")
		assertFixed("their own", "they're own")
		// But not where "there" is a place or starts "is there any".
		assertLeftAlone("is there people")
		assertLeftAlone("are there kids")
		assertLeftAlone("over there friends")
		assertLeftAlone("out there family")
	}

	@Test
	fun theyreIsWhatFollowsWhenItIsAVerbOrAdverb() {
		assertFixed("they're going", "their going")
		assertFixed("they're not", "their not")
		assertFixed("they're all", "their all")
		assertFixed("they're the", "their the")
		assertFixed("they're gonna", "and there gonna")
		// "there" is only taken for "they're" at the start of a clause, since "in there going" can be right.
		assertFixed("they're going", "there going")
		assertLeftAlone("in there going")
		assertLeftAlone("went there still")
	}

	@Test
	fun theirAndThereBeforeIngWordsAreLeftAlone() {
		assertLeftAlone("their working")
		assertLeftAlone("their saying")
		assertLeftAlone("their very")
		assertLeftAlone("their really")
	}

	// --- Your, its, whose ---

	@Test
	fun yourIsYoureBeforeSomethingThatIsNotOwned() {
		assertFixed("you're welcome", "your welcome")
		assertFixed("You're a", "Your a")
		assertFixed("you're the", "your the")
		assertFixed("you're not", "your not")
		assertFixed("you're going", "your going")
		assertLeftAlone("your car")
		assertLeftAlone("your very own")
		assertLeftAlone("your about")
		assertLeftAlone("your doing")
	}

	@Test
	fun youreIsYourBeforeSomethingOwned() {
		assertFixed("your car", "you're car")
		assertFixed("your own", "you're own")
		assertFixed("your phone", "youre phone")
		assertLeftAlone("you're welcome")
		assertLeftAlone("you're friends")
		assertLeftAlone("you're working")
	}

	@Test
	fun itsAndItsWithAnApostrophe() {
		assertFixed("it's a", "its a")
		assertFixed("it's not", "its not")
		assertFixed("it's the", "its the")
		assertFixed("its own", "it's own")
		assertFixed("its name", "it's name")
		assertLeftAlone("its name")
		assertLeftAlone("it's a")
		assertLeftAlone("its very nature")
	}

	@Test
	fun whoseAndWhos() {
		assertFixed("who's going", "whose going")
		assertFixed("who's there", "whose there")
		assertFixed("whose car", "who's car")
		assertFixed("whose fault", "who's fault")
		assertLeftAlone("whose car")
		assertLeftAlone("who's going")
	}

	// --- To, too, then, than ---

	@Test
	fun toBecomesTooBeforeAnAdjective() {
		assertFixed("too much", "to much")
		assertFixed("too late", "to late")
		assertFixed("Too bad", "To bad")
		assertLeftAlone("to high school")
		assertLeftAlone("go to fast food")
		assertLeftAlone("to Long Beach")
		assertLeftAlone("too much")
	}

	@Test
	fun tooBecomesToBeforeAVerb() {
		assertFixed("to be", "too be")
		assertFixed("to go", "want too go")
		assertLeftAlone("too much")
		assertLeftAlone("too many")
		assertLeftAlone("too late")
	}

	@Test
	fun thenAndThan() {
		assertFixed("than", "better then")
		assertFixed("than", "more then")
		assertFixed("then", "and than")
		assertFixed("then", "so than")
		assertFixed("Than", "bigger Then")
		assertLeftAlone("better than")
		assertLeftAlone("and then")
		assertLeftAlone("rather than")
	}

	// --- Other pairs ---

	@Test
	fun loseAndLoose() {
		assertFixed("lose", "to loose")
		assertFixed("lose", "will loose")
		assertFixed("lose", "don't loose")
		assertLeftAlone("a loose")
		assertLeftAlone("to lose")
	}

	@Test
	fun affectAndEffect() {
		assertFixed("effect", "the affect")
		assertFixed("effect", "side affect")
		assertLeftAlone("it will affect")
		assertLeftAlone("the effect")
	}

	@Test
	fun weatherOrNot() {
		assertFixed("whether or", "weather or")
		assertLeftAlone("the weather")
		assertLeftAlone("bad weather")
	}

	// --- Subject and verb ---

	@Test
	fun subjectAndVerbAgree() {
		assertFixed("he doesn't", "he don't")
		assertFixed("they were", "they was")
		assertFixed("you were", "you was")
		assertFixed("they are", "they is")
		assertFixed("she is", "she are")
		assertFixed("he has", "he have")
		assertFixed("we have", "we has")
		assertFixed("I don't", "I doesn't")
		assertLeftAlone("he doesn't")
		assertLeftAlone("they were")
		assertLeftAlone("I was")
		assertLeftAlone("we have")
	}

	@Test
	fun agreementIsNotCheckedInBasic() {
		assertLeftAlone("they was", GrammarLevel.BASIC)
	}

	@Test
	fun theLevelComesFromThePreference() {
		assertEquals(GrammarLevel.BASIC, GrammarLevel.fromPreference("basic"))
		assertEquals(GrammarLevel.OFF, GrammarLevel.fromPreference("off"))
		assertEquals(GrammarLevel.FULL, GrammarLevel.fromPreference("full"))
		assertEquals(GrammarLevel.FULL, GrammarLevel.fromPreference("nonsense"))
		assertEquals(GrammarLevel.FULL, GrammarLevel.fromPreference(null))
	}

	@Test
	fun whereTheFixStartsIsTheNumberOfWordsBack() {
		assertEquals(0, Grammar.fix(emptyList(), "dont", ' ', GrammarLevel.FULL)?.back)
		assertEquals(1, Grammar.fix(listOf("their"), "is", ' ', GrammarLevel.FULL)?.back)
		assertEquals(1, Grammar.fix(listOf("a"), "apple", ' ', GrammarLevel.FULL)?.back)
	}
}

class GrammarChangeTest {
	private fun change(text: String, boundary: Char = ' ', level: GrammarLevel = GrammarLevel.FULL) =
		Grammar.change(text, boundary, level, "@#/\\._-:+=~")

	@Test
	fun changesTheEarlierWordInAWholeSentence() {
		// Typing "is" finishes the phrase "their is".
		val change = change("I think their is")
		assertEquals(TextChange(8, "I think there is"), change)
		assertEquals("I think there is", change!!.text)
	}

	@Test
	fun changesOnlyTheLastWord() {
		assertEquals(TextChange(9, "I really don't"), change("I really dont"))
		assertEquals(TextChange(0, "I"), change("i"))
		assertEquals(TextChange(6, "so do I"), change("so do i"))
	}

	@Test
	fun keepsWhatComesBeforeTheChange() {
		assertEquals("He said: \"there is", change("He said: \"their is")?.text)
		assertEquals("(there are", change("(their are")?.text)
		assertEquals("Hello world, I'm", change("Hello world, im")?.text)
	}

	@Test
	fun nothingChangesForFineText() {
		assertEquals(null, change("I think there is"))
		assertEquals(null, change("Their car is here"))
		assertEquals(null, change("hello world"))
		assertEquals(null, change(""))
	}

	@Test
	fun aSentenceBreakStopsTheContext() {
		// "there" ends a sentence, so "car" starts a new one and there is no phrase "there car".
		assertEquals(null, change("Look over there. car"))
		assertEquals(null, change("Look over there, car"))
	}

	@Test
	fun handlesAndAddressesAreLeftAlone() {
		assertEquals(null, change("email@their is"))
		assertEquals(null, change("see www.dont"))
		assertEquals(null, change("a/dont"))
		assertEquals(null, change("#alot"))
	}

	@Test
	fun offChangesNothing() {
		assertEquals(null, change("their is", level = GrammarLevel.OFF))
	}

	@Test
	fun wordStartsPointAtTheWords() {
		assertEquals(listOf(8, 4, 0), WordUtils.wordStarts("how are you", 2))
		assertEquals(listOf(4, 0), WordUtils.wordStarts("how are", 1))
		assertEquals(listOf(4), WordUtils.wordStarts("how are", 0))
		assertEquals(listOf(0), WordUtils.wordStarts("hello", 2))
		// A leading quote is not part of the word.
		assertEquals(listOf(1), WordUtils.wordStarts("'hello", 2))
	}

	@Test
	fun wordStartsStopAtPunctuation() {
		assertEquals(listOf(7), WordUtils.wordStarts("Hello. world", 2))
		assertEquals(listOf(6, 0), WordUtils.wordStarts("hello yo", 1))
		// A comma between them ends the run, so only the last word is found.
		assertEquals(listOf(5), WordUtils.wordStarts("yes, no", 1))
	}
}
