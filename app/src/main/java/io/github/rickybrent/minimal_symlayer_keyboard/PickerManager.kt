package io.github.rickybrent.minimal_symlayer_keyboard

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PickerManager(private val context: Context, private val service: InputMethodService) {

    private var popupWindow: PopupWindow? = null
    private var emojiAdapter: EmojiAdapter? = null
    private var symbolAdapter: SymbolAdapter? = null
    private var clipboardAdapter: ClipboardHistoryAdapter? = null
    private var popupShownTime: Long = 0
    private var initialPressComplete = false
    private var activeTextWatcher: TextWatcher? = null
    private var currentView: ViewType = ViewType.EMOJI
    private var skinTone: String = SkinTone.DEFAULT

    private var inlineViewContainer: FrameLayout? = null
    private val pickerView: View
    private var characterMap: SymMapView? = null

    private lateinit var contentArea: FrameLayout
    private lateinit var titleArea: TextView
    private lateinit var searchBar: EditText
    private lateinit var emojiButton: ImageButton
    private lateinit var emojiCloseButton: ImageButton
    private lateinit var symButton: ImageButton
    private lateinit var symCloseButton: ImageButton
    private lateinit var clipboardButton: ImageButton
    private lateinit var clipboardClearButton: ImageButton
    private lateinit var emptyClipboardMessage: TextView
    private lateinit var recyclerView: RecyclerView

    enum class ViewType {
        EMOJI, SYMBOL, CLIPBOARD
    }

    init {
        pickerView = View.inflate(service, R.layout.picker_container, null)
        // Create the clipboard history right away so that it records what is copied from now on,
        // rather than only after the clipboard view has been opened for the first time.
        getClipboardAdapter()
    }

    /**
     * Apply the emoji and clipboard settings. Cheap to call repeatedly with the same values.
     */
    fun applySettings(
        skinTone: String,
        clipboardMaxUnpinned: Int,
        clipboardExpireMillis: Long,
        clipboardSkipSensitive: Boolean
    ) {
        if (this.skinTone != skinTone) {
            this.skinTone = skinTone
            emojiAdapter?.setSkinTone(skinTone)
        }
        getClipboardAdapter().applySettings(clipboardMaxUnpinned, clipboardExpireMillis, clipboardSkipSensitive)
    }

    fun setInlineViewContainer(container: FrameLayout?) {
        inlineViewContainer = container
        // Ensure pickerView is not attached to a different parent
        (pickerView.parent as? ViewGroup)?.removeView(pickerView)
        inlineViewContainer?.addView(pickerView)
        // The map of additional characters sits in the same place as the pickers, but only when there is no picker.
        characterMap?.let { (it.parent as? ViewGroup)?.removeView(it) }
        characterMap = SymMapView(context) { text -> service.currentInputConnection?.commitText(text, 1) }.also {
            it.visibility = View.GONE
            inlineViewContainer?.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /**
     * Show the map of symbols and additional characters, in the space of the pickers. Unlike a picker it does
     * not take the key presses: they go on as usual, and it is up to the caller to hide it again.
     * @return false if it can't be shown right now, because a picker is showing or there is nowhere to show it.
     */
    fun showCharacterMap(symbols: List<SymbolRow>, extras: List<CharacterRow>): Boolean {
        val container = inlineViewContainer ?: return false
        val map = characterMap ?: return false
        if (isShowing()) return false
        map.setContent(symbols, extras)
        map.visibility = View.VISIBLE
        pickerView.visibility = View.GONE
        container.layoutParams = container.layoutParams.also { it.height = ViewGroup.LayoutParams.WRAP_CONTENT }
        container.visibility = View.VISIBLE
        return true
    }

    /** Hide the map of additional characters, if it is showing. */
    fun hideCharacterMap() {
        if (!isCharacterMapShowing()) return
        characterMap?.visibility = View.GONE
        pickerView.visibility = View.VISIBLE
        inlineViewContainer?.visibility = View.GONE
    }

    fun isCharacterMapShowing(): Boolean = characterMap?.visibility == View.VISIBLE

    fun handleKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (event.action == KeyEvent.ACTION_UP && keyCode == MP01_KEYCODE_EMOJI_PICKER) {
            // Prevent the key up event from the opening hotkey from immediately closing the popup.
            if (!initialPressComplete && System.currentTimeMillis() - popupShownTime < 1000) {
                initialPressComplete = true
                return true // Consume the event
            }
            if (currentView == ViewType.EMOJI)
                hide()
            else
                switchToView(ViewType.EMOJI)
            return true
        }

        if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_SYM) {
            if (currentView == ViewType.SYMBOL)
                hide()
            else
                switchToView(ViewType.SYMBOL)
            return true
        }

        // Handle 'Enter' key only if the search bar has focus
        if (keyCode == KeyEvent.KEYCODE_ENTER && searchBar.hasFocus()) {
            if (event.action == KeyEvent.ACTION_DOWN) return true // Consume down event
            if (event.action == KeyEvent.ACTION_UP) {
                // This will insert text or an emoji and dismiss the popup.
                when (currentView) {
                    ViewType.EMOJI -> getEmojiAdapter().selectFirstEmoji()
                    ViewType.CLIPBOARD -> getClipboardAdapter().selectFirstItem()
                    else -> {}
                }
                return true
            }
        }

        // Forward other key events to the search bar if it has focus
        if (searchBar.hasFocus() && event.keyCode != KeyEvent.KEYCODE_BACK) {
             if (event.action == KeyEvent.ACTION_DOWN) {
                searchBar.onKeyDown(keyCode, event)
             } else {
                searchBar.onKeyUp(keyCode, event)
             }
             return true
        } else if (currentView == ViewType.SYMBOL) {
            // Forward all input as sym key presses on the sym key view.
            return service.forceSymKeyEvent(event)
        }
        if (event.keyCode == MP01_KEYCODE_EMOJI_PICKER || event.keyCode == KeyEvent.KEYCODE_SYM) {
            return true //
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            hide()
            return true
        }

        return false // Don't consume other events
    }

    private fun getEmojiAdapter(): EmojiAdapter {
        if (emojiAdapter == null) {
            emojiAdapter = EmojiAdapter(context, skinTone) { emoji ->
                service.currentInputConnection?.commitText(emoji.character, 1)
                hide()
            }
        }
        return emojiAdapter!!
    }

    private fun getSymbolAdapter(): SymbolAdapter {
        if (symbolAdapter == null) {
            symbolAdapter = SymbolAdapter(service) { keyCode ->
                if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
                    if (service.shift.get()) {
                        service.shift.reset()
                    } else {
                        service.shift.onKeyDown()
                        service.shift.onKeyUp()
                    }
                    service.updateModStateIcon()
                } else if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_DEL) {
                    service.sendDownUpKeyEvents(keyCode)
                } else {
                    service.onSymKey(KeyEvent(KeyEvent.ACTION_DOWN, keyCode), true)
                    service.onSymKey(KeyEvent(KeyEvent.ACTION_UP, keyCode), false)
                }
            }
        }
        return symbolAdapter!!
    }

    private fun getClipboardAdapter(): ClipboardHistoryAdapter {
        if (clipboardAdapter == null) {
            clipboardAdapter = ClipboardHistoryAdapter(context) { text ->
                service.currentInputConnection?.commitText(text, 1)
                hide()
            }.also { it.onHistoryChanged = { updateClipboardEmptyState() } }
        }
        return clipboardAdapter!!
    }

    /**
     * Show the empty message instead of the list when there is no clipboard history,
     * e.g. after the last clipping was removed.
     */
    private fun updateClipboardEmptyState() {
        if (!::recyclerView.isInitialized || currentView != ViewType.CLIPBOARD) return
        val empty = getClipboardAdapter().getHistory().isEmpty()
        emptyClipboardMessage.visibility = if (empty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
    }


    fun show(startingView: ViewType = ViewType.EMOJI) {
        // A picker takes the place of the map of additional characters.
        characterMap?.visibility = View.GONE
        pickerView.visibility = View.VISIBLE
        initialPressComplete = false
        popupShownTime = System.currentTimeMillis()
        if (!::contentArea.isInitialized) {
            setupPickerView()
        }
        val height = (context.resources.displayMetrics.heightPixels / 2.25).toInt()
        if (isShowing()) {
            hide()
            return
        }
        switchToView(startingView) // Default to emoji view
        inlineViewContainer?.let {
            val layoutParams = it.layoutParams
            layoutParams.height = height
            it.layoutParams = layoutParams
            it.visibility = View.VISIBLE
        }
        return
    }

    fun hide() {
        characterMap?.visibility = View.GONE
        pickerView.visibility = View.VISIBLE
        inlineViewContainer?.visibility = View.GONE
        // Ensure we exit any emoji meta shortcut mode used to open the picker
        service.resetEmojiMeta()
    }

    private fun setupPickerView() {
        contentArea = pickerView.findViewById(R.id.picker_content_area)
        searchBar = pickerView.findViewById(R.id.search_bar)
        titleArea = pickerView.findViewById(R.id.picker_title)
        emojiButton = pickerView.findViewById<ImageButton>(R.id.emoji_view_button)
        emojiCloseButton = pickerView.findViewById<ImageButton>(R.id.emoji_close_button)
        symButton = pickerView.findViewById(R.id.sym_button)
        symCloseButton = pickerView.findViewById(R.id.sym_close_button)
        clipboardButton = pickerView.findViewById(R.id.clipboard_button)
        clipboardClearButton = pickerView.findViewById(R.id.clipboard_clear_button)
        emptyClipboardMessage = pickerView.findViewById(R.id.empty_clipboard_message)

        // Create and add the RecyclerView here
        recyclerView = RecyclerView(context)
        contentArea.addView(recyclerView)


        symCloseButton.setOnClickListener { hide() }
        emojiCloseButton.setOnClickListener { hide() }
        emojiButton.setOnClickListener { switchToView(ViewType.EMOJI) }
        symButton.setOnClickListener { switchToView(ViewType.SYMBOL) }
        clipboardButton.setOnClickListener { switchToView(ViewType.CLIPBOARD) }
        clipboardClearButton.setOnClickListener { getClipboardAdapter().clearUnpinned() }

        pickerView.isFocusableInTouchMode = false
    }

    private fun switchToView(viewType: ViewType) {
        currentView = viewType

        recyclerView.visibility = View.GONE
        emptyClipboardMessage.visibility = View.GONE

        // Remove the searchBar watcher before switching
        activeTextWatcher?.let { searchBar.removeTextChangedListener(it) }

        symCloseButton.visibility = View.GONE
        emojiCloseButton.visibility = View.GONE
        titleArea.visibility = View.GONE
        emojiButton.visibility = View.VISIBLE
        symButton.visibility = View.VISIBLE
        searchBar.visibility = View.VISIBLE
        clipboardButton.visibility = View.VISIBLE
        clipboardClearButton.visibility = View.GONE

        when (viewType) {
            ViewType.EMOJI -> {
                emojiButton.visibility = View.GONE
                emojiCloseButton.visibility = View.VISIBLE
                recyclerView.visibility = View.VISIBLE
                val adapter = getEmojiAdapter()
                adapter.refresh()
                val layoutManager = GridLayoutManager(context, EmojiAdapter.GRID_SPAN_COUNT)
                layoutManager.spanSizeLookup = adapter.getSpanSizeLookup()
                recyclerView.layoutManager = layoutManager
                recyclerView.adapter = adapter
                searchBar.requestFocus()
                searchBar.hint = "Search emoji"
                activeTextWatcher = object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        adapter.filter.filter(s)
                    }
                    override fun afterTextChanged(s: Editable?) {}
                }
                searchBar.addTextChangedListener(activeTextWatcher)
            }
            ViewType.SYMBOL -> {
                symButton.visibility = View.GONE
                symCloseButton.visibility = View.VISIBLE
                recyclerView.visibility = View.VISIBLE
                searchBar.visibility = View.GONE
                titleArea.visibility = View.VISIBLE
                val adapter = getSymbolAdapter()
                val layoutManager = GridLayoutManager(context, 10)
                layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return adapter.getSpanSize(position)
                    }
                }
                recyclerView.layoutManager = layoutManager
                recyclerView.adapter = adapter
                popupWindow?.contentView?.requestFocus()
            }
            ViewType.CLIPBOARD -> {
                clipboardButton.visibility = View.GONE
                clipboardClearButton.visibility = View.VISIBLE
                val adapter = getClipboardAdapter()
                recyclerView.layoutManager = LinearLayoutManager(context)
                recyclerView.adapter = adapter
                adapter.refresh() // Also shows the empty message if there is nothing to show.
                searchBar.requestFocus()
                searchBar.hint = "Search clipboard"
                activeTextWatcher = object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        adapter.filter.filter(s)
                    }
                    override fun afterTextChanged(s: Editable?) {}
                }
                searchBar.addTextChangedListener(activeTextWatcher)
            }
        }
    }

    /** @return true if a picker is showing (and so takes the key presses). The map of additional characters is not one. */
    fun isShowing(): Boolean {
        return inlineViewContainer?.visibility == View.VISIBLE && !isCharacterMapShowing()
    }

}