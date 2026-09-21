package io.github.rickybrent.minimal_symlayer_keyboard

/** How much of the grammar and punctuation is fixed as it is typed. */
enum class GrammarLevel(val preferenceValue: String) {
	OFF("off"),
	/**
	 * Only what is always wrong: a lowercase "i", a missing apostrophe, "a apple", "could of", a doubled "the",
	 * capitals for names of days and places and at the start of a sentence, and a caps lock slip ("hELLO").
	 */
	BASIC("basic"),
	/** Also words that sound alike, such as their, there and they're, decided by the word after them. */
	FULL("full");

	companion object {
		fun fromPreference(value: String?): GrammarLevel =
			values().firstOrNull { it.preferenceValue == value } ?: FULL
	}
}

/**
 * A change to the words just typed: the text from the word [back] words before the one that was just finished,
 * up to and including that word, is replaced with [replacement]. So 0 changes only the word just finished.
 */
data class GrammarFix(val back: Int, val replacement: String, val rule: String)

/** [text] after a fix, and where in the text it starts to differ from what was typed. */
data class TextChange(val start: Int, val text: String)

/**
 * Fixes grammar and punctuation with rules. It sees the words before the one just finished, never what
 * comes after, and it has no idea what a sentence means, so every rule is limited to a pattern that is hardly
 * ever right as it was typed. A rule that is not sure does nothing.
 */
object Grammar {
	/**
	 * Fix the last word of [text], which was just finished, with the words before it in mind.
	 * @param text What was typed, ending with the word that was just finished (not with the character after it).
	 * @param boundary The character that finished the word.
	 * @param neverAfter Characters that, right before what is to be changed, mean it is a handle, an address or
	 * a file name and not to be touched.
	 * @param capitalizeSentences Whether a word that starts a sentence gets a capital.
	 * @param isWord Whether a lowercase word is a real one, which is what tells a caps lock slip from a name.
	 * @return What the text becomes, or null to leave it as typed.
	 */
	fun change(
		text: String,
		boundary: Char,
		level: GrammarLevel,
		neverAfter: String = "",
		capitalizeSentences: Boolean = false,
		isWord: (String) -> Boolean = { false }
	): TextChange? {
		val word = WordUtils.trailingWord(text)
		val wordStart = text.length - word.length
		val starts = capitalizeSentences && startsSentence(text.substring(0, wordStart))
		val fix = fix(WordUtils.wordsBefore(text, 2), word, boundary, level, starts, isWord) ?: return null
		val fixStarts = WordUtils.wordStarts(text, fix.back)
		if (fixStarts.size <= fix.back) return null
		val start = fixStarts[fix.back]
		if (text.substring(0, start).lastOrNull()?.let { it in neverAfter } == true) return null
		return TextChange(start, text.substring(0, start) + fix.replacement)
	}

	/**
	 * @param previous The words before [word] that are only separated from it by spaces, nearest first.
	 * @param word The word that was just finished.
	 * @param boundary The character that finished it.
	 * @param startsSentence Whether [word] starts a sentence and so takes a capital.
	 * @param isWord Whether a lowercase word is a real one.
	 * @return What to change, or null to leave it as typed.
	 */
	fun fix(
		previous: List<String>,
		word: String,
		boundary: Char,
		level: GrammarLevel,
		startsSentence: Boolean = false,
		isWord: (String) -> Boolean = { false }
	): GrammarFix? {
		if (level == GrammarLevel.OFF || word.isEmpty()) return null
		capsSlip(word, isWord)?.let { return it }
		// Shouting is on purpose, and something with capitals in the middle is a name or code.
		if (word.length > 1 && word.all { !it.isLetter() || it.isUpperCase() }) return null
		if (word.drop(1).any { it.isUpperCase() }) return null

		basic(previous, word, boundary, startsSentence)?.let { fix ->
			// A fix to the word itself starts the sentence with a capital, too.
			return if (startsSentence && fix.back == 0 && fix.rule != "capital") fix.copy(replacement = fix.replacement.replaceFirstChar { it.uppercase() }) else fix
		}
		if (level == GrammarLevel.FULL) return homophones(previous, word, boundary) ?: verbForms(previous, word) ?: agreement(previous, word)
		return null
	}

