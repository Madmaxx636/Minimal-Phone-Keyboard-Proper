package io.github.rickybrent.minimal_symlayer_keyboard

/**
 * Works out what a misspelled word was meant to be. It weighs every candidate from the spell checker, from
 * the built in words and from the words the user has used the way a good keyboard does: how likely a slip of
 * the fingers is (a key next to the right one, two letters swapped, a doubled letter) against how common the
 * word is, and how well it follows the word before it.
 */
object Corrector {
	/** A word that could be what was meant, with a score that is higher for a likelier word. */
	class Candidate(val word: String, val score: Double, val distance: Int)

	private val keyRows = arrayOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

	private val keyPositions: Map<Char, Pair<Int, Int>> = buildMap {
		keyRows.forEachIndexed { row, keys -> keys.forEachIndexed { column, key -> put(key, row to column) } }
	}

	/** @return true if the two keys are next to each other on the keyboard (or are the same key). */
	fun keysNear(a: Char, b: Char): Boolean {
		val (rowA, columnA) = keyPositions[a] ?: return false
		val (rowB, columnB) = keyPositions[b] ?: return false
		return when (rowB - rowA) {
			0 -> Math.abs(columnA - columnB) <= 1
			// Each row is shifted half a key from the one above it.
			1 -> columnB == columnA || columnB == columnA - 1
			-1 -> columnA == columnB || columnA == columnB - 1
			else -> false
		}
	}

	private fun isVowel(c: Char) = c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u'

	private const val NEAR_KEY = 0.5
	private const val VOWEL_FOR_VOWEL = 0.7
	private const val DOUBLE_LETTER = 0.6
	private const val SWAPPED = 0.6
	private const val FIRST_LETTER = 0.8
	// A letter that is nowhere near the right key is a rarer slip than one that is missing or extra.
	private const val FAR_SUBSTITUTION = 1.2

	// Words that have to be very common to be worth guessing at, as a word list has odd two letter tokens.
	private val shortWords = setOf(
		"a", "i", "to", "of", "in", "on", "at", "is", "it", "my", "me", "we", "he", "so", "do", "go", "no", "up", "us", "be",
		"by", "or", "as", "an", "if", "am", "oh", "ok", "hi", "yo", "ah", "uh", "um"
	)

	/** Candidates rarer than this are not guessed at, unless the spell checker or the user's words offer them. */
	private const val MAX_RANK = 25000

	private const val COST_WEIGHT = 3.0
	private const val UNKNOWN_RANK = 60000.0

	/**
	 * How much it takes to turn what was [typed] into [target]: 1 for a letter that is missing, extra or wrong,
	 * less for slips that are easy to make: a key next to the right one, a doubled letter, two swapped letters
	 * or a vowel for a vowel, and more for a letter from the other side of the keyboard. The first letter is
	 * rarely wrong, so a wrong one costs more.
	 */
	fun cost(typed: String, target: String): Double {
		val n = typed.length
		val m = target.length
		val d = Array(n + 1) { DoubleArray(m + 1) }
		for (j in 1..m) d[0][j] = d[0][j - 1] + insertion(target, j)
		for (i in 1..n) d[i][0] = d[i - 1][0] + extra(typed, i)
		for (i in 1..n) {
			for (j in 1..m) {
				var best = minOf(
					d[i - 1][j] + extra(typed, i),
					d[i][j - 1] + insertion(target, j),
					d[i - 1][j - 1] + substitution(typed[i - 1], target[j - 1])
				)
				if (i > 1 && j > 1 && typed[i - 1] == target[j - 2] && typed[i - 2] == target[j - 1] && typed[i - 1] != typed[i - 2]) {
					best = minOf(best, d[i - 2][j - 2] + SWAPPED)
				}
				d[i][j] = best
			}
		}
		// Two swapped letters are one slip, even at the start.
		val first = if (n > 0 && m > 0 && typed[0] != target[0] && !swappedStart(typed, target)) FIRST_LETTER else 0.0
		return d[n][m] + first
	}

	/** The cost of a letter that was typed and should not be there ([i] counts from 1). */
	private fun extra(typed: String, i: Int): Double {
		val doubled = (i >= 2 && typed[i - 1] == typed[i - 2]) || (i < typed.length && typed[i - 1] == typed[i])
		return if (doubled) DOUBLE_LETTER else 1.0
	}

	/** The cost of a letter of the target that was not typed ([j] counts from 1). */
	private fun insertion(target: String, j: Int): Double {
		val doubled = (j >= 2 && target[j - 1] == target[j - 2]) || (j < target.length && target[j - 1] == target[j])
		// The h of "wh", "ch", "sh", "th" and "gh" is the one that is left out most ("wich", "wat", "tink").
		val silentH = j >= 2 && target[j - 1] == 'h' && target[j - 2] in "wcstg"
		return if (doubled || silentH) DOUBLE_LETTER else 1.0
	}

	private fun substitution(typed: Char, target: Char): Double = when {
		typed == target -> 0.0
		keysNear(typed, target) -> NEAR_KEY
		isVowel(typed) && isVowel(target) -> VOWEL_FOR_VOWEL
		else -> FAR_SUBSTITUTION
	}

