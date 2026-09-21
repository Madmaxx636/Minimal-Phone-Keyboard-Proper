package io.github.rickybrent.minimal_symlayer_keyboard

data class Emoji(
    val character: String,
    val category: String,
    val subCategory: String,
    val name: String,
    val tags: List<String>
)

object EmojiData {
    private const val VARIATION_SELECTOR_16 = "️"

    /**
     * @return An identifier for [character] that is the same whether or not it has the variation
     * selector (U+FE0F) that asks for it to be shown as an emoji.
     */
    fun key(character: String): String = character.replace(VARIATION_SELECTOR_16, "")

    /**
     * The data lists many emoji twice: with the variation selector, and without it. This keeps the
     * first of each, in the form with the most selectors, as that is the one that reliably shows as an emoji.
     */
    fun deduplicate(emojis: List<Emoji>): List<Emoji> {
        fun selectors(emoji: Emoji) = emoji.character.length - key(emoji.character).length

        val best = HashMap<String, Emoji>()
        for (emoji in emojis) {
            val key = key(emoji.character)
            val current = best[key]
            if (current == null || selectors(emoji) > selectors(current)) {
                best[key] = emoji
            }
        }
        val seen = HashSet<String>()
        return emojis.mapNotNull { emoji ->
            val key = key(emoji.character)
            if (seen.add(key)) best.getValue(key) else null
        }
    }
}