	/**
	 * @return true if a new sentence starts after [before], where its first word takes a capital. Unlike
	 * [WordUtils.isSentenceStart] this is not fooled by an ellipsis or by the full stop of an abbreviation.
	 */
	fun startsSentence(before: CharSequence): Boolean {
		if (!WordUtils.isSentenceStart(before)) return false
		var i = before.length - 1
		while (i >= 0 && before[i] == ' ') i--
		if (i < 0 || before[i] != '.') return true
		if (i > 0 && before[i - 1] == '.') return false
		var start = i
		while (start > 0 && before[start - 1].isLetter()) start--
		val last = before.substring(start, i).lowercase()
		// "e.g." and "U.S." end in a single letter, "Dr." and "vs." are not the end of a sentence.
		return last.length > 1 && last !in abbreviations
	}

	private val abbreviations = setOf("mr", "mrs", "ms", "dr", "st", "jr", "sr", "prof", "vs", "inc", "ltd", "approx", "dept", "est")

	/**
	 * Caps lock left on for the start of a word ("hELLO") or two capitals at the start ("THe") become "Hello"
	 * and "The", when that is a real word. Names like "iPad" and "IBMs" are not real words, so they stay.
	 */
	private fun capsSlip(word: String, isWord: (String) -> Boolean): GrammarFix? {
		if (word.length < 3 || word.any { !it.isLetter() }) return null
		val lower = word.lowercase()
		val invertedCaps = word[0].isLowerCase() && word.drop(1).all { it.isUpperCase() }
		val twoCapitals = word[0].isUpperCase() && word[1].isUpperCase() && word.drop(2).all { it.isLowerCase() }
		if (!invertedCaps && !twoCapitals) return null
		if (!isWord(lower)) return null
		return GrammarFix(0, lower.replaceFirstChar { it.uppercase() }, "caps lock")
	}

	// --- Basic ---

	private val contractions = mapOf(
		"im" to "I'm", "i'm" to "I'm", "i'll" to "I'll", "i've" to "I've", "i'd" to "I'd",
		"dont" to "don't", "doesnt" to "doesn't", "didnt" to "didn't", "cant" to "can't", "wont" to "won't",
		"isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't", "werent" to "weren't",
		"hasnt" to "hasn't", "havent" to "haven't", "hadnt" to "hadn't", "couldnt" to "couldn't",
		"wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "mustnt" to "mustn't",
		"youre" to "you're", "theyre" to "they're", "youve" to "you've", "theyve" to "they've", "weve" to "we've",
		"youll" to "you'll", "theyll" to "they'll", "itll" to "it'll",
		"thats" to "that's", "whats" to "what's", "theres" to "there's", "heres" to "here's", "wheres" to "where's",
		"whos" to "who's", "hes" to "he's", "shes" to "she's",
		"ive" to "I've", "youd" to "you'd", "theyd" to "they'd", "aint" to "ain't", "yall" to "y'all", "oclock" to "o'clock",
		"hows" to "how's", "thatll" to "that'll", "whatll" to "what'll", "wholl" to "who'll", "therell" to "there'll",
		"shouldve" to "should've", "wouldve" to "would've", "couldve" to "could've", "mightve" to "might've", "mustve" to "must've"
	)

	private val joinedPhrases = mapOf(
		"alot" to "a lot", "aswell" to "as well", "atleast" to "at least", "infact" to "in fact",
		"eachother" to "each other", "noone" to "no one", "incase" to "in case", "everytime" to "every time",
		"ofcourse" to "of course", "thankyou" to "thank you", "infront" to "in front", "inorder" to "in order",
		"alittle" to "a little", "abit" to "a bit", "lotsof" to "lots of", "kindof" to "kind of", "sortof" to "sort of",
		"alotof" to "a lot of"
	)

	// Names that always take a capital, and words that have a capital in an odd place. The ones that are
	// also everyday words (may, march, polish, turkey, apple, orange, china as in dishes) are left out.
	private val properNouns: Map<String, String> = buildMap {
		listOf(
			"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
			"January", "February", "April", "June", "July", "August", "September", "October", "November", "December",
			"English", "Spanish", "French", "German", "Italian", "Chinese", "Japanese", "Korean", "Russian", "Arabic",
			"Portuguese", "Hindi", "American", "British", "Canadian", "Mexican", "European", "African", "Asian", "Australian",
			"Irish", "Scottish", "Christmas", "Easter", "Thanksgiving", "Halloween", "Christian", "Muslim", "Jewish",
			"America", "Canada", "Mexico", "England", "France", "Germany", "Spain", "Italy", "Japan", "Russia", "India",
			"Brazil", "Australia", "Africa", "Europe", "Asia", "Ireland", "Scotland", "Texas", "California", "Florida",
			"London", "Paris", "Tokyo", "Berlin", "Chicago", "Boston", "Seattle", "Toronto",
			"Google", "Facebook", "Instagram", "YouTube", "Twitter", "WhatsApp", "Netflix", "Spotify", "TikTok", "Gmail",
			"iPhone", "iPad", "iPod", "iOS", "USA", "UK"
		).forEach { put(it.lowercase(), it) }
	}

	// Words that are sometimes doubled by mistake and never on purpose ("that that" and "had had" can be right).
	private val repeatable = setOf(
		"the", "a", "an", "to", "of", "in", "on", "at", "for", "and", "or", "but", "be", "it", "i", "we", "you", "my",
		"your", "this", "with", "from", "by", "as", "are", "was", "were", "will", "would", "could", "should", "so"
	)

	private val notHaveAfter = setOf("could", "would", "should", "must", "might")

	private fun basic(previous: List<String>, word: String, boundary: Char, startsSentence: Boolean): GrammarFix? {
		val lower = word.lowercase()
		val prev = previous.getOrNull(0)
		val prevLower = prev?.lowercase()

		// A lone "i" is "I". Not before a full stop, which could be the start of "i.e.".
		if (word == "i" && boundary != '.') return GrammarFix(0, "I", "capital I")

		contractions[lower]?.let { fixed ->
			// "I'm" is already right.
			val replacement = cased(word, fixed)
			if (replacement != word) return GrammarFix(0, replacement, "apostrophe")
		}
		joinedPhrases[lower]?.let { return GrammarFix(0, cased(word, it), "joined words") }

		if (prev != null && prevLower == lower && lower in repeatable) return GrammarFix(1, prev, "repeated word")
		if (prev != null && prevLower in notHaveAfter && lower == "of") return GrammarFix(1, "$prev have", "could have")
		if (prev != null) articleFix(prev, word)?.let { return it }

		properNouns[lower]?.let { if (word != it) return GrammarFix(0, it, "capital") }
		// A word that starts a sentence takes a capital, unless it is slang or laughter, which is often typed small on purpose.
		if (startsSentence && word[0].isLowerCase() && !AutoCorrect.neverFix(word)) {
			return GrammarFix(0, word.replaceFirstChar { it.uppercase() }, "capital")
		}
		return null
	}

	private fun articleFix(prev: String, word: String): GrammarFix? {
		val article = prev.lowercase()
		if (article != "a" && article != "an") return null
		// Acronyms, numbers and names are pronounced in ways a rule can't guess.
		if (word.length < 2 || word.any { !it.isLetter() } || word.drop(1).any { it.isUpperCase() }) return null
		val correct = if (startsWithVowelSound(word.lowercase())) "an" else "a"
		return if (correct == article) null else GrammarFix(1, cased(prev, correct) + " " + word, "a or an")
	}

	// Words that start with a vowel but sound like they start with a consonant.
	private val consonantSounds = setOf(
		"one", "once", "oneself", "use", "used", "useful", "useless", "user", "users", "usual", "usually", "utility",
		"utensil", "utopia", "university", "unit", "units", "unique", "uniform", "union", "united", "universe",
		"universal", "unicorn", "unanimous", "unilateral", "unicycle", "unify", "unified", "ubiquitous", "ewe"
	)

	// Words that start with a consonant but sound like they start with a vowel.
	private val vowelSounds = setOf("hour", "hours", "hourly", "honest", "honestly", "honesty", "honor", "honour", "heir", "heiress")

	private fun startsWithVowelSound(word: String): Boolean = when {
		word in vowelSounds -> true
		word in consonantSounds || word.startsWith("eu") -> false
		else -> word[0] in "aeiou"
	}

	// --- Full: words that sound alike ---

	private val existential = setOf(
		"is", "are", "was", "were", "isn't", "aren't", "wasn't", "weren't", "has", "have", "had", "must", "might",
		"could", "should", "would", "can", "may", "seems", "seem", "exists", "exist"
	)

	// What follows "they're" and hardly ever "their" or "there". Verbs ending in -ing are left out, as
	// "their working conditions" and "their saying" are fine.
	private val afterTheyre = setOf(
		"going", "gonna", "not", "always", "never", "just", "still", "all", "both", "so", "too", "also", "actually",
		"already", "probably", "definitely"
	)

	// Nouns that follow "their", "your", "its" or "whose" and are not also something else after "there", "you're" ...
	private val possessed = setOf(
		"own", "car", "cars", "house", "houses", "home", "homes", "phone", "phones", "name", "names", "mom", "dad",
		"mother", "father", "wife", "husband", "boyfriend", "girlfriend", "brother", "sister", "family", "kids",
		"children", "parents", "dog", "cat", "job", "jobs", "life", "lives", "money", "email", "message", "account",
		"password", "address", "birthday", "room", "bed", "hands", "hand", "eyes", "head", "heart", "hearts", "mind",
		"minds", "opinion", "opinions", "idea", "ideas", "plan", "plans", "team", "boss", "friends", "school",
		"work", "way", "food", "town", "city", "country", "money"
	)

	// Where "there" is right even though a noun follows it, as in "is there any" or "over there people".
	private val existentialBefore = setOf(
		"is", "are", "was", "were", "isn't", "aren't", "wasn't", "weren't", "has", "have", "had", "will", "would",
		"could", "should", "can", "may", "might", "must", "does", "do", "did", "doesn't", "don't", "didn't"
	)
	private val placeBefore = setOf("in", "over", "out", "up", "down", "back", "from", "to", "around", "through", "behind", "under", "near", "right")

	// After "to" these are "too". Words that also make a noun ("to high school", "to fast food") are left out.
	private val tooAdjectives = setOf("much", "many", "late", "early", "soon", "bad", "busy", "tired", "expensive", "easy", "slow", "hard")

	private val bareVerbs = setOf(
		"be", "do", "go", "get", "make", "have", "take", "see", "say", "know", "help", "try", "tell", "ask", "come",
		"use", "find", "give", "work", "call", "send", "let", "put", "keep", "run", "start", "stop", "meet", "talk",
		"buy", "pay", "eat", "sleep", "wait", "leave", "feel", "need", "want"
	)

	private val comparatives = setOf(
		"more", "less", "better", "worse", "bigger", "smaller", "faster", "slower", "higher", "lower", "older",
		"younger", "easier", "harder", "greater", "rather", "other", "larger", "longer", "shorter", "cheaper",
		"stronger", "weaker", "further", "farther", "taller", "smarter", "happier", "safer", "nicer"
	)
	private val beforeThen = setOf("and", "but", "so", "until", "till", "since", "well", "ok", "okay", "back", "just", "only", "right", "from")

	private val loseAfter = setOf("to", "will", "would", "can", "can't", "cannot", "won't", "might", "may", "could", "should", "gonna", "wanna", "must", "don't")

