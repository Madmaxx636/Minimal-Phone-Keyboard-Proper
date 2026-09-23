package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpacingTest {
	private val dictionary = BaseDictionary.parse(File("src/main/res/raw/common_words_en.txt").readText())

	private fun space(text: String, capitalize: Boolean = false) = Spacing.afterPunctuation(text, capitalize)

	private fun split(word: String, maxRank: Int = Int.MAX_VALUE) =
		Spacing.split(word, maxRank) { dictionary.rank(it) }?.joinToString(" ")

	// --- Space after punctuation ---

	@Test
	fun aFullStopTypedIntoTheNextWordGetsASpace() {
		assertEquals("hello. world", space("hello.world"))
		assertEquals("so it goes. then", space("so it goes.then"))
	}

	@Test
	fun theNextSentenceGetsACapitalIfAsked() {
		assertEquals("hello. World", space("hello.world", capitalize = true))
		assertEquals("really? No", space("really?no", capitalize = true))
		// A comma does not start a sentence.
		assertEquals("yes, please", space("yes,please", capitalize = true))
	}

	@Test
	fun otherMarksGetASpaceToo() {
		assertEquals("yes, please", space("yes,please"))
		assertEquals("what? no", space("what?no"))
		assertEquals("wow! really", space("wow!really"))
		assertEquals("first; second", space("first;second"))
		assertEquals("a, b, c", space("a,b,c"))
		assertEquals("hi, I", space("hi,I"))
	}

	@Test
	fun everyMarkInATokenGetsASpace() {
		assertEquals("hello. world. today", space("hello.world.today"))
		assertEquals("ok, so. what", space("ok,so.what"))
	}

	@Test
	fun anOpeningQuoteOrBracketIsKept() {
		assertEquals("(hello. world", space("(hello.world"))
		assertEquals("\"yes, please", space("\"yes,please"))
	}

	@Test
	fun titlesAndApostrophesWork() {
		assertEquals("Mr. Smith", space("Mr.Smith"))
		assertEquals("don't. stop", space("don't.stop"))
	}

	@Test
	fun onlyTheLastTokenIsLookedAt() {
		assertEquals("we met at noon. then", space("we met at noon.then"))
		assertNull(space("hello. world"))
		assertNull(space("hello.world "))
	}

	@Test
	fun webAddressesAndFileNamesAreLeftAlone() {
		for (text in listOf(
			"google.com", "www.google.com", "mail.google.com", "example.co.uk", "notes.txt", "photo.jpeg", "index.html",
			"readme.md", "archive.tar.gz", "https://a.example", "john@example.com", "#tag.thing", "user_name.thing", "web.app"
		)) {
			assertNull(text, space(text))
		}
	}

	@Test
	fun numbersAbbreviationsAndEllipsesAreLeftAlone() {
		for (text in listOf("3.14", "v1.2", "1,000", "e.g", "i.e", "U.S.A", "a.m", "Ph.D", "wait...", "wait...what", "what?!", "hello", "ok.")) {
			assertNull(text, space(text))
		}
	}

	@Test
	fun wordsThatAreNotAddressesGetTheirSpaceEvenAfterAnEverydayEnding() {
		// "no", "so", "to", "it" are also domain endings, but nobody types those as addresses.
		assertEquals("really. no", space("really.no"))
		assertEquals("go. to", space("go.to"))
	}

	// --- Words that ran together ---

	@Test
	fun laughterIsRecognized() {
		for (word in listOf("haha", "hahaha", "hahahaha", "hehe", "hehehe", "lol", "lolol", "loool", "lmao", "lmfao", "lmaooo", "ahaha", "rofl", "HAHAHA")) {
			assertTrue(word, Spacing.isLaughter(word))
		}
		for (word in listOf("hello", "ha", "he", "holy", "hat", "love", "lot", "hole")) {
			assertFalse(word, Spacing.isLaughter(word))
		}
	}

	@Test
	fun aWordRunTogetherWithLaughterIsSplit() {
		assertEquals("love hahaha", split("lovehahaha"))
		assertEquals("Love hahaha", split("Lovehahaha"))
		assertEquals("that lol", split("thatlol"))
		assertEquals("lol that", split("lolthat"))
		assertEquals("haha yes", split("hahayes"))
	}

	@Test
	fun twoWordsRunTogetherAreSplit() {
		assertEquals("good morning", split("goodmorning"))
		assertEquals("thank you", split("thankyou"))
		assertEquals("hello world", split("helloworld"))
		assertEquals("see you", split("seeyou"))
		assertEquals("some more", split("somemore"))
	}

	@Test
	fun smallWordsStartAPhrase() {
		assertEquals("I have", split("ihave"))
		assertEquals("to the", split("tothe"))
		assertEquals("of course", split("ofcourse"))
		assertEquals("in the", split("inthe"))
		assertEquals("lots of", split("lotsof"))
	}

	@Test
	fun threeWordsCanBeSplit() {
		assertEquals("I love you", split("iloveyou"))
		assertEquals("go to the", split("gotothe"))
	}

	@Test
	fun aWordThatIsAlreadyOneIsNotSplit() {
		for (word in listOf("hello", "morning", "something", "everyone", "another", "hahaha", "lol", "cannot", "whatever")) {
			assertNull(word, split(word))
		}
	}

	@Test
	fun typosAreNotSplitWhenTheSplitHasToBeSure() {
		// These are one word with a slip, not two words, and are for the spell checker. A split that is made
		// even when a spelling is close only takes pieces that are very common words.
		for (word in listOf(
			"definately", "recieve", "wierd", "becuase", "seperate", "occured", "untill", "tommorow", "accomodate", "goverment",
			"thier", "freind", "beleive", "diffrent", "intresting", "probaly", "realy", "peice", "tomorow", "wouldnt", "couldnt",
			"didnt", "youre", "theyre", "thats", "wasnt", "shouldnt"
		)) {
			assertNull("$word became ${split(word, 6000)}", split(word, 6000))
		}
	}

	@Test
	fun aSureSplitIsStillMade() {
		assertEquals("love hahaha", split("lovehahaha", 6000))
		assertEquals("good morning", split("goodmorning", 6000))
		assertEquals("I love you", split("iloveyou", 6000))
		assertNull(split("diffrent", 6000))
		assertEquals("diff rent", split("diffrent"))
	}

	@Test
	fun namesThatEndInASmallWordAreNotSplit() {
		assertNull(split("Dillon"))
		assertNull(split("Marlon"))
		assertNull(split("Brandonn"))
	}

	@Test
	fun brandsThatAreTwoWordsOnPurposeAreNotSplit() {
		for (word in listOf("facebook", "youtube", "paypal", "snapchat", "minecraft", "iphone", "playstation", "microsoft")) {
			assertNull(word, split(word))
		}
	}

	@Test
	fun shoutingCamelCaseAndSymbolsAreNotSplit() {
		assertNull(split("LOVEHAHAHA"))
		assertNull(split("loveHahaha"))
		assertNull(split("love-hahaha"))
		assertNull(split("love2you"))
	}

	@Test
	fun tooShortOrTooLongIsNotSplit() {
		assertNull(split("isit"))
		assertNull(split("ofit"))
		assertNull(split("a".repeat(40)))
	}

	@Test
	fun withoutAWordListNothingIsSplit() {
		assertNull(Spacing.split("lovehahaha") { -1 })
	}
}
