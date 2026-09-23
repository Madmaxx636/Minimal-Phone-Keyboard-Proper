package io.github.rickybrent.minimal_symlayer_keyboard

/**
 * Helpers for finding the word being typed and matching the capitalization of what was typed.
 */
object WordUtils {
	/** Words shorter than this are not checked or completed. */
	const val MIN_WORD_LENGTH = 2

	fun isWordChar(c: Char): Boolean = c.isLetter() || c == '\'' || c == '’'

	/**
	 * @return The word that ends at the end of [text], or an empty string if [text] doesn't end in one.
	 */
	fun trailingWord(text: CharSequence): String {
		var start = text.length
		while (start > 0 && isWordChar(text[start - 1])) start--
		// Part of a longer token like "abc123d" or "snake_case", which is not worth suggesting for.
		if (start > 0 && (text[start - 1].isDigit() || text[start - 1] == '_')) return ""
		// A leading apostrophe is a quote mark, not part of the word.
		while (start < text.length && !text[start].isLetter()) start++
		return text.substring(start)
	}

	/**
	 * @return The word before the one at the end of [text], if it is separated from it by spaces only.
	 * The word at the end of [text] may be empty, so for "hello " this is "hello" and for "hello world" too.
	 * This is empty after punctuation, e.g. for "Hello. ", as a new sentence does not follow on from the last.
	 */
	fun wordBefore(text: CharSequence): String {
		var end = text.length
		while (end > 0 && isWordChar(text[end - 1])) end--
		val wordStart = end
		while (end > 0 && text[end - 1] == ' ') end--
		if (end == wordStart) return ""
		return trailingWord(text.subSequence(0, end))
	}

	/**
	 * @return Up to [count] words before the one at the end of [text], nearest first, as long as only spaces
	 * separate them. So for "how are you doing" with 2 this is "you", "are". Punctuation ends the run.
	 */
	fun wordsBefore(text: CharSequence, count: Int): List<String> {
		val result = ArrayList<String>(count)
		// Skip the word at the end, which may be empty.
		var end = text.length
		while (end > 0 && isWordChar(text[end - 1])) end--
		while (result.size < count) {
			val spacesEnd = end
			while (end > 0 && text[end - 1] == ' ') end--
			if (end == spacesEnd) break
			val word = trailingWord(text.subSequence(0, end))
			if (word.isEmpty()) break
			result.add(word)
			while (end > 0 && isWordChar(text[end - 1])) end--
		}
		return result
	}

	/**
	 * @return Where the word at the end of [text] starts, then where the words before it start, nearest first, up
	 * to [count] of them, as long as only spaces separate them. This is where [wordsBefore] finds its words.
	 */
	fun wordStarts(text: CharSequence, count: Int): List<Int> {
		val result = ArrayList<Int>(count + 1)
		var end = text.length
		var start = end
		while (start > 0 && isWordChar(text[start - 1])) start--
		result.add(end - trailingWord(text).length)
		end = start
		while (result.size <= count) {
			val spacesEnd = end
			while (end > 0 && text[end - 1] == ' ') end--
			if (end == spacesEnd) break
			val word = trailingWord(text.subSequence(0, end))
			if (word.isEmpty()) break
			result.add(end - word.length)
			while (end > 0 && isWordChar(text[end - 1])) end--
		}
		return result
	}

	/**
	 * @return true if a new sentence starts after [text] (ignoring trailing spaces): at the very start,
	 * after a full stop, question mark, exclamation mark or ellipsis, or on a new line.
	 */
	fun isSentenceStart(text: CharSequence): Boolean {
		var i = text.length - 1
		while (i >= 0 && text[i] == ' ') i--
		if (i < 0) return true
		return text[i] == '.' || text[i] == '?' || text[i] == '!' || text[i] == '\n' || text[i] == '\u2026'
	}

	/**
	 * @return [suggestion] with the capitalization style of [typed]: ALL CAPS, Capitalized, or as is.
	 */
	fun matchCase(typed: String, suggestion: String): String {
		if (typed.isEmpty() || suggestion.isEmpty()) return suggestion
		val letters = typed.filter { it.isLetter() }
		if (letters.length > 1 && letters.all { it.isUpperCase() }) {
			return suggestion.uppercase()
		}
		if (typed[0].isUpperCase() && suggestion[0].isLowerCase()) {
			return suggestion.replaceFirstChar { it.uppercase() }
		}
		return suggestion
	}
}

/** A word that was learned, for listing. */
data class LearnedWord(val word: String, val count: Int)

/** A correction that is a habit, for listing. */
data class LearnedCorrection(val typo: String, val fix: String, val count: Int)

/**
 * The words the user has typed or accepted, used to suggest completions and to avoid flagging
 * their names and jargon as typos. This is stored on the device only.
 *
 * Words are suggested from the first time they are used. Only words that the spell checker did not flag
 * are learned as they are typed, and a word must have been seen [KNOWN_COUNT] times before it stops
 * being flagged as a typo, so that a typo that slipped through once is not trusted.
 */
class LearnedWords(
	private val maxWords: Int = DEFAULT_MAX_WORDS,
	private val maxPairs: Int = DEFAULT_MAX_PAIRS
) {
	private class Entry {
		var total = 0
		/** How recently it was used, see [clock]. */
		var seq = 0L
		// Exact capitalizations seen, so "Paris" is suggested as "Paris" but "the" is not "The".
		val forms = HashMap<String, Int>(2)

		/** The most used form. Of equally used ones, the one with the fewest capitals. */
		fun bestForm(): String = forms.entries
			.sortedWith(
				compareByDescending<Map.Entry<String, Int>> { it.value }
					.thenBy { form -> form.key.count { it.isUpperCase() } }
					.thenBy { it.key }
			)
			.first().key
	}

	// Keyed by the lowercase word.
	private val entries = HashMap<String, Entry>()

	private class Follow(var count: Int, var seq: Long, var form: String)

	// The words that followed each word, both lowercase, to suggest the next word. [Follow.seq] says how
	// recently, and [Follow.form] is the capitalization it was last typed with (as in "I" or "Paris").
	private val followers = HashMap<String, HashMap<String, Follow>>()
	private var pairCount = 0
	private var clock = 0L

	// Corrections that the user makes a habit of, keyed by the lowercase typo, see [learnCorrection].
	private class Habit(var fix: String, var count: Int)
	private val habits = HashMap<String, Habit>()

	// How many times a word that the spell checker does not recognize has been typed, keyed by the
	// lowercase word, so that it can be learned on its own once that happens enough, see [sawUnrecognized].
	private val unrecognizedSeen = HashMap<String, Int>()

	// How much has been learned since old counts were last halved, and whether that is being counted.
	private var events = 0
	private var bulk = 0

	/** true if there are changes that have not been saved with [serialize]. */
	var isDirty = false
		private set

	val size: Int get() = entries.size

	/** How many pairs of words are known, see [learnPair]. */
	val pairSize: Int get() = pairCount

	fun markSaved() {
		isDirty = false
	}

	/** Record [weight] uses of [word]. */
	fun learn(word: String, weight: Int = 1) {
		if (word.length < WordUtils.MIN_WORD_LENGTH || weight <= 0 || !word.all { WordUtils.isWordChar(it) }) return
		val entry = entries.getOrPut(word.lowercase()) { Entry() }
		entry.total += weight
		entry.seq = ++clock
		entry.forms[word] = (entry.forms[word] ?: 0) + weight
		isDirty = true
		if (entries.size > maxWords) trim()
		countEvent()
	}

	/**
	 * Record that [word], which the spell checker does not recognize, was typed again, so that it can be
	 * learned on its own once it has been typed enough not to be a one-off slip, without the user having
	 * to tap it to save it. The count is forgotten once it is learned this way.
	 * @return true the first time [word] has been typed more than [UNRECOGNIZED_LEARN_AFTER] times.
	 */
	fun sawUnrecognized(word: String): Boolean {
		if (word.length < WordUtils.MIN_WORD_LENGTH || !word.all { WordUtils.isWordChar(it) }) return false
		val lower = word.lowercase()
		val count = (unrecognizedSeen[lower] ?: 0) + 1
		if (count > UNRECOGNIZED_LEARN_AFTER) {
			unrecognizedSeen.remove(lower)
			return true
		}
		unrecognizedSeen[lower] = count
		isDirty = true
		// A lot of one-off typos should not be left to pile up.
		if (unrecognizedSeen.size > MAX_UNRECOGNIZED) unrecognizedSeen.entries.removeAll { it.value <= 1 }
		return false
	}

	/**
	 * Learn from a text: every word, and which word followed which. A word only follows another when
	 * nothing but spaces come between them, so punctuation and line breaks start a new run of words.
	 * Tokens that are part of something else, like "abc123" or "snake_case", are skipped.
	 * @return The number of words that were read.
	 */
	fun learnText(text: CharSequence): Int {
		bulk++
		try {
			return learnTextInternal(text)
		} finally {
			bulk--
		}
	}

	private fun learnTextInternal(text: CharSequence): Int {
		var count = 0
		var prev = ""
		var prev2 = ""
		var i = 0
		val n = text.length
		while (i < n) {
			val c = text[i]
			if (!WordUtils.isWordChar(c)) {
				// Only spaces keep a run of words going.
				if (c != ' ' && c != '\t' && c != '\u00a0') {
					prev = ""
					prev2 = ""
				}
				i++
				continue
			}
			var end = i
			while (end < n && WordUtils.isWordChar(text[end])) end++
			val partOfLongerToken = (i > 0 && (text[i - 1].isDigit() || text[i - 1] == '_')) ||
				(end < n && (text[end].isDigit() || text[end] == '_'))
			val word = text.substring(i, end).trim('\'', '\u2019')
			i = end
			if (partOfLongerToken || word.none { it.isLetter() }) {
				prev = ""
				prev2 = ""
				continue
			}
			learn(word)
			if (prev.isNotEmpty()) learnPair(prev, word)
			if (prev2.isNotEmpty()) learnTriple(prev2, prev, word)
			prev2 = prev
			prev = word
			count++
		}
		return count
	}

	/** Record [weight] times that [next] was typed after [prev]. */
	fun learnPair(prev: String, next: String, weight: Int = 1) {
		if (weight <= 0 || !isPairWord(prev) || !isPairWord(next)) return
		addPair(prev, next, weight, clock + 1, next)
		isDirty = true
		if (pairCount > maxPairs) trimPairs()
		countEvent()
	}

	/**
	 * Record that [next] was typed after the two words [prev2] and [prev], so that phrases are remembered
	 * and not only pairs of words. It is read back with `nextWords(prev, limit, before = prev2)`.
	 */
	fun learnTriple(prev2: String, prev: String, next: String, weight: Int = 1) {
		if (weight <= 0 || !isPairWord(prev2) || !isPairWord(prev) || !isPairWord(next)) return
		addPair(phraseKey(prev2, prev), next, weight, clock + 1, next)
		isDirty = true
		if (pairCount > maxPairs) trimPairs()
		countEvent()
	}

	/** Record that [word] started a sentence. Read back with `nextWords(START, ...)`. */
	fun learnStarter(word: String) {
		if (!isPairWord(word)) return
		addPair(START, word, 1, clock + 1, word)
		isDirty = true
		if (pairCount > maxPairs) trimPairs()
		countEvent()
	}

	/**
	 * Remember that the user turns [typo] into [fix], for instance by picking the correction or by not undoing it.
	 * Once it has happened twice (or was chosen on purpose) [correctionFor] gives it back, so it is applied
	 * even where the spell checker has no opinion. [weight] says how deliberate it was.
	 */
	fun learnCorrection(typo: String, fix: String, weight: Int = 1) {
		if (weight <= 0 || typo.length < 2 || !isPairWord(typo) || !isPairWord(fix)) return
		val key = typo.lowercase()
		if (key == fix.lowercase()) return
		val habit = habits[key]
		if (habit == null) {
			habits[key] = Habit(fix, weight)
		} else if (habit.fix.equals(fix, ignoreCase = true)) {
			habit.count += weight
			habit.fix = fix
		} else {
			// A different fix: the old habit has to give way before the new one takes over.
			habit.count -= weight
			if (habit.count <= 0) {
				habit.fix = fix
				habit.count = weight
			}
		}
		isDirty = true
	}

	/** @return What [typo] is a habit of being corrected into, or null. */
	fun correctionFor(typo: String): String? {
		val habit = habits[typo.lowercase()] ?: return null
		return if (habit.count >= HABIT_COUNT) habit.fix else null
	}

	/** Stop correcting [typo] on its own. @return true if there was a habit. */
	fun forgetCorrection(typo: String): Boolean {
		val removed = habits.remove(typo.lowercase()) != null
		if (removed) isDirty = true
		return removed
	}

	/** @return The corrections that are a habit, most used first. */
	fun corrections(): List<LearnedCorrection> = habits.entries
		.filter { it.value.count >= HABIT_COUNT }
		.map { LearnedCorrection(it.key, it.value.fix, it.value.count) }
		.sortedWith(compareByDescending<LearnedCorrection> { it.count }.thenBy { it.typo })

	/**
	 * Forget [word] completely: it is no longer suggested, and the pairs it is part of are forgotten too.
	 * @return true if it was known.
	 */
	fun forgetWord(word: String): Boolean {
		val lower = word.lowercase()
		var removed = entries.remove(lower) != null
		// The pairs and phrases that start with it, and the ones that end in it.
		if (followers.keys.removeAll { it == lower || it.split(' ').contains(lower) }) removed = true
		for (follows in followers.values) {
			if (follows.remove(lower) != null) removed = true
		}
		if (habits.entries.removeAll { it.key == lower || it.value.fix.equals(lower, ignoreCase = true) }) removed = true
		followers.values.removeAll { it.isEmpty() }
		pairCount = followers.values.sumOf { it.size }
		if (removed) isDirty = true
		return removed
	}

	/**
	 * Forget that [next] follows [prev], leaving both words themselves alone. [prev] may be [START].
	 * @return true if the pair was known.
	 */
	fun forgetPair(prev: String, next: String): Boolean {
		val lowerPrev = prev.lowercase()
		val lowerNext = next.lowercase()
		var removed = false
		// The pair itself, and the phrases that end in it, which would bring it right back.
		for ((key, follows) in followers) {
			if ((key == lowerPrev || key.endsWith(" $lowerPrev")) && follows.remove(lowerNext) != null) removed = true
		}
		followers.values.removeAll { it.isEmpty() }
		pairCount = followers.values.sumOf { it.size }
		if (removed) isDirty = true
		return removed
	}

	/** @return Every learned word with how many times it was used, most used first. */
	fun words(): List<LearnedWord> = entries.values
		.map { LearnedWord(it.bestForm(), it.total) }
		.sortedWith(compareByDescending<LearnedWord> { it.count }.thenBy { it.word.lowercase() })

	/**
	 * @return The words that were typed most after [prev], ties going to the most recent,
	 * capitalized as they were typed.
	 */
	fun nextWords(prev: String, limit: Int, before: String = ""): List<String> {
		if (limit <= 0) return emptyList()
		val result = ArrayList<String>(limit)
		fun addFrom(key: String) {
			val follows = followers[key] ?: return
			for (follow in follows.values.sortedWith(compareByDescending<Follow> { it.count }.thenByDescending { it.seq })) {
				if (result.size < limit && result.none { it.equals(follow.form, ignoreCase = true) }) result.add(follow.form)
			}
		}
		// What followed these two words is a better guide than what followed the last one alone.
		if (before.isNotEmpty() && isPairWord(before) && isPairWord(prev)) addFrom(phraseKey(before, prev))
		addFrom(prev.lowercase())
		return result
	}

	private fun phraseKey(prev2: String, prev: String) = prev2.lowercase() + " " + prev.lowercase()

	private fun isKey(key: String): Boolean {
		if (key == START || isPairWord(key)) return true
		val parts = key.split(' ')
		return parts.size == 2 && parts.all { isPairWord(it) }
	}

	// Unlike the words that are suggested for, these can be short: "I" and "a" are worth predicting.
	private fun isPairWord(word: String) = word.all { WordUtils.isWordChar(it) } && word.any { it.isLetter() }

	private fun addPair(prev: String, next: String, count: Int, seq: Long, form: String) {
		val follows = followers.getOrPut(prev.lowercase()) { HashMap() }
		val follow = follows[next.lowercase()]
		if (follow == null) {
			follows[next.lowercase()] = Follow(count, seq, form)
			pairCount++
		} else {
			follow.count += count
			if (seq >= follow.seq) {
				follow.seq = seq
				follow.form = form
			}
		}
		if (seq > clock) clock = seq
	}

	/** Forget the least used and oldest pairs when there are too many. */
	private fun trimPairs() {
		val all = followers.flatMap { (prev, follows) -> follows.map { Triple(prev, it.key, it.value) } }
		all.sortedWith(compareBy<Triple<String, String, Follow>> { it.third.count }.thenBy { it.third.seq })
			.take(all.size - maxPairs * 9 / 10)
			.forEach { (prev, next, _) -> followers[prev]?.remove(next) }
		followers.values.removeAll { it.isEmpty() }
		pairCount = followers.values.sumOf { it.size }
	}

	/** @return How many times [word] has been seen, ignoring capitalization. */
	fun count(word: String): Int = entries[word.lowercase()]?.total ?: 0

	fun isKnown(word: String): Boolean = count(word) >= KNOWN_COUNT

	/**
	 * @return The known words that are one to [maxDistance] letter changes away from [word], for finding out what
	 * a misspelled word was meant to be. A word that was only typed once could be a typo, so it is not offered.
	 */
	fun near(word: String, maxDistance: Int): List<LearnedWord> {
		val lower = word.lowercase()
		val bag = AutoCorrect.LetterBag(lower)
		val result = ArrayList<LearnedWord>()
		for ((key, entry) in entries) {
			if (entry.total < KNOWN_COUNT || key == lower || Math.abs(key.length - lower.length) > maxDistance) continue
			if (!bag.mayBeWithin(key, maxDistance)) continue
			if (AutoCorrect.editDistance(lower, key) <= maxDistance) result.add(LearnedWord(entry.bestForm(), entry.total))
		}
		return result
	}

	/**
	 * @return The most used words that start with [prefix] (but are longer than it),
	 * capitalized to match [prefix].
	 */
	fun completions(prefix: String, limit: Int): List<String> {
		if (prefix.length < WordUtils.MIN_WORD_LENGTH || limit <= 0) return emptyList()
		val lower = prefix.lowercase()
		return entries.entries
			.filter { it.key.length > lower.length && it.key.startsWith(lower) }
			.sortedWith(
				compareByDescending<Map.Entry<String, Entry>> { it.value.total }
					.thenByDescending { it.value.seq }
					.thenBy { it.key.length }
					.thenBy { it.key }
			)
			.take(limit)
			.map { WordUtils.matchCase(prefix, it.value.bestForm()) }
	}

	fun clear() {
		if (entries.isNotEmpty() || pairCount > 0 || habits.isNotEmpty() || unrecognizedSeen.isNotEmpty()) isDirty = true
		entries.clear()
		followers.clear()
		habits.clear()
		unrecognizedSeen.clear()
		pairCount = 0
	}

	/** Forget the least used words when there are too many. */
	private fun trim() {
		val keep = maxWords * 9 / 10
		entries.entries
			.sortedBy { it.value.total }
			.take(entries.size - keep)
			.forEach { entries.remove(it.key) }
	}

	/**
	 * One line per exact form: the word, a tab, and how many times it was used. Then one line per pair:
	 * the first word, the second word, how many times, how recently, and the capitalization of the second, tab separated.
	 */
	fun serialize(): String {
		val text = StringBuilder()
		for (entry in entries.values) {
			for ((form, count) in entry.forms) {
				text.append(form).append('\t').append(count).append('\n')
			}
		}
		for ((prev, follows) in followers) {
			for ((next, follow) in follows) {
				text.append(prev).append('\t').append(next).append('\t').append(follow.count).append('\t')
					.append(follow.seq).append('\t').append(follow.form).append('\n')
			}
		}
		for ((typo, habit) in habits) {
			text.append(HABIT_MARK).append('\t').append(typo).append('\t').append(habit.fix).append('\t')
				.append(habit.count).append('\n')
		}
		for ((word, count) in unrecognizedSeen) {
			text.append(UNRECOGNIZED_MARK).append('\t').append(word).append('\t').append(count).append('\n')
		}
		return text.toString()
	}

	/** Add the words saved by [serialize]. Lines that can't be understood are skipped. */
	fun load(text: String) {
		bulk++
		try {
			loadInternal(text)
		} finally {
			bulk--
		}
	}

	private fun loadInternal(text: String) {
		for (line in text.lineSequence()) {
			val parts = line.split('\t')
			if (parts.size == 4 && parts[0] == HABIT_MARK) {
				val count = parts[3].toIntOrNull() ?: continue
				if (count > 0 && isPairWord(parts[1]) && isPairWord(parts[2])) habits[parts[1].lowercase()] = Habit(parts[2], count)
				continue
			}
			if (parts.size == 3 && parts[0] == UNRECOGNIZED_MARK) {
				val count = parts[2].toIntOrNull() ?: continue
				val word = parts[1]
				if (count in 1..UNRECOGNIZED_LEARN_AFTER && word.length >= WordUtils.MIN_WORD_LENGTH && isPairWord(word)) {
					unrecognizedSeen[word.lowercase()] = count
				}
				continue
			}
			if (parts.size == 5) {
				val count = parts[2].toIntOrNull() ?: continue
				val seq = parts[3].toLongOrNull() ?: continue
				if (count > 0 && isKey(parts[0]) && isPairWord(parts[1]) && isPairWord(parts[4])) {
					addPair(parts[0], parts[1], count, seq, parts[4])
				}
				continue
			}
			val count = parts.getOrNull(1)?.toIntOrNull() ?: continue
			learn(parts[0], count)
		}
		isDirty = false
	}

	/**
	 * Count something that was learned. After enough of it the old counts are halved, so that what the user
	 * does now outweighs what they did long ago, while everything keeps its order.
	 */
	private fun countEvent() {
		if (bulk > 0) return
		if (++events < AGE_EVERY) return
		events = 0
		fun half(n: Int) = (n + 1) / 2
		for (entry in entries.values) {
			entry.total = half(entry.total)
			for (form in entry.forms.keys.toList()) entry.forms[form] = half(entry.forms.getValue(form))
		}
		for (follows in followers.values) {
			for (follow in follows.values) follow.count = half(follow.count)
		}
		for (habit in habits.values) habit.count = half(habit.count)
	}

	companion object {
		/** Stands for the start of a sentence where a word that comes before another is expected. */
		const val START = "^"

		const val DEFAULT_MAX_WORDS = 20000
		const val DEFAULT_MAX_PAIRS = 60000
		const val KNOWN_COUNT = 2

		/** How often a correction must have been made before it is done automatically. */
		const val HABIT_COUNT = 2

		/** The old counts are halved after this much has been learned. */
		const val AGE_EVERY = 30000

		/** A word not in any dictionary is learned on its own after being typed more than this many times. */
		const val UNRECOGNIZED_LEARN_AFTER = 2

		/** How many not-yet-recognized words are tracked at once, so a run of one-off typos can't pile up. */
		const val MAX_UNRECOGNIZED = 2000

		private const val HABIT_MARK = "~"
		private const val UNRECOGNIZED_MARK = "?"
	}
}