	/**
	 * @param typed The misspelled word.
	 * @param maxDistance The most letter changes (a swap counts as one) that a candidate may be away.
	 * @param level Decides how bold to be with the first letter, which is rarely wrong.
	 * @param system What the system spell checker suggested, best first.
	 * @param dictionary The built in common words, or null.
	 * @param learned The words the user has used, or null.
	 * @param previous The word before [typed], or an empty string.
	 * @param hints The built in guesses at what follows a word, or null.
	 * @return The candidates that are allowed, likeliest first.
	 */
	fun rank(
		typed: String,
		maxDistance: Int,
		level: AutoCorrectLevel,
		system: List<String>,
		dictionary: BaseDictionary?,
		learned: LearnedWords?,
		previous: String = "",
		hints: NextWordHints? = null
	): List<Candidate> {
		val lower = typed.lowercase()
		if (lower.isEmpty() || maxDistance < 1) return emptyList()
		val found = LinkedHashMap<String, Candidate>()

		val followers = if (previous.isEmpty()) emptyList() else learned?.nextWords(previous, 8).orEmpty().map { it.lowercase() }
		val guessed = if (previous.isEmpty()) emptyList() else hints?.after(previous, 8).orEmpty().map { it.lowercase() }

		fun add(word: String, rank: Int, bonus: Double, uses: Int = 0) {
			val other = word.lowercase()
			if (other == lower || word.length < 2 || word.any { it.isWhitespace() }) return
			val distance = AutoCorrect.editDistance(lower, other)
			if (distance < 1 || distance > maxDistance) return
			// Mistyping the first letter is rarer than the others, so it takes a bolder level to fix. Swapping
			// the first two letters is one slip like any other swap.
			if (other[0] != lower[0] && !swappedStart(lower, other) &&
				!(level == AutoCorrectLevel.HIGH || (level == AutoCorrectLevel.MEDIUM && distance <= 1))
			) return
			var frequency = -Math.log((if (rank >= 0) rank.toDouble() else UNKNOWN_RANK) + 25.0)
			// A word the user has used is one they mean, however rare it is in general.
			if (uses > 0) frequency = maxOf(frequency, -Math.log(30.0 + 400.0 / uses))
			var score = -COST_WEIGHT * cost(lower, other) + frequency + bonus
			val followed = followers.indexOf(other)
			if (followed >= 0) score += 2.0 - followed * 0.1
			else if (guessed.contains(other)) score += 1.5
			val existing = found[other]
			if (existing == null || existing.score < score) found[other] = Candidate(word, score, distance)
		}

		// The spell checker knows things a word list does not, so what it puts first gets a head start.
		system.take(5).forEachIndexed { index, word ->
			add(word, dictionary?.rank(word) ?: -1, arrayOf(1.6, 1.1, 0.8, 0.5, 0.5)[index])
		}
		learned?.near(lower, maxDistance)?.forEach { add(it.word, dictionary?.rank(it.word) ?: -1, 0.0, it.count) }
		dictionary?.let { scan(it, lower, maxDistance, level) { word, rank -> add(word, rank, 0.0) } }
		return found.values.sortedByDescending { it.score }
	}

	/** Offer the common words that could be [lower] with a slip or two, without measuring all of them. */
	private fun scan(dictionary: BaseDictionary, lower: String, maxDistance: Int, level: AutoCorrectLevel, offer: (String, Int) -> Unit) {
		val words = dictionary.words()
		val bag = AutoCorrect.LetterBag(lower)
		for (rank in 0 until minOf(words.size, MAX_RANK)) {
			val word = words[rank]
			if (Math.abs(word.length - lower.length) > maxDistance) continue
			if (word.length < 3 && word !in shortWords) continue
			if (!bag.mayBeWithin(word, maxDistance)) continue
			if (word[0] == lower[0]) {
				offer(word, rank)
			} else if (swappedStart(lower, word) || level == AutoCorrectLevel.HIGH ||
				(level == AutoCorrectLevel.MEDIUM && oneOffAtStart(lower, word))
			) {
				offer(word, rank)
			}
		}
	}

	/** @return true if the words are the same but for the first two letters, which are the other way round. */
	fun swappedStart(a: String, b: String): Boolean =
		a.length == b.length && a.length >= 3 && a[0] == b[1] && a[1] == b[0] && a.regionMatches(2, b, 2, a.length - 2)

	/** @return true if the words are the same but for the first letter: wrong, missing or extra. */
	private fun oneOffAtStart(a: String, b: String): Boolean = when {
		a.length == b.length -> a.regionMatches(1, b, 1, a.length - 1)
		a.length == b.length + 1 -> a.regionMatches(1, b, 0, b.length)
		b.length == a.length + 1 -> b.regionMatches(1, a, 0, a.length)
		else -> false
	}

	/**
	 * @return What to change [word], a finished word that was flagged as a typo, into: the likeliest candidate, in
	 * the capitalization of [word], or null to leave it alone.
	 */
	fun choose(
		word: String,
		level: AutoCorrectLevel,
		atSentenceStart: Boolean,
		isKnown: Boolean,
		isCommon: Boolean,
		system: List<String>,
		dictionary: BaseDictionary?,
		learned: LearnedWords?,
		previous: String = "",
		hints: NextWordHints? = null
	): String? {
		if (!AutoCorrect.eligible(word, level, atSentenceStart, isKnown, isCommon)) return null
		val candidates = rank(word, AutoCorrect.limit(word, level), level, system, dictionary, learned, previous, hints)
		return candidates.firstOrNull()?.let { WordUtils.matchCase(word, it.word) }
	}
}
