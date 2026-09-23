package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Types sentences that are already correct, one word at a time as the keyboard sees them, and checks that the
 * fixes do not touch any of it. A fix that changes right text is worse than one that is missed.
 */
class ProseStressTest {
	private val dictionary = BaseDictionary.parse(File("src/main/res/raw/common_words_en.txt").readText())
	private val hints = NextWordHints.parse(File("src/main/res/raw/next_words.txt").readText())
	private val fixer = TextFixer(LearnedWords(), dictionary = { dictionary }, hints = { hints })
	private val fine = SpellResult(false, emptyList())

	private val sentences = listOf(
		"Their car is over there and they're going to the store.",
		"Were you there when it happened?",
		"I have gone to the market. We were going to buy bread.",
		"If he were here, he would help us.",
		"Does he have a car? Can she have one? Would it have mattered?",
		"It's a nice day. Its color is blue and its tail is long.",
		"You're welcome to stay. Your friends are here, and your dog is too.",
		"Who's coming to dinner? Whose coat is this?",
		"See www.example.com and notes.txt for the details, or mail john@example.com.",
		"Use e.g. this one, or i.e. that one. Dr. Smith agrees with Mr. Jones.",
		"Wait... what? I don't know. Maybe we should go.",
		"I think that that is right. She had had enough of it.",
		"We went to Paris in June, and we saw the Eiffel Tower on Monday.",
		"He speaks English and Spanish. She lives in London.",
		"I'm sure they're fine. We've been there, and you've seen it.",
		"There are many reasons. There is one more. There was a time when it mattered.",
		"They were late, so we left. You were right about it.",
		"He has a dog and she has a cat. I have a bird. They have fish.",
		"I would have gone if I could have. She should have known better.",
		"The effect was big. It will affect us all. That's the weather for you.",
		"Rather than wait, we left. It's better than nothing. Then we went home.",
		"Don't lose your keys. I could lose track of time. The lid is loose.",
		"It is too late to go, and I want to sleep. That's too much for me.",
		"Me too. I love you too. Thank you so much.",
		"My iPhone is on YouTube, and my iPad is on Netflix.",
		"Yes, please. No, thanks. Okay, then. Well, maybe. Sure, why not?",
		"Call me at 5.30 or on 555.1234. The price is 3.14 dollars. Version 1.2 is out.",
		"Thanks! See you soon. Talk later? Sounds good; let's do it.",
		"What are you doing? Where are they going? When were we there?",
		"We are going to the beach. They are going to the park. She is going home.",
		"I am here. You are there. He is on his way. It is what it is.",
		"An apple a day. An hour ago is right. A university is big.",
		"Once upon a time, there was a user who used a unique keyboard.",
		"Let's meet at 5 o'clock. Y'all come back now. It ain't so bad.",
		"Good morning. Good night. Happy birthday! Merry Christmas.",
		"lol that was funny. hahaha okay. omg no way. brb in five.",
		"He said he'd be there. She said she'll call. We said we'd try.",
		"The team has ten players and they have won. The group is here.",
		"I've seen it. She's taken it. They've written it. We've done it. He's gone.",
		"Sam and Alex are here. Beeper is open. Kubernetes runs it.",
		"Can you help me? Could you tell me where the station is? I'd like to know.",
		"The suppose to be right answer is what I supposed. I was supposed to go."
	)

	@Test
	fun correctSentencesAreNotChanged() {
		val changed = ArrayList<String>()
		for (sentence in sentences) {
			var i = 0
			while (i < sentence.length) {
				if (!WordUtils.isWordChar(sentence[i])) {
					i++
					continue
				}
				var end = i
				while (end < sentence.length && WordUtils.isWordChar(sentence[end])) end++
				// The word is finished by the next character, or by the end of the sentence.
				val boundary = if (end < sentence.length) sentence[end] else ' '
				val text = sentence.substring(0, end)
				val word = WordUtils.trailingWord(text)
				if (word.isNotEmpty() && boundary in " .,!?;:") {
					val fixed = fixer.fix(TextFixer.Settings(), text, boundary, word, fine, systemSaysFine = true, typo = false, neverAfter = "@#/\\._-:+=~")
					if (fixed != null) changed.add("\"${text.takeLast(30)}\" -> \"${fixed.text.takeLast(30)}\" in \"$sentence\"")
				}
				i = end
			}
		}
		assertTrue("Right text was changed:\n" + changed.joinToString("\n"), changed.isEmpty())
	}
}