/**
 * Common words, most common first, to complete words before [LearnedWords] has learned enough.
 */
class BaseDictionary(private val words: List<String>) {
	// Where each word is in the list, which is how common it is.
	private val ranks by lazy {
		val map = HashMap<String, Int>(words.size * 2)
		words.forEachIndexed { index, word -> map.putIfAbsent(word, index) }
		map
	}

	/** @return true if [word] is one of the common words, ignoring capitalization. */
	fun contains(word: String): Boolean = ranks.containsKey(word.lowercase())

	/** @return How common [word] is: 0 for the most common, larger for rarer, and -1 if it is not one of them. */
	fun rank(word: String): Int = ranks[word.lowercase()] ?: -1

	/** All the words, most common first. */
	fun words(): List<String> = words

	/**
	 * @return The common words that are one to [maxDistance] letter changes away from [word] and start with
	 * the same letter, the closest first and among those the most common.
	 */
	fun nearest(word: String, maxDistance: Int, limit: Int): List<String> {
		val lower = word.lowercase()
		if (lower.isEmpty() || limit <= 0) return emptyList()
		val found = ArrayList<Pair<Int, Int>>()
		for ((index, candidate) in words.withIndex()) {
			if (candidate[0] != lower[0] || Math.abs(candidate.length - lower.length) > maxDistance) continue
			val distance = AutoCorrect.editDistance(lower, candidate)
			if (distance in 1..maxDistance) found.add(distance to index)
		}
		return found
			.sortedWith(compareBy({ it.first }, { it.second }))
			.take(limit)
			.map { words[it.second] }
	}

