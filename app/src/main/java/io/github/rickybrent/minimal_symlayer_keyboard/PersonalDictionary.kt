package io.github.rickybrent.minimal_symlayer_keyboard

/**
 * What the user typed into the settings to teach the keyboard: shortcuts that turn into text, and words that
 * are right however they are spelled. One per line:
 *
 *     omw = on my way
 *     Kubernetes
 *
 * A shortcut is a single word. A line with no equals sign holds words (separated by spaces or commas) that
 * are never fixed and are suggested like the words the user has used. Lines starting with `#` are notes.
 */
class PersonalDictionary private constructor(
	private val shortcuts: Map<String, String>,
	private val words: List<String>
) {
	private val wordSet = words.map { it.lowercase() }.toHashSet()

	val isEmpty: Boolean get() = shortcuts.isEmpty() && words.isEmpty()

	/** @return The text that [word] stands for, or null if it is not a shortcut. */
	fun expansion(word: String): String? = shortcuts[word.lowercase()]

	/** @return true if [word] is one of the words, or a shortcut. Capitalization does not matter. */
	fun contains(word: String): Boolean {
		val lower = word.lowercase()
		return wordSet.contains(lower) || shortcuts.containsKey(lower)
	}

	/** @return The words that start with [prefix] (but are longer), capitalized to match [prefix]. */
	fun completions(prefix: String, limit: Int): List<String> {
		if (prefix.length < WordUtils.MIN_WORD_LENGTH || limit <= 0) return emptyList()
		val lower = prefix.lowercase()
		return words
			.filter { it.length > lower.length && it.lowercase().startsWith(lower) }
			.take(limit)
			.map { WordUtils.matchCase(prefix, it) }
	}

	companion object {
		val EMPTY = PersonalDictionary(emptyMap(), emptyList())

		fun parse(text: String?): PersonalDictionary {
			if (text.isNullOrBlank()) return EMPTY
			val shortcuts = LinkedHashMap<String, String>()
			val words = ArrayList<String>()
			for (raw in text.lineSequence()) {
				val line = raw.trim()
				if (line.isEmpty() || line.startsWith("#")) continue
				val equals = line.indexOf('=')
				if (equals >= 0) {
					val key = line.substring(0, equals).trim().lowercase()
					val value = line.substring(equals + 1).trim()
					if (key.isNotEmpty() && value.isNotEmpty() && key.all { WordUtils.isWordChar(it) || it.isDigit() }) {
						shortcuts[key] = value
					}
				} else {
					line.split(' ', ',', ';', '\t')
						.map { it.trim() }
						.filter { it.length >= WordUtils.MIN_WORD_LENGTH && it.all { c -> WordUtils.isWordChar(c) } }
						.forEach { if (words.none { w -> w.equals(it, ignoreCase = true) }) words.add(it) }
				}
			}
			return if (shortcuts.isEmpty() && words.isEmpty()) EMPTY else PersonalDictionary(shortcuts, words)
		}
	}
}
