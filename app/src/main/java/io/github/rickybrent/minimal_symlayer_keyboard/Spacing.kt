package io.github.rickybrent.minimal_symlayer_keyboard

/**
 * Puts in the spaces that were left out: after punctuation that is jammed between two words ("hello.world"),
 * and between words that ran together ("lovehahaha").
 */
object Spacing {
	/** Marks that end a word and are followed by a space, in the middle of a token, when a space is missing. */
	private const val SEPARATORS = ".,!?;"

	/** Characters that may open a token: "(hello.world" is still "hello.world". */
	private const val OPENERS = "\"'([{“‘"

	// A run of words joined with full stops that ends in one of these is a web address or a file name, not two
	// sentences. Endings that are also everyday words (in, is, it, no, so, to) are left out: a missed space
	// costs little, a space inside an address ruins it.
	private val addressEndings = setOf(
		"com", "org", "net", "edu", "gov", "mil", "int", "io", "co", "uk", "us", "ca", "de", "fr", "au", "me", "tv", "ai",
		"app", "dev", "info", "biz", "xyz", "ly", "gg", "fm", "ru", "jp", "cn", "nz", "za", "br", "es", "nl", "se", "ch",
		"eu", "cc", "sh", "ws", "cloud", "online", "site", "store", "tech", "blog", "page", "link", "news", "shop",
		"txt", "pdf", "png", "jpg", "jpeg", "gif", "svg", "webp", "mp3", "mp4", "wav", "mov", "mkv", "doc", "docx", "xls",
		"xlsx", "ppt", "pptx", "zip", "gz", "tar", "rar", "apk", "kt", "java", "md", "json", "xml", "html", "htm", "css",
		"js", "ts", "py", "sh", "cpp", "exe", "csv", "log", "bak", "ini", "cfg", "yml", "yaml", "toml", "rb", "php",
		"sql", "db", "bin", "iso", "jar", "epub", "odt", "ods", "rtf", "tmp", "lock", "gradle", "kts", "swift", "rs", "go"
	)

	/**
	 * Add the missing spaces after the punctuation in the last word-like token of [text], which is where a
	 * sentence was run into the next one: "hello.world" and "yes,please" become "hello. world" and "yes, please".
	 * Web addresses, file names, e-mail addresses, numbers, abbreviations ("e.g", "U.S.A") and marks that are not
	 * between two words ("wait...") are left as they are.
	 *
	 * @param text What was typed, ending with the word that was just finished (not with the character after it).
	 * @param capitalize Whether the word after a full stop, question mark or exclamation mark gets a capital.
	 * @return [text] with the spaces, or null if there was nothing to do.
	 */
	fun afterPunctuation(text: String, capitalize: Boolean = false): String? {
		var tokenStart = text.length
		while (tokenStart > 0 && !text[tokenStart - 1].isWhitespace()) tokenStart--
		var bodyStart = tokenStart
		while (bodyStart < text.length && text[bodyStart] in OPENERS) bodyStart++
		val body = text.substring(bodyStart)
		if (body.length < 3 || body.none { it in SEPARATORS }) return null
		if (body.any { !it.isLetter() && !isApostrophe(it) && it !in SEPARATORS }) return null

		val segments = ArrayList<String>()
		val separators = ArrayList<Char>()
		var start = 0
		for (i in body.indices) {
			if (body[i] in SEPARATORS) {
				segments.add(body.substring(start, i))
				separators.add(body[i])
				start = i + 1
			}
		}
		segments.add(body.substring(start))
		// Every mark has to be between two words, so "wait..." and "what?!" are left alone.
		if (segments.any { it.isEmpty() || !it.first().isLetter() || !it.last().isLetter() }) return null

		// Words joined only with full stops make a web address or an abbreviation, when they end the right way.
		val keepDot = BooleanArray(separators.size)
		var i = 0
		while (i < separators.size) {
			if (separators[i] != '.') {
				i++
				continue
			}
			var end = i
			while (end < separators.size && separators[end] == '.') end++
			val chain = segments.subList(i, end + 1)
			val address = chain.last().lowercase() in addressEndings || chain.first().equals("www", ignoreCase = true)
			val abbreviation = chain.any { it.length < 2 }
			if (address || abbreviation) for (k in i until end) keepDot[k] = true
			i = end
		}

		val result = StringBuilder(text.substring(0, bodyStart))
		var capitalizeNext = false
		var changed = false
		for (k in segments.indices) {
			var segment = segments[k]
			if (capitalizeNext && segment.first().isLowerCase()) segment = segment.replaceFirstChar { it.uppercase() }
			result.append(segment)
			if (k < separators.size) {
				result.append(separators[k])
				if (keepDot[k]) {
					capitalizeNext = false
				} else {
					result.append(' ')
					changed = true
					capitalizeNext = capitalize && separators[k] in ".!?"
				}
			}
		}
		return if (changed) result.toString() else null
	}

	private fun isApostrophe(c: Char) = c == '\'' || c == '’'

	// --- Words that ran together ---

	// Laughter and shouts that are not in any word list. Whole words only.
	private val laughter = Regex("a?(?:ha){2,}h?|(?:he){2,}h?|l+o+l+(?:o+l+)*|lmf?a+o+|rofl(?:mao)?")