	/**
	 * @return The most common words that start with [prefix] (but are longer than it), capitalized to
	 * match [prefix], leaving out the ones that [exclude] says no to.
	 */
	fun completions(prefix: String, limit: Int, exclude: (String) -> Boolean = { false }): List<String> {
		if (prefix.length < WordUtils.MIN_WORD_LENGTH || limit <= 0) return emptyList()
		val lower = prefix.lowercase()
		val result = ArrayList<String>(limit)
		for (word in words) {
			if (word.length > lower.length && word.startsWith(lower) && !exclude(word)) {
				result.add(WordUtils.matchCase(prefix, word))
				if (result.size == limit) break
			}
		}
		return result
	}

	companion object {
		/** @param text One word per line, most common first. */
		fun parse(text: String): BaseDictionary =
			BaseDictionary(text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList())
	}
}

/**
 * Built in guesses for what comes next, so that there is something to suggest before the user has typed
 * enough for [LearnedWords] to know. Each line is a word, then the words that most often follow it, most
 * likely first, all separated by tabs. The word `^` stands for the start of a sentence, and `*` for any word
 * that has no line of its own. Lines starting with `#` are comments.
 */
class NextWordHints(
	private val next: Map<String, List<String>>,
	private val starters: List<String>,
	private val generic: List<String>
) {
	/** @return The words that most often come after [prev]. */
	fun after(prev: String, limit: Int): List<String> = next[prev.lowercase()].orEmpty().take(limit)

	/** @return The words that most often start a sentence. */
	fun starters(limit: Int): List<String> = starters.take(limit)

	/** @return Words that often follow any word. */
	fun generic(limit: Int): List<String> = generic.take(limit)

	companion object {
		fun parse(text: String): NextWordHints {
			val next = HashMap<String, List<String>>()
			var starters = emptyList<String>()
			var generic = emptyList<String>()
			for (line in text.lineSequence()) {
				if (line.isBlank() || line.startsWith("#")) continue
				val parts = line.split('\t').map { it.trim() }.filter { it.isNotEmpty() }
				if (parts.size < 2) continue
				val words = parts.drop(1)
				when (parts[0]) {
					"^" -> starters = words
					"*" -> generic = words
					else -> next[parts[0].lowercase()] = words
				}
			}
			return NextWordHints(next, starters, generic)
		}
	}
}

