package io.github.rickybrent.minimal_symlayer_keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView

class ClipboardHistoryAdapter(
    private val context: Context,
    private val onItemSelected: (String) -> Unit
) : RecyclerView.Adapter<ClipboardHistoryAdapter.ClipboardViewHolder>(), Filterable {

    private val model = ClipboardHistoryModel()
    private var filteredHistory: List<Clipping> = listOf()
    private var query: CharSequence? = null
    private var pinnedLoaded = false
    private var skipSensitive = true
    private var clipboardManager: ClipboardManager? = null

    /** Called whenever the history is added to, removed from, or otherwise changed. */
    var onHistoryChanged: (() -> Unit)? = null

    private var _prefs: SharedPreferences? = null
    private val prefs: SharedPreferences?
        get() {
            if (_prefs == null) {
                _prefs = ContextCompat.getSystemService(context, UserManager::class.java)
                    ?.takeIf { it.isUserUnlocked }
                    ?.let { context.getSharedPreferences("clipboard_history", Context.MODE_PRIVATE) }
            }
            return _prefs
        }

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        // Only the focused app and the current keyboard may read the clipboard.
        val clip = try {
            clipboardManager?.primaryClip
        } catch (e: SecurityException) {
            null
        }
        if (clip != null && clip.itemCount > 0) {
            if (skipSensitive && clip.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE, false) == true) {
                return@OnPrimaryClipChangedListener
            }
            val text = clip.getItemAt(0).text?.toString()
            if (!text.isNullOrEmpty() && model.add(text)) {
                updateFilteredHistory()
            }
        }
    }

    init {
        initialize()
        loadPinnedClippings()
        updateFilteredHistory()
    }

    private fun initialize() {
        if (clipboardManager == null) {
            clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
        }
    }

    /**
     * Load the pinned clippings once storage is available. Before the first unlock (direct boot)
     * storage is not, so this is retried until it works.
     */
    private fun loadPinnedClippings() {
        if (pinnedLoaded) return
        val p = prefs ?: return
        p.getStringSet(PREF_PINNED_CLIPPINGS, emptySet())?.forEach { model.addPinned(it) }
        pinnedLoaded = true
    }

    private fun savePinnedClippings() {
        // Never overwrite what is stored with an empty set before it has been read.
        if (!pinnedLoaded) return
        prefs?.edit { putStringSet(PREF_PINNED_CLIPPINGS, model.pinnedTexts()) }
    }

    /**
     * Apply the clipboard settings.
     * @param maxUnpinned The number of unpinned clippings to keep.
     * @param expireMillis Unpinned clippings older than this are dropped, or 0 to keep them.
     * @param skipSensitive Don't record clippings that the copying app marked as sensitive.
     */
    fun applySettings(maxUnpinned: Int, expireMillis: Long, skipSensitive: Boolean) {
        if (model.maxUnpinned == maxUnpinned && model.expireMillis == expireMillis && this.skipSensitive == skipSensitive) {
            return
        }
        model.maxUnpinned = maxUnpinned
        model.expireMillis = expireMillis
        this.skipSensitive = skipSensitive
        model.trim()
        updateFilteredHistory()
    }

    fun getHistory(): List<Clipping> {
        return model.items()
    }

    private fun togglePin(item: Clipping) {
        model.togglePin(item)
        savePinnedClippings()
        updateFilteredHistory()
    }

    private fun removeItem(item: Clipping) {
        model.remove(item)
        savePinnedClippings()
        updateFilteredHistory()
    }

    /** Remove every clipping that is not pinned. */
    fun clearUnpinned() {
        if (model.clearUnpinned()) {
            updateFilteredHistory()
        }
    }

    fun paste(text: String) {
        val clip = ClipData.newPlainText("pasted text", text)
        clipboardManager?.setPrimaryClip(clip)
    }

    fun refresh() {
        loadPinnedClippings()
        updateFilteredHistory()
    }

    private fun updateFilteredHistory() {
        filteredHistory = model.search(query)
        notifyDataSetChanged()
        onHistoryChanged?.invoke()
    }

    class ClipboardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.clipboard_text)
        val pinIcon: ImageView = view.findViewById(R.id.pin_icon)
        val clearIcon: ImageView = view.findViewById(R.id.clear_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipboardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.picker_item_clipping, parent, false)
        return ClipboardViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClipboardViewHolder, position: Int) {
        val item = filteredHistory[position]
        holder.textView.text = item.text
        holder.pinIcon.setImageResource(
            if (item.isPinned) R.drawable.ic_pin_filled else R.drawable.ic_pin_outline
        )

        holder.itemView.setOnClickListener {
            onItemSelected(item.text)
        }

        holder.pinIcon.setOnClickListener {
            togglePin(item)
        }

        holder.clearIcon.setOnClickListener {
            removeItem(item)
        }
    }

    override fun getItemCount(): Int = filteredHistory.size

    fun selectFirstItem() {
        if (filteredHistory.isNotEmpty()) {
            onItemSelected(filteredHistory[0].text)
        }
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                results.values = model.search(constraint)
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                // Remember the query so that later changes to the history keep the filter applied.
                query = constraint
                filteredHistory = results?.values as? List<Clipping> ?: emptyList()
                notifyDataSetChanged()
                onHistoryChanged?.invoke()
            }
        }
    }

    companion object {
        private const val PREF_PINNED_CLIPPINGS = "pinned_clippings"

        // ClipDescription.EXTRA_IS_SENSITIVE, which is only defined from Android 13.
        private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}
