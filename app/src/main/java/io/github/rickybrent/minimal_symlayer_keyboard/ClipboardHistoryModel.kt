package io.github.rickybrent.minimal_symlayer_keyboard

/**
 * The clipboard history, without any Android dependencies. It is used from the main thread and from
 * the thread that filters the history, so every method is synchronized.
 *
 * Pinned clippings are always kept and listed first. The remaining clippings are listed newest first,
 * and are limited by [maxUnpinned] and (optionally) [expireMillis].
 */
class ClipboardHistoryModel(
    var maxUnpinned: Int = DEFAULT_MAX_UNPINNED,
    var expireMillis: Long = 0L,
    private val now: () -> Long = System::currentTimeMillis
) {
    // Newest first, pinned or not.
    private val history = mutableListOf<Clipping>()

    /** @return The clippings to display: pinned first, then newest first. */
    @Synchronized
    fun items(): List<Clipping> {
        expire()
        return history.sortedByDescending { it.isPinned }
    }

    /**
     * Record newly copied text. Copying text that is already in the history moves it to the front.
     * @return true if the history changed.
     */
    @Synchronized
    fun add(text: String): Boolean {
        if (text.isEmpty()) return false
        val existing = history.indexOfFirst { it.text == text }
        if (existing == 0) {
            history[0].timestamp = now()
            return false
        }
        val clipping = if (existing > 0) history.removeAt(existing) else Clipping(text, timestamp = now())
        clipping.timestamp = now()
        history.add(0, clipping)
        trim()
        return true
    }

    /** Restore a pinned clipping, e.g. from storage. */
    @Synchronized
    fun addPinned(text: String) {
        val existing = history.firstOrNull { it.text == text }
        if (existing != null) {
            existing.isPinned = true
        } else if (text.isNotEmpty()) {
            history.add(Clipping(text, isPinned = true, timestamp = now()))
        }
    }

    @Synchronized
    fun togglePin(item: Clipping) {
        item.isPinned = !item.isPinned
        trim()
    }

    @Synchronized
    fun remove(item: Clipping) {
        history.remove(item)
    }

    /** Remove everything that is not pinned. @return true if anything was removed. */
    @Synchronized
    fun clearUnpinned(): Boolean = history.removeAll { !it.isPinned }

    @Synchronized
    fun pinnedTexts(): Set<String> = history.filter { it.isPinned }.map { it.text }.toSet()

    /** @return The clippings containing [query], ignoring case. */
    @Synchronized
    fun search(query: CharSequence?): List<Clipping> {
        val all = items()
        if (query.isNullOrEmpty()) return all
        val needle = query.toString().lowercase()
        return all.filter { it.text.lowercase().contains(needle) }
    }

    /** Apply a changed [maxUnpinned]. */
    @Synchronized
    fun trim() {
        var unpinned = history.count { !it.isPinned }
        while (unpinned > maxUnpinned) {
            val last = history.indexOfLast { !it.isPinned }
            if (last < 0) break
            history.removeAt(last)
            unpinned--
        }
    }

    /** Remove unpinned clippings that are older than [expireMillis]. @return true if anything was removed. */
    @Synchronized
    fun expire(): Boolean {
        if (expireMillis <= 0) return false
        val cutoff = now() - expireMillis
        return history.removeAll { !it.isPinned && it.timestamp < cutoff }
    }

    companion object {
        const val DEFAULT_MAX_UNPINNED = 50
    }
}