/** What the system spell checker said about a word. */
data class SpellResult(val isTypo: Boolean, val corrections: List<String>)

/**
 * @param leadingSpace For a [Kind.NEXT_WORD] that is offered while a word is being typed: it goes after a space.
 */
data class Suggestion(val text: String, val kind: Kind, val leadingSpace: Boolean = false) {
	enum class Kind {
		/** The word as typed, offered so that it can be added to the learned words. */
		TYPED,
		CORRECTION,
		COMPLETION,
		/** A prediction of the next word, after a space. */
		NEXT_WORD
	}
}

object SuggestionBuilder {
	const val SLOTS = 3

	/**
	 * Work out what to offer for the word being typed.
	 *
	 * For a word that looks like a typo this is the word itself, followed by completions (from the words
	 * the user has used, then from [base]), then corrections. Otherwise it is completions, and if there are
	 * not enough of those, words that could come next after this one.
	 *
	 * @param spell The spell checker's opinion, or null if it has none (yet).
	 * @param learned The words the user has used, or null if there are none.
	 * @param offerTyped Whether to offer the typed word for a typo, so that it can be learned.
	 * @param base Common words to complete with, or null to use only what the user has typed.
	 * @param hints Built in guesses at the next word, to fill the slots that are left over.
	 * @param before The word before [word], which makes what follows it easier to guess.
	 * @param personal The words the user added by hand, which are never typos.
	 */
	fun build(
		word: String,
		spell: SpellResult?,
		learned: LearnedWords?,
		slots: Int = SLOTS,
		offerTyped: Boolean = true,
		base: BaseDictionary? = null,
		hints: NextWordHints? = null,
		before: String = "",
		personal: PersonalDictionary? = null
	): List<Suggestion> {
		if (word.length < WordUtils.MIN_WORD_LENGTH) return emptyList()

		val mine = personal?.contains(word) == true
		val typo = spell?.isTypo == true && learned?.isKnown(word) != true && !mine
		// Only a word that is known to be one can be followed by something, not one that is half typed.
		val valid = (spell != null && !spell.isTypo) || learned?.isKnown(word) == true || base?.contains(word) == true || mine
		val result = ArrayList<Suggestion>(slots)
		val seen = HashSet<String>()
		seen.add(word.lowercase())

		fun add(text: String, kind: Suggestion.Kind, leadingSpace: Boolean = false) {
			if (result.size < slots && seen.add(text.lowercase())) {
				result.add(Suggestion(text, kind, leadingSpace))
			}
		}

		if (typo && offerTyped) result.add(Suggestion(word, Suggestion.Kind.TYPED))
		// The spell checker sees every unfinished word as a typo, so the user's own words go first.
		learned?.completions(word, slots)?.forEach { add(it, Suggestion.Kind.COMPLETION) }
		personal?.completions(word, slots)?.forEach { add(it, Suggestion.Kind.COMPLETION) }
		base?.completions(word, slots) { seen.contains(it) }?.forEach { add(it, Suggestion.Kind.COMPLETION) }
		if (typo) {
			spell?.corrections?.forEach { add(WordUtils.matchCase(word, it), Suggestion.Kind.CORRECTION) }
		} else if (result.size < slots && valid) {
			// A finished word with nothing to complete: offer what could follow it instead of leaving gaps.
			nextWords(word, learned, hints, slots, before).forEach { add(it, Suggestion.Kind.NEXT_WORD, leadingSpace = true) }
		}
		return result
	}