	private val afterItsIs = setOf("a", "an", "the", "not", "been", "going", "gonna", "just", "so", "too", "ok", "okay", "what", "how", "my", "your", "our")
	private val afterItsOwn = setOf("own", "name", "color", "colour", "size", "shape", "purpose", "owner", "price", "weight", "features", "feature")

	private val afterWhosIs = setOf("going", "coming", "been", "a", "an", "the", "not", "there", "here", "that", "this", "he", "she", "it", "you", "gonna")
	private val afterWhosePossessed = setOf("own", "car", "phone", "house", "name", "mom", "dad", "fault", "turn", "idea", "birthday", "coat", "bag", "book", "hat", "keys", "key", "wallet", "shoes")

	// After "your" these are "you're". "Your very own", "your about page" and "your doing" are fine, so they are left out.
	private val afterYoureNotYour = setOf("welcome", "the", "a", "an", "not", "so", "too", "going", "gonna", "always", "never", "just", "still", "all", "both", "also", "actually", "definitely", "probably", "already", "currently")

	private fun homophones(previous: List<String>, word: String, boundary: Char): GrammarFix? {
		val prev = previous.getOrNull(0) ?: return null
		val prevLower = prev.lowercase()
		val prev2 = previous.getOrNull(1)?.lowercase()
		val lower = word.lowercase()
		fun fix(replacement: String, rule: String) = GrammarFix(1, cased(prev, replacement) + " " + word, rule)

		when (prevLower) {
			"their", "there", "they're", "theyre" -> {
				val startsClause = previous.size < 2 || prev2 in clauseStarters
				when {
					prevLower != "there" && lower in existential -> return fix("there", "there")
					// "their going" and "there going" (at the start of a clause) are "they're going".
					prevLower == "their" && (lower in afterTheyre || lower == "the" || lower == "a" || lower == "an") -> return fix("they're", "they're")
					prevLower == "there" && lower in afterTheyre && startsClause -> return fix("they're", "they're")
					prevLower == "there" && lower in possessed && prev2 !in existentialBefore && prev2 !in placeBefore -> return fix("their", "their")
					(prevLower == "they're" || prevLower == "theyre") && lower == "own" -> return fix("their", "their")
				}
			}
			"your" -> if (lower in afterYoureNotYour) return fix("you're", "you're")
			"you're", "youre" -> if (lower in possessed && lower != "friends" && lower != "work" && lower != "way") return fix("your", "your")
			"its" -> if (lower in afterItsIs) return fix("it's", "it's")
			"it's" -> if (lower in afterItsOwn) return fix("its", "its")
			"whose" -> if (lower in afterWhosIs) return fix("who's", "who's")
			"who's" -> if (lower in afterWhosePossessed) return fix("whose", "whose")
			"weather" -> if (lower == "or" || lower == "to") return fix("whether", "whether")
			"were" -> if (lower in afterWere && (previous.size < 2 || prev2 in beforeWereClause)) return fix("we're", "we're")
			"to" -> if (lower in tooAdjectives && !word[0].isUpperCase()) return fix("too", "too")
			"too" -> if (lower in bareVerbs) return fix("to", "to")
		}
		// "Me to." ends a sentence with "too". Somebody who says "to" last has left out what they go to.
		if (lower == "to" && boundary in ".!?" && prevLower in meAlso) return GrammarFix(0, "too", "too")
		if (lower == "to" && prevLower == "suppose" && prev2 in beForms) return fix("supposed", "supposed to")
		if (lower == "than" && prevLower in beforeThen) return GrammarFix(0, cased(word, "then"), "then")
		if (lower == "then" && prevLower in comparatives) return GrammarFix(0, cased(word, "than"), "than")
		if (lower == "loose" && prevLower in loseAfter) return GrammarFix(0, cased(word, "lose"), "lose")
		if (lower == "affect" && prevLower in setOf("the", "an", "no", "side", "any", "little", "big", "huge", "great", "major", "minor", "lasting", "negative", "positive")) {
			return GrammarFix(0, cased(word, "effect"), "effect")
		}
		return null
	}