	/** @return true if [word] is laughter such as "haha", "hehe", "lol" or "lmao", which is never a typo. */
	fun isLaughter(word: String): Boolean = laughter.matches(word.lowercase())

	// Small words that can start a run-together phrase ("ihave", "tothe", "isthe") or sit in the middle of one.
	private val shortStarts = setOf(
		"a", "i", "to", "of", "in", "on", "at", "is", "it", "my", "me", "we", "he", "so", "do", "go", "no", "up", "us", "be",
		"by", "or", "as", "an", "if", "am"
	)

	// Small words that can end one ("lotsof", "thankyou" ends in a long word). Others are too often the end of
	// a name, as in "Brandon".
	private val shortEnds = setOf("of", "to", "it", "me", "up", "us")

	// Words that are made of two words on purpose, and names that are.
	private val neverSplit = setOf(
		"facebook", "youtube", "paypal", "snapchat", "minecraft", "playstation", "microsoft", "hotmail", "whatsapp",
		"linkedin", "github", "gmail", "iphone", "ipad", "ipod", "icloud", "itunes", "imessage", "netflix", "airbnb",
		"wikipedia", "bitcoin", "blockchain", "smartphone", "podcast", "webcam", "tiktok", "telegram", "keyboard"
	)

	private const val PIECE_PENALTY = 3.0
	private const val LAUGHTER_SCORE = -6.0
	private const val MIN_SPLIT_LENGTH = 5
	private const val MAX_SPLIT_LENGTH = 32

	/**
	 * Split a word that is two or three words with no space between them, like "lovehahaha" or "goodmorning".
	 * Only a word that is not a word itself is split, and only into words (or laughter) that are all common ones,
	 * so a typo or a name that happens to be made of two words is more likely left alone than split.
	 *
	 * @param word A finished word that is not known to be one.
	 * @param maxRank Pieces that are rarer than this (see [rankOf]) don't count as words, so that a caller can ask
	 * for a split it is sure of.
	 * @param rankOf How common a word is: 0 for the most common, larger for rarer, and -1 if it is not a word.
	 * @return The pieces, the first capitalized like [word], or null if it can't be split.
	 */
	fun split(word: String, maxRank: Int = Int.MAX_VALUE, rankOf: (String) -> Int): List<String>? {
		val lower = word.lowercase()
		if (lower.length < MIN_SPLIT_LENGTH || lower.length > MAX_SPLIT_LENGTH || lower.any { !it.isLetter() }) return null
		// Shouting and camelCase are on purpose.
		if (word.length > 1 && word.all { it.isUpperCase() }) return null
		if (word.drop(1).any { it.isUpperCase() }) return null
		if (lower in neverSplit || isLaughter(lower) || rankOf(lower) >= 0) return null

		var best: List<String>? = null
		var bestScore = Double.NEGATIVE_INFINITY
		fun consider(pieces: List<String>) {
			var score = -PIECE_PENALTY * (pieces.size - 1)
			for ((index, piece) in pieces.withIndex()) {
				score += pieceScore(piece, index == 0, index == pieces.size - 1, maxRank, rankOf) ?: return
			}
			if (score > bestScore) {
				best = pieces
				bestScore = score
			}
		}
		for (i in 1 until lower.length) {
			val first = lower.substring(0, i)
			if (pieceScore(first, true, false, maxRank, rankOf) == null) continue
			consider(listOf(first, lower.substring(i)))
			for (j in i + 1 until lower.length) {
				consider(listOf(first, lower.substring(i, j), lower.substring(j)))
			}
		}
		val pieces = best ?: return null
		// A short first piece with nothing longer next to it, like "ofit", is more likely a slip than two words.
		if (pieces.none { it.length >= 3 }) return null
		return pieces.mapIndexed { index, piece ->
			when {
				piece == "i" -> "I"
				index == 0 -> WordUtils.matchCase(word, piece)
				else -> piece
			}
		}
	}

	/** @return How likely [piece] is as part of a run-together phrase (higher is likelier), or null if it can't be. */
	private fun pieceScore(piece: String, first: Boolean, last: Boolean, maxRank: Int, rankOf: (String) -> Int): Double? {
		if (piece.length >= 3 && isLaughter(piece)) return LAUGHTER_SCORE
		// The word lists have no one letter words.
		if (piece == "a" || piece == "i") return if (last) null else -Math.log(10.0)
		if (piece.length <= 2) {
			val allowed = if (last) piece in shortEnds else piece in shortStarts
			// A lone "a" or "i" can only start it, "isaword" is fine but "wasi" is not.
			if (!allowed || (piece.length == 1 && !first && piece != "a")) return null
		}
		val rank = rankOf(piece)
		if (rank < 0 || rank > maxRank) return null
		// Three letters is where the word lists start to hold odd tokens, so only take the common ones.
		if (piece.length == 3 && rank > MAX_RANK_FOR_THREE_LETTERS) return null
		return -Math.log(rank + 10.0)
	}

	private const val MAX_RANK_FOR_THREE_LETTERS = 12000
}