	/**
	 * Predict the next word: what the user typed after [prev] before, then the built in guesses for it,
	 * then words that often follow any word, so that all the slots are filled when there are hints.
	 * @param prev The word before the cursor, which is followed by a space, or empty if there is none.
	 * @param before The word before [prev], which makes what follows easier to guess.
	 */
	fun buildNext(
		prev: String,
		learned: LearnedWords?,
		slots: Int = SLOTS,
		hints: NextWordHints? = null,
		before: String = ""
	): List<Suggestion> {
		return nextWords(prev, learned, hints, slots, before).map { Suggestion(it, Suggestion.Kind.NEXT_WORD) }
	}

	/**
	 * What to offer at the start of a sentence, or in an empty field: the words the user starts sentences
	 * with, then the built in ones. They are capitalized, as they start a sentence.
	 */
	fun buildStart(learned: LearnedWords?, slots: Int = SLOTS, hints: NextWordHints? = null): List<Suggestion> {
		val result = ArrayList<Suggestion>(slots)
		val seen = HashSet<String>()
		val words = learned?.nextWords(LearnedWords.START, slots).orEmpty() + hints?.starters(slots * 2).orEmpty()
		for (word in words) {
			val text = word.replaceFirstChar { it.uppercase() }
			if (result.size < slots && seen.add(text.lowercase())) {
				result.add(Suggestion(text, Suggestion.Kind.NEXT_WORD))
			}
		}
		return result
	}

