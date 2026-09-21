package io.github.rickybrent.minimal_symlayer_keyboard

/**
 * Helpers for showing only the emoji that match a preferred skin tone.
 *
 * The emoji data lists every skin tone variant as its own entry, so without filtering the
 * People & Body category is several times longer than it needs to be.
 */
object SkinTone {
    /** Preference value that shows every variant. */
    const val ALL = "all"

    /** Preference value that shows only emoji without a skin tone modifier. */
    const val DEFAULT = "default"

    private const val TONE_RANGE_START = 0x1F3FB
    private const val TONE_RANGE_END = 0x1F3FF
    private const val VARIATION_SELECTOR_16 = 0xFE0F

    private val toneCodePoints = mapOf(
        "light" to 0x1F3FB,
        "medium-light" to 0x1F3FC,
        "medium" to 0x1F3FD,
        "medium-dark" to 0x1F3FE,
        "dark" to 0x1F3FF
    )

    private fun isTone(codePoint: Int) = codePoint in TONE_RANGE_START..TONE_RANGE_END

    private inline fun forEachCodePoint(text: String, action: (Int) -> Unit) {
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            action(codePoint)
            i += Character.charCount(codePoint)
        }
    }

    /** @return The distinct skin tone modifiers used in [character]. */
    fun tonesIn(character: String): Set<Int> {
        val tones = mutableSetOf<Int>()
        forEachCodePoint(character) { if (isTone(it)) tones.add(it) }
        return tones
    }

    /**
     * @return [character] without skin tone modifiers or variation selectors, so that a variant
     * has the same key as the emoji it is a variant of.
     */
    private fun baseKey(character: String): String {
        val key = StringBuilder()
        forEachCodePoint(character) {
            if (!isTone(it) && it != VARIATION_SELECTOR_16) key.appendCodePoint(it)
        }
        return key.toString()
    }

    /**
     * @param preference One of [ALL], [DEFAULT], or a tone name ("light", "medium-light", "medium",
     * "medium-dark", "dark"). Unknown values are treated like [DEFAULT].
     * @return The emoji to show for the preference, in their original order.
     * With a tone selected, each emoji that has a variant in that tone is replaced by the variant.
     * Emoji that only exist with mixed tones (e.g. a handshake between two tones) are only shown by [ALL].
     */
    fun filter(emojis: List<Emoji>, preference: String): List<Emoji> {
        if (preference == ALL) return emojis

        val target = toneCodePoints[preference]
        if (target == null) {
            return emojis.filter { !isVariant(it) }
        }

        val onlyTarget = setOf(target)
        val variantKeys = emojis
            .filter { tonesIn(it.character) == onlyTarget }
            .map { baseKey(it.character) }
            .filter { it.isNotEmpty() }
            .toHashSet()

        return emojis.filter { emoji ->
            val tones = tonesIn(emoji.character)
            when {
                // The bare modifiers in the Component category are always useful.
                tones.isNotEmpty() && baseKey(emoji.character).isEmpty() -> true
                tones.isEmpty() -> baseKey(emoji.character) !in variantKeys
                else -> tones == onlyTarget
            }
        }
    }

    /** @return true if [emoji] is a skin tone variant of another emoji. */
    private fun isVariant(emoji: Emoji): Boolean {
        return tonesIn(emoji.character).isNotEmpty() && baseKey(emoji.character).isNotEmpty()
    }
}
