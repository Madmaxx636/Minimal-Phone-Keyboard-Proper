package io.github.rickybrent.minimal_symlayer_keyboard

/**
 * Decides what to change in the text just before the cursor when a word is finished: the spaces that were left
 * out after punctuation, then the word itself (a shortcut, words that ran together, or a spelling), then the
 * grammar of the words that result. It knows nothing about the editor, so that it can be tested on its own.
 */
class TextFixer(
	private val learned: LearnedWords,
	private val dictionary: () -> BaseDictionary?,
	private val hints: () -> NextWordHints?
) {
	/** What is turned on. */
	class Settings(
		val autoCorrect: AutoCorrectLevel = AutoCorrectLevel.MEDIUM,
		val grammar: GrammarLevel = GrammarLevel.FULL,
		val autoSpace: Boolean = true,
		val splitWords: Boolean = true,
		val capitalizeSentences: Boolean = true,
		val personal: PersonalDictionary = PersonalDictionary.EMPTY
	)

	/**
	 * @param text What the text becomes: all of what was given to [fix], changed.
	 * @param typo The misspelled word if a fix was a correction of it, and [correction] what it became.
	 * @param learn Whether the last word is worth learning: it is not when it is what a shortcut stands for.
	 */
	class Fixed(val text: String, val typo: String?, val correction: String?, val learn: Boolean)

	/**
	 * @param text What was typed, ending with the finished [word] (not with the character that finished it).
	 * @param boundary The character that finished the word.
	 * @param spell What the spell checker said about [word], or null if there is no opinion.
	 * @param systemSaysFine Whether the system spell checker knows [word] to be a word.
	 * @param typo Whether [word] is a typo that is left as it is unless it is fixed here.
	 * @param neverAfter Characters that, right before a word, mean it is a handle, an address or a file name.
	 * @return The fixed text, or null if there is nothing to change.
	 */
	fun fix(
		settings: Settings,
		text: String,
		boundary: Char,
		word: String,
		spell: SpellResult?,
		systemSaysFine: Boolean,
		typo: Boolean,
		neverAfter: String
	): Fixed? {
		var current = text
		var currentWord = word
		val capitalize = settings.capitalizeSentences

		if (settings.autoSpace) {
			Spacing.afterPunctuation(current, capitalize)?.let {
				current = it
				currentWord = WordUtils.trailingWord(it)
			}
		}

		var typoFixed: String? = null
		var correction: String? = null
		var learn = true
		var newWord: String? = null
		val before = current.substring(0, current.length - currentWord.length)
		// Handles, addresses and file names are not words to fix.
		val protected = before.lastOrNull()?.let { it in neverAfter } == true
		if (currentWord.isNotEmpty() && !protected) {
			val expansion = settings.personal.expansion(currentWord)
			if (expansion != null) {
				newWord = WordUtils.matchCase(currentWord, expansion)
				learn = false
			} else {
				// Two very common words run together are that, but for anything less clear-cut a spelling that is
				// close comes first: "diffrent" is not "diff rent".
				val spelled = habitFor(settings, currentWord)
					?: splitFor(settings, currentWord, systemSaysFine, SURE_SPLIT_RANK)
					?: spellingFor(settings, currentWord, before, spell)
					?: splitFor(settings, currentWord, systemSaysFine, LOOSE_SPLIT_RANK)
				if (spelled != null) {
					newWord = spelled
					if (' ' !in spelled) {
						typoFixed = currentWord
						correction = spelled
					}
					// A phrase starts a sentence with a capital like any other word does.
					if (' ' in spelled && settings.grammar != GrammarLevel.OFF && capitalize && Grammar.startsSentence(before)) {
						newWord = spelled.replaceFirstChar { it.uppercase() }
					}
				}
			}
		}
		if (newWord != null) current = before + newWord

		// The grammar of what has now been typed. A word that is a typo which is left as it is has none, and neither
		// has what a shortcut stands for.
		if (learn && (newWord != null || !typo) && settings.grammar != GrammarLevel.OFF) {
			val known = dictionary()
			Grammar.change(
				current, boundary, settings.grammar, neverAfter, capitalize,
				isWord = { known?.contains(it) == true || isKnownWord(settings, it) }
			)?.let { current = it.text }
		}
		return if (current == text) null else Fixed(current, typoFixed, correction, learn)
	}

	private fun isKnownWord(settings: Settings, word: String) = learned.isKnown(word) || settings.personal.contains(word)

	/** @return What the user has made a habit of changing [word] into, or null. */
	private fun habitFor(settings: Settings, word: String): String? {
		if (settings.autoCorrect == AutoCorrectLevel.OFF) return null
		return learned.correctionFor(word)?.let { WordUtils.matchCase(word, it) }
	}

	/** @return [word] as several words if it is a few words that ran together, or null. */
	private fun splitFor(settings: Settings, word: String, systemSaysFine: Boolean, maxRank: Int): String? {
		if (!settings.splitWords || systemSaysFine) return null
		// Whatever is a word to the built in list, the user's words or the ones they added counts as one.
		val known = dictionary()
		val pieces = Spacing.split(word, maxRank) { piece ->
			val rank = known?.rank(piece) ?: -1
			when {
				rank >= 0 -> rank
				isKnownWord(settings, piece) -> KNOWN_WORD_RANK
				else -> -1
			}
		}
		return pieces?.joinToString(" ")
	}

	/**
	 * @return What to change the finished [word], which follows [before], into: the likeliest of what the spell
	 * checker says and what the common words and the user's words offer, as far as the level allows.
	 */
	private fun spellingFor(settings: Settings, word: String, before: String, spell: SpellResult?): String? {
		if (settings.autoCorrect == AutoCorrectLevel.OFF || spell == null || !spell.isTypo) return null
		val known = dictionary()
		return Corrector.choose(
			word, settings.autoCorrect, WordUtils.isSentenceStart(before),
			isKnown = isKnownWord(settings, word), isCommon = known?.contains(word) == true,
			system = spell.corrections, dictionary = known, learned = learned,
			previous = WordUtils.wordsBefore(before, 1).firstOrNull().orEmpty(), hints = hints()
		)
	}

	companion object {
		/** How common the words that the user added or used a lot count as, when splitting words that ran together. */
		private const val KNOWN_WORD_RANK = 3000

		/** A split into words that are all more common than this is made even when a spelling is close. */
		private const val SURE_SPLIT_RANK = 6000

		/**
		 * The last resort split, tried when nothing else fixed the word, allows pieces from anywhere in the
		 * common word list. It is capped rather than left unlimited so that the rarer words near the end of a
		 * larger list, which are there to help spelling and completions, don't also make more names and made
		 * up words look like two words run together.
		 */
		private const val LOOSE_SPLIT_RANK = 30000
	}
}