	private fun nextWords(
		prev: String,
		learned: LearnedWords?,
		hints: NextWordHints?,
		slots: Int,
		before: String = ""
	): List<String> {
		val words = ArrayList<String>(slots)
		val seen = HashSet<String>()
		fun addAll(candidates: List<String>) {
			for (word in candidates) {
				if (words.size < slots && seen.add(word.lowercase())) words.add(word)
			}
		}
		if (prev.isNotEmpty()) {
			addAll(learned?.nextWords(prev, slots, before).orEmpty())
			addAll(hints?.after(prev, slots).orEmpty())
		}
		addAll(hints?.generic(slots).orEmpty())
		return words
	}
}

/** How readily a misspelled word is fixed without asking. */
enum class AutoCorrectLevel(val preferenceValue: String, val maxDistance: Int) {
	OFF("off", 0),
	/** Only a single slip: a letter too many, too few, wrong, or two swapped. */
	LOW("low", 1),
	MEDIUM("medium", 2),
	/** Whatever the spell checker's best guess is, however far off. */
	HIGH("high", 3);

	companion object {
		fun fromPreference(value: String?): AutoCorrectLevel =
			values().firstOrNull { it.preferenceValue == value } ?: MEDIUM
	}
}

/**
 * Decides whether a word that the spell checker flagged is fixed automatically, and to what.
 */
object AutoCorrect {
	/** Shorter words are never corrected, there is too little to go on. */
	const val MIN_WORD_LENGTH = 3

	/**
	 * The letters of a word, for ruling out most words that are far from it without measuring them. Words
	 * that are within a few letter changes share nearly all their letters, so a word with too many letters that
	 * the other one lacks can't be close.
	 */
	class LetterBag(word: String) {
		private val counts = IntArray(BAG_SIZE)
		private val scratch = IntArray(BAG_SIZE)

		init {
			for (c in word) counts[if (c.code < BAG_SIZE) c.code else 0]++
		}

		/** @return false if [other] can't be within [maxDistance] letter changes of the word, true if it might. */
		fun mayBeWithin(other: String, maxDistance: Int): Boolean {
			System.arraycopy(counts, 0, scratch, 0, BAG_SIZE)
			var missing = 0
			for (c in other) {
				val index = if (c.code < BAG_SIZE) c.code else 0
				if (scratch[index] > 0) {
					scratch[index]--
				} else if (++missing > maxDistance) {
					return false
				}
			}
			return true
		}

		private companion object {
			const val BAG_SIZE = 128
		}
	}