	private val clauseStarters = setOf("and", "but", "so", "if", "when", "because", "that", "while", "then", "where", "since", "although", "though", "as", "or")

	private val meAlso = setOf("me", "you", "us")

	private val beForms = setOf(
		"am", "is", "are", "was", "were", "be", "been", "isn't", "aren't", "wasn't", "weren't", "not", "i'm", "you're",
		"we're", "they're", "he's", "she's", "it's", "that's", "who's"
	)

	// "Were going" at the start of a sentence is "we're going". Words that can follow "were" in a question
	// ("Were all of them", "Were just the two") are left out.
	private val afterWere = setOf("going", "gonna", "not", "always", "never", "actually", "already", "definitely", "probably")
	private val beforeWereClause = setOf("but", "so", "because", "when", "since", "while", "then")

	// --- Full: subject and verb ---

	private fun agreement(previous: List<String>, word: String): GrammarFix? {
		val prev = previous.getOrNull(0) ?: return null
		val subject = prev.lowercase()
		val lower = word.lowercase()
		// "Does he have" and "can she have" are right: only a subject that starts a clause goes with "has".
		val before = previous.getOrNull(1)?.lowercase()
		val startsClause = before == null || before in clauseStarters
		fun fix(replacement: String, rule: String) = GrammarFix(1, prev + " " + cased(word, replacement), rule)
		return when {
			subject in setOf("he", "she", "it") && lower == "don't" -> fix("doesn't", "doesn't")
			subject in setOf("i", "we", "you", "they") && lower == "doesn't" -> fix("don't", "don't")
			subject in setOf("we", "they", "you") && lower == "was" -> fix("were", "were")
			subject in setOf("we", "they") && lower == "where" -> fix("were", "were")
			subject in setOf("we", "they", "you") && lower == "is" -> fix("are", "are")
			subject in setOf("he", "she", "it") && lower == "are" -> fix("is", "is")
			subject in setOf("he", "she", "it") && lower == "have" && startsClause -> fix("has", "has")
			subject in setOf("they", "we", "you", "i") && lower == "has" && startsClause -> fix("have", "have")
			subject == "i" && lower == "is" -> fix("am", "am")
			subject == "i" && lower == "are" -> fix("am", "am")
			else -> null
		}
	}

	// --- Full: the form of a verb after "have" ---

	private val haveForms = setOf(
		"have", "has", "had", "haven't", "hasn't", "hadn't", "having", "i've", "you've", "we've", "they've",
		"could've", "should've", "would've", "must've", "might've"
	)

	private val participles = mapOf(
		"went" to "gone", "ate" to "eaten", "saw" to "seen", "took" to "taken", "wrote" to "written", "drove" to "driven",
		"spoke" to "spoken", "chose" to "chosen", "began" to "begun", "drank" to "drunk", "sang" to "sung", "swam" to "swum",
		"came" to "come", "did" to "done", "gave" to "given", "knew" to "known", "grew" to "grown", "threw" to "thrown",
		"flew" to "flown", "stole" to "stolen", "fell" to "fallen", "hid" to "hidden", "rode" to "ridden", "shook" to "shaken",
		"blew" to "blown", "ran" to "run", "rang" to "rung", "sank" to "sunk"
	)

	/** "I have went" is "I have gone": after "have" the plain past of these verbs is a mistake. */
	private fun verbForms(previous: List<String>, word: String): GrammarFix? {
		val prev = previous.getOrNull(0)?.lowercase() ?: return null
		if (prev !in haveForms) return null
		val participle = participles[word.lowercase()] ?: return null
		return GrammarFix(0, cased(word, participle), "verb form")
	}

	/** @return [replacement] starting with a capital if [original] does. */
	private fun cased(original: String, replacement: String): String {
		return if (original.isNotEmpty() && original[0].isUpperCase() && replacement[0].isLowerCase()) {
			replacement.replaceFirstChar { it.uppercase() }
		} else {
			replacement
		}
	}
}