	/**
	 * The number of single letter changes it takes to turn [a] into [b]: adding, removing or changing a
	 * letter, or swapping two neighbouring letters, which each count as one.
	 */
	fun editDistance(a: String, b: String): Int {
		val d = Array(a.length + 1) { IntArray(b.length + 1) }
		for (i in 0..a.length) d[i][0] = i
		for (j in 0..b.length) d[0][j] = j
		for (i in 1..a.length) {
			for (j in 1..b.length) {
				val cost = if (a[i - 1] == b[j - 1]) 0 else 1
				var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
				if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
					v = minOf(v, d[i - 2][j - 2] + 1)
				}
				d[i][j] = v
			}
		}
		return d[a.length][b.length]
	}

	/**
	 * A stand-in for the system spell checker, for when there isn't one turned on: a word that is not one of the
	 * common words and that the user has not used, but is close to one of them, is taken for a typo. Anything
	 * else is left alone, since a list of common words can't tell a rare word from a typo.
	 * @param wasUsed Whether the user has used the word before.
	 * @param maxDistance How many letter changes away a common word may be and still count as a match, so
	 * that more typos are caught at the bolder auto-correct levels, see [AutoCorrectLevel.maxDistance].
	 */
	fun builtInSpell(word: String, dictionary: BaseDictionary, wasUsed: Boolean, maxDistance: Int = 1): SpellResult {
		if (word.length < MIN_WORD_LENGTH || wasUsed || dictionary.contains(word)) return SpellResult(false, emptyList())
		val near = dictionary.nearest(word, maxDistance, 3).map { WordUtils.matchCase(word, it) }
		return SpellResult(near.isNotEmpty(), near)
	}

	// Slang, short forms and interjections that a spell checker may not know but that are not typos.
	private val neverFixWords = setOf(
		"lol", "lmao", "lmfao", "omg", "idk", "imo", "imho", "btw", "tbh", "smh", "brb", "ttyl", "afk", "irl", "fyi", "thx",
		"pls", "plz", "np", "ty", "yw", "wtf", "ily", "ikr", "jk", "nvm", "ok", "hmm", "hmmm", "umm", "uhh", "ugh", "yay",
		"aww", "awww", "nah", "bro", "bruh", "dude", "yea", "yeah", "yep", "yup", "nope", "gonna", "wanna", "gotta",
		"kinda", "sorta", "dunno", "lemme", "gimme", "cuz", "tho", "thru", "ya", "yall", "ayo", "sus", "bae", "fam", "lit",
		"wow", "woah", "whoa", "oof", "yikes", "cya", "gn", "gm", "asap", "diy", "eta", "aka", "tbd", "ftw", "fomo",
		"hbu", "wbu", "rn", "ig", "ngl", "imy", "omw", "otw", "wyd", "wya", "hmu", "tmi", "tldr", "lmk", "bff", "bf", "gf",
		"ai", "tv", "ok", "okay", "vs", "etc", "eg", "ie", "pm", "am", "dm", "dms", "pic", "pics", "app", "apps"
	)

	/**
	 * @return true if [word] is not to be touched whatever the spell checker says: laughter, slang, and words
	 * stretched on purpose ("sooo", "yesss").
	 */
	fun neverFix(word: String): Boolean {
		if (Spacing.isLaughter(word) || word.lowercase() in neverFixWords) return true
		var run = 1
		for (i in 1 until word.length) {
			run = if (word[i].lowercaseChar() == word[i - 1].lowercaseChar()) run + 1 else 1
			if (run >= 3) return true
		}
		return false
	}

	/**
	 * @return true if [word], a finished word that the spell checker flagged as a typo, may be fixed by itself.
	 * @param atSentenceStart Whether [word] starts a sentence, where a capital is expected of any word.
	 * @param isKnown Whether the user has used the word enough to trust it.
	 * @param isCommon Whether the word is a common word, which the spell checker must have missed.
	 */
	fun eligible(word: String, level: AutoCorrectLevel, atSentenceStart: Boolean, isKnown: Boolean, isCommon: Boolean): Boolean {
		if (level == AutoCorrectLevel.OFF || word.length < MIN_WORD_LENGTH || isKnown || isCommon) return false
		// ALL CAPS and camelCase are on purpose. A capital in the middle of a sentence is probably a name.
		if (word.drop(1).any { it.isUpperCase() }) return false
		if (word[0].isUpperCase() && !atSentenceStart && level != AutoCorrectLevel.HIGH) return false
		return !neverFix(word)
	}

	/** @return How many letter changes away from [word] a correction may be. Short words leave less room to guess. */
	fun limit(word: String, level: AutoCorrectLevel): Int =
		minOf(level.maxDistance, maxOf(1, (word.length - 1) / 2))

	/**
	 * @param word A finished word that the spell checker flagged as a typo.
	 * @param corrections What the spell checker suggested, best first.
	 * @param atSentenceStart Whether [word] starts a sentence, where a capital is expected of any word.
	 * @param isKnown Whether the user has used the word enough to trust it.
	 * @param isCommon Whether the word is a common word, which the spell checker must have missed.
	 * @return What to change [word] into, or null to leave it alone.
	 */
	fun choose(
		word: String,
		corrections: List<String>,
		level: AutoCorrectLevel,
		atSentenceStart: Boolean,
		isKnown: Boolean,
		isCommon: Boolean
	): String? {
		if (!eligible(word, level, atSentenceStart, isKnown, isCommon)) return null

		val lower = word.lowercase()
		val limit = limit(word, level)
		var best: String? = null
		var bestDistance = Int.MAX_VALUE
		for (candidate in corrections.take(3)) {
			val other = candidate.lowercase()
			if (candidate.isEmpty() || candidate.any { it.isWhitespace() } || other == lower) continue
			val distance = editDistance(lower, other)
			if (distance > limit) continue
			// Mistyping the first letter is rarer than the others, so it takes a bolder level to fix.
			val sameStart = other[0] == lower[0]
			val allowed = level == AutoCorrectLevel.HIGH || sameStart ||
				(level == AutoCorrectLevel.MEDIUM && distance <= 1)
			if (allowed && distance < bestDistance) {
				best = candidate
				bestDistance = distance
			}
		}
		return best?.let { WordUtils.matchCase(word, it) }
	}
}
