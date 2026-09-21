package io.github.rickybrent.minimal_symlayer_keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.text.TextUtils
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethod.SHOW_FORCED
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager
import java.util.Locale
import android.inputmethodservice.InputMethodService as AndroidInputMethodService

/**
 * MP01 keycode sent instead of KeyEvent.KEYCODE_EMOJI_PICKER.
 */
const val MP01_KEYCODE_EMOJI_PICKER = 666;

/**
 * MP01 keycode sent instead of KeyEvent.KEYCODE_DICTATE.
 */
const val MP01_KEYCODE_DICTATE = 667;

// Only for modifier keys we want to force when using sym+keys to navigate.
val forceModifierPairs = listOf(
		KeyEvent.META_SHIFT_ON to KeyEvent.KEYCODE_SHIFT_LEFT,
		KeyEvent.META_META_ON to KeyEvent.KEYCODE_META_LEFT,
		KeyEvent.META_CTRL_ON to KeyEvent.KEYCODE_CTRL_LEFT,
	)

/**
 * @return true if it is suitable to provide suggestions or text transforms in the given editor.
 */
fun canUseSuggestions(editorInfo: EditorInfo): Boolean {
	if(editorInfo.inputType == InputType.TYPE_NULL) {
		return false
	}

	return when(editorInfo.inputType and InputType.TYPE_MASK_VARIATION) {
		InputType.TYPE_TEXT_VARIATION_PASSWORD -> false
		InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> false
		InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> false
		InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> false
		InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> false
		InputType.TYPE_TEXT_VARIATION_URI -> false
		else -> true
	}
}

/**
 * @return A KeyEvent made from the given one, but with the given key code.
 */
fun makeKeyEvent(original: KeyEvent, code: Int): KeyEvent {
	return makeKeyEvent(original, code, original.metaState, original.action, original.source, KeyCharacterMap.VIRTUAL_KEYBOARD)
}

/**
 * @return A KeyEvent made from the given one, but with the given key code, meta state, action, and source.
 */
fun makeKeyEvent(original: KeyEvent, code: Int, metaState: Int, action: Int, source: Int): KeyEvent {
	return makeKeyEvent(original, code, metaState, action, source, KeyCharacterMap.VIRTUAL_KEYBOARD)
}

/**
 * @return A KeyEvent made from the given one, but with the given key code, meta state, action, source, and deviceId.
 */
fun makeKeyEvent(original: KeyEvent, code: Int, metaState: Int, action: Int, source: Int, deviceId: Int): KeyEvent {
	return KeyEvent(original.downTime, original.eventTime, action, code, original.repeatCount, metaState, deviceId, code, 0, source)
}

val templates = hashMapOf(
	"fr" to hashMapOf(
		KeyEvent.KEYCODE_A to arrayOf('`', '^', 'æ', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_E to arrayOf('´', '`', '^', '¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_I to arrayOf('^', '¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_O to arrayOf('^', 'œ', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_U to arrayOf('`', '^', '¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_Y to arrayOf('¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_C to arrayOf('ç', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf(MPSUBST_STR_DOTSPACE)
	),
	"es" to hashMapOf(
		KeyEvent.KEYCODE_A to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_E to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_I to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_O to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_U to arrayOf('´', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf(MPSUBST_STR_DOTSPACE)
	),
	// French and Spanish together: every accent of either language on the vowels, the most used first.
	"fr-es" to hashMapOf(
		KeyEvent.KEYCODE_A to arrayOf('`', '^', '´', 'æ', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_E to arrayOf('´', '`', '^', '¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_I to arrayOf('´', '^', '¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_O to arrayOf('´', '^', 'œ', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_U to arrayOf('´', '`', '^', '¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_Y to arrayOf('¨', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_C to arrayOf('ç', MPSUBST_BYPASS),
		KeyEvent.KEYCODE_SPACE to arrayOf(MPSUBST_STR_DOTSPACE)
	)
)

class InputMethodService : AndroidInputMethodService() {
	private lateinit var vibrator: Vibrator
	private var pickerManager: PickerManager? = null
	private var mainInputView: View? = null
	private var inputViewStrip: View? = null
	private var stripModifierRow: LinearLayout? = null

	val shift = Modifier()
	private val alt = Modifier()
	private val sym = SimpleModifier()
	private val dotCtrl = TripleModifier()
	private val emojiMeta = TripleModifier()
	private val caps = Modifier()

	private val voiceInput = VoiceInput(this)
	private val suggestionController = SuggestionController(
		this,
		onApplied = { vibrate() }
	)
	// Set when Enter was sent by us after a fix, so that the real key's release is not passed on as well.
	private var swallowEnterUp = false
	private var learnedWordsResetTime = -1L
	private var learnedWordsReloadTime = -1L

	private var lastShift = false
	private var lastAlt = false
	private var lastSym = false
	private var lastDotCtrl = false
	private var lastEmojiMeta = false
	private var lastCaps = false

	private var autoCapitalize = false
	private var showToolbar = false
	private var isInputViewActive = false

	enum class DeviceType(val source: Int) {
		MP01(InputDevice.SOURCE_KEYBOARD)
	}
	val deviceType = DeviceType.MP01

	private val multipress = MultipressController(arrayOf(
		templates["fr-es"]!!,
		hashMapOf(
			KeyEvent.KEYCODE_Q to arrayOf(MPSUBST_TOGGLE_ALT, '°', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_W to arrayOf(MPSUBST_TOGGLE_ALT, '&', '↑', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_E to arrayOf(MPSUBST_TOGGLE_ALT, '€', '∃', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_R to arrayOf(MPSUBST_TOGGLE_ALT, '®', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_T to arrayOf(MPSUBST_TOGGLE_ALT, '[', '{', '<', '≤', '†', '™', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_Y to arrayOf(MPSUBST_TOGGLE_ALT, ']', '}', '>', '≥', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_U to arrayOf(MPSUBST_TOGGLE_ALT, '—', '–', '∪', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_I to arrayOf(MPSUBST_TOGGLE_ALT, '|', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_O to arrayOf(MPSUBST_TOGGLE_ALT, '\\', 'œ', 'º', '÷', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_P to arrayOf(MPSUBST_TOGGLE_ALT, ';', '¶', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_A to arrayOf(MPSUBST_TOGGLE_ALT, 'æ', 'ª', '←', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_S to arrayOf(MPSUBST_TOGGLE_ALT, 'ß', '§', '↓', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_D to arrayOf(MPSUBST_TOGGLE_ALT, '∂', '→', '⇒', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_F to arrayOf(MPSUBST_TOGGLE_ALT, MPSUBST_CIRCUMFLEX, MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_G to arrayOf(MPSUBST_TOGGLE_ALT, '•', '·', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_H to arrayOf(MPSUBST_TOGGLE_ALT, '²', '♯', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_J to arrayOf(MPSUBST_TOGGLE_ALT, '=', '≠', '≈', '±', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_K to arrayOf(MPSUBST_TOGGLE_ALT, '%', '‰', '‱', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_L to arrayOf(MPSUBST_TOGGLE_ALT, MPSUBST_BACKTICK, MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_Z to arrayOf(MPSUBST_TOGGLE_ALT, '¡', '‽', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_X to arrayOf(MPSUBST_TOGGLE_ALT, '×', 'χ', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_C to arrayOf(MPSUBST_TOGGLE_ALT, 'ç', '©', '¢', '⊂', '⊄', '⊃', '⊅', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_V to arrayOf(MPSUBST_TOGGLE_ALT, '∀', '√', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_B to arrayOf(MPSUBST_TOGGLE_ALT, '…', 'ß', '∫', '♭', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_N to arrayOf(MPSUBST_TOGGLE_ALT, '~', '¬', '∩', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_M to arrayOf(MPSUBST_TOGGLE_ALT, '$', '€', '£', '¿', MPSUBST_TOGGLE_SHIFT, MPSUBST_BYPASS),
			MP01_KEYCODE_EMOJI_PICKER to arrayOf(MPSUBST_TOGGLE_ALT, MPSUBST_BYPASS),
			MP01_KEYCODE_DICTATE to arrayOf(MPSUBST_TOGGLE_ALT, MPSUBST_BYPASS),
			KeyEvent.KEYCODE_SPACE to arrayOf('\t', '⇥', MPSUBST_BYPASS)
		)
	))

	private val unlockReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
				updateFromPreferences()
			}
		}
	}

	override fun onCreate() {
		super.onCreate()
		val context = createDeviceProtectedStorageContext()
		pickerManager = PickerManager(this, this)

		val preferences = PreferenceManager.getDefaultSharedPreferences(context)
		preferences.registerOnSharedPreferenceChangeListener { _, _ ->
			updateFromPreferences()
		}
		updateFromPreferences()
		val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
		registerReceiver(unlockReceiver, filter)

		vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			val mgr = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
			mgr.defaultVibrator
		} else {
			@Suppress("DEPRECATION")
			getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		suggestionController.onDestroy()
		pickerManager?.hide()
		unregisterReceiver(unlockReceiver)
	}

	override fun onCreateInputView(): View {
		mainInputView = layoutInflater.inflate(R.layout.input_view_container, null)

		val pickerContainer = mainInputView?.findViewById<FrameLayout>(R.id.picker_container_inline)
		pickerManager?.setInlineViewContainer(pickerContainer)

		val inputContainer = mainInputView?.findViewById<FrameLayout>(R.id.input_view_container)
		val strip = layoutInflater.inflate(R.layout.input_view_strip, null)
		this.inputViewStrip = strip
		stripModifierRow = strip.findViewById(R.id.modifier_row)
		strip.findViewById<ImageButton>(R.id.toolbar_emoji).setOnClickListener { showEmojiPicker() }
		strip.findViewById<ImageButton>(R.id.toolbar_clipboard).setOnClickListener { showClipboardHistory() }
		strip.findViewById<ImageButton>(R.id.toolbar_voice).setOnClickListener { startVoiceInput() }
		suggestionController.setViews(listOf(
			strip.findViewById<TextView>(R.id.suggestion_0),
			strip.findViewById<TextView>(R.id.suggestion_1),
			strip.findViewById<TextView>(R.id.suggestion_2)
		))
		inputContainer?.addView(strip)
		strip.visibility = if (showToolbar) View.VISIBLE else View.GONE
		updateToolbarModifiers()

		return mainInputView!!
	}

	override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
		super.onStartInputView(info, restarting)
		isInputViewActive = true
		updateStatusIconIfNeeded()
		suggestionController.onStartInputView(info)
		commitDictatedText()
	}

	/**
	 * Type in what was dictated with the speech dialog, which is shown by another activity and
	 * so can only be typed in once the app being typed into has focus again.
	 */
	private fun commitDictatedText() {
		var text = VoiceInput.takePendingText() ?: return
		val ic = currentInputConnection ?: return
		if (ic.getCursorCapsMode(TextUtils.CAP_MODE_SENTENCES) != 0) {
			text = text.replaceFirstChar { it.uppercase() }
		}
		ic.commitText(text, 1)
	}

	private fun showEmojiPicker() {
		if (isInputViewActive.not()) requestShowSelf(SHOW_FORCED)
		pickerManager?.show()
	}

	private fun showClipboardHistory() {
		if (isInputViewActive.not()) requestShowSelf(SHOW_FORCED)
		pickerManager?.show(PickerManager.ViewType.CLIPBOARD)
	}

	override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
		super.onStartInput(attribute, restarting)

		updateFromPreferences()
		suggestionController.onStartInput(attribute)

		if(!sym.get()) {
			updateAutoCapitalization()
		}
	}

	/**
	 * Reset the shift/caps state when the InputView is closed and update the icons.
	 * Prevents auto-caps's icon from appearing when no text input is active.
	 */
	override fun onFinishInput() {
		super.onFinishInput()
		suggestionController.onFinishInput()
	}

	override fun onFinishInputView(finishingInput: Boolean) {
		super.onFinishInputView(finishingInput)
		isInputViewActive = false
		shift.reset()
		caps.reset()
		updateStatusIconIfNeeded()
		pickerManager?.hide()
		suggestionController.onFinishInputView()
	}

	override fun onUpdateSelection(
		oldSelStart: Int,
		oldSelEnd: Int,
		newSelStart: Int,
		newSelEnd: Int,
		candidatesStart: Int,
		candidatesEnd: Int
	) {
		if(!sym.get()) {
			updateAutoCapitalization()
		}
		suggestionController.onSelectionUpdate(newSelStart, newSelEnd)

		super.onUpdateSelection(
			oldSelStart,
			oldSelEnd,
			newSelStart,
			newSelEnd,
			candidatesStart,
			candidatesEnd
		)
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
		if (isInputViewActive && pickerManager?.isShowing() == true) {
			pickerManager!!.handleKeyEvent(event) // always eat
			return true
		} else if (event.keyCode == KeyEvent.KEYCODE_BACK) {
			// While the toolbar is showing, the system gives Back to the keyboard first, and an app that was built
			// for newer Android ignores Back sent as a key. So hide the toolbar like any keyboard does, which lets
			// the next Back go to the app, and still pass this one on for the apps that do take it as a key.
			if (showToolbar && isInputViewShown) requestHideSelf(0)
			sendDownUpKeyEvents(event.keyCode)
			return true
		}

		// Update modifier states
		if(!event.isLongPress && event.repeatCount == 0) {
			when(event.keyCode) {
				KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
					alt.onKeyDown()
					updateStatusIconIfNeeded(true)
				}
				KeyEvent.KEYCODE_SHIFT_LEFT -> {
					if (caps.get()) {
						caps.reset()
					} else {
						shift.onKeyDown()
					}
					updateStatusIconIfNeeded(true)
				}
				KeyEvent.KEYCODE_SHIFT_RIGHT -> {
					if (caps.get()) {
						caps.reset()
					} else {
						shift.onKeyDown()
					}
					updateStatusIconIfNeeded(true)
				}
				KeyEvent.KEYCODE_SYM -> {
					sym.onKeyDown()
					onSymPossiblyChanged()
					updateStatusIconIfNeeded(true)
				}
				MP01_KEYCODE_DICTATE -> {
					dotCtrl.onKeyDown()
					updateStatusIconIfNeeded(true)
				}
				MP01_KEYCODE_EMOJI_PICKER -> {
					emojiMeta.onKeyDown()
					updateStatusIconIfNeeded(true)
				}
			}
		}

		if(event.isCtrlPressed) {
			return super.onKeyDown(keyCode, event)
		}

		// Apply any special logic for triple modifiers that may modify key handling.
		if (tripleModifierOnKeyDown(keyCode, event)) {
			return true
		}

		// Use special behavior when the SYM modifier is enabled
		if(sym.get()) {
			return onSymKey(event, true)
		}

		// Apply multipress substitution
		if(event.isPrintingKey || event.keyCode == KeyEvent.KEYCODE_SPACE) {
			val char = multipress.process(event, enhancedMetaState(event))
			if(char != MPSUBST_BYPASS) {
				if(char != MPSUBST_NOTHING) {
					currentInputConnection?.deleteSurroundingText(1, 0)
					updateAutoCapitalization()
					when(char) {
						MPSUBST_STR_DOTSPACE -> currentInputConnection?.commitText(". ", 2)
						else -> sendCharacter(char.toString())
					}

					consumeModifierNext()
					vibrate()
				}
				return true
			}
		}

		// Handle backspace/delete
		if(event.keyCode == KeyEvent.KEYCODE_DEL || event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
			multipress.reset()
			// Backspace right after a typo was fixed puts the word back as it was typed.
			if (event.keyCode == KeyEvent.KEYCODE_DEL && event.repeatCount == 0 && suggestionController.undoAutoCorrect()) {
				consumeModifierNext()
				return true
			}
			consumeModifierNext()

			return super.onKeyDown(keyCode, event)
		}

		// Ignore all long presses after this point
		if(event.isLongPress || event.repeatCount > 0) {
			return true
		}

		// Print something if it is a simple printing key press
		if((event.isPrintingKey || event.keyCode == KeyEvent.KEYCODE_SPACE || (event.keyCode == KeyEvent.KEYCODE_ENTER && shift.get()))) {
			val isShifted = shift.get() || caps.get()
			val str = if (alt.get() && multipress.overrideAltKeys) {
				// temporary workaround with the latest software update.
				AltKeyMappings.getAltKeyChar(event.keyCode, isShifted)?.toString()
					?: event.getUnicodeChar(enhancedMetaState(event)).toChar().toString()
			} else {
				event.getUnicodeChar(enhancedMetaState(event)).toChar().toString()
			}
			currentInputConnection?.commitText(str, 1)

			consumeModifierNext()
			return true
		}

		if(event.keyCode == KeyEvent.KEYCODE_ENTER) {
			// The last word of a message is only fixed if it is done before Enter sends the message. The key is
			// sent again after the fix, in order behind it, as a key press from the physical keyboard, rather than
			// left to race the fix to the app.
			if (!shift.get() && !alt.get() && !event.isCtrlPressed && suggestionController.fixBeforeEnter()) {
				consumeModifierNext()
				swallowEnterUp = true
				sendKey(KeyEvent.KEYCODE_ENTER, event, true)
				sendKey(KeyEvent.KEYCODE_ENTER, event, false)
				return true
			}
			consumeModifierNext()
		}

		return super.onKeyDown(keyCode, event)
	}

	fun tripleModifierOnKeyDown(keyCode: Int, event: KeyEvent): Boolean {
		if (event.keyCode == MP01_KEYCODE_EMOJI_PICKER || event.keyCode == MP01_KEYCODE_DICTATE) {
			val tripleMod =
				if (event.keyCode == MP01_KEYCODE_EMOJI_PICKER) emojiMeta else dotCtrl;
			if (alt.get()) {
				tripleMod.activateSkipKeyUp()
				return false
			}
			if (!tripleMod.get()) {
				tripleMod.onKeyDown();
			}
			if (multipress.process(event, enhancedMetaState(event)) == MPSUBST_BYPASS) {
				return true;
			}
			if (!tripleMod.isLongPress()) {
				tripleMod.activateLongPress()
				vibrate()
			}
			return true
		} else if (KeyEvent.isModifierKey(event.keyCode)) {
			// pass
		} else if (dotCtrl.get() && dotCtrl.getModKey() == 0 && dotCtrl.modKeyCode != 0) {
			// mark that we've activated the mod key, then send ctrl.
			dotCtrl.activateModKey()
			sendKey(dotCtrl.getModKey(), event, true)
		} else if (emojiMeta.get() && emojiMeta.getModKey() == 0 && emojiMeta.modKeyCode != 0) {
			// mark that we've activated the mod key, then send meta.
			emojiMeta.activateModKey()
			sendKey(emojiMeta.getModKey(), event, true)
		}
		// Prioritizing ctrl/meta, so commenting this out:
		// if(sym.get()) { return onSymKey(event, true) }
		// Handle emojiMeta + key shortcuts.
		if (emojiMeta.get() && onEmojiMetaShotcut(event)) {
			emojiMeta.activateSkipKeyUp()
			return true
		}

		// If either modkey is active, send the key as a keypress.
		if (dotCtrl.getModKey() != 0 || emojiMeta.getModKey() != 0) {
			sendKey(keyCode, event, true)
			sendKey(keyCode, event, false)
			return true
		}
		return false
	}

	/**
	 * Overridden to ensure the input view is shown when our inline picker is active,
	 * even when a hardware keyboard is connected.
	 */
	override fun onEvaluateInputViewShown(): Boolean {
		return true || super.onEvaluateInputViewShown()
	}

	override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
		if (isInputViewActive && pickerManager?.isShowing() == true) {
			pickerManager!!.handleKeyEvent(event) // always eat
			return true
		}
		if (event.keyCode == KeyEvent.KEYCODE_ENTER && swallowEnterUp) {
			swallowEnterUp = false
			return true
		}

		// Update modifier states
		when(event.keyCode) {
			KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
				alt.onKeyUp()
				updateStatusIconIfNeeded(true)
			}
			KeyEvent.KEYCODE_SHIFT_LEFT -> {
				shift.onKeyUp()
				updateStatusIconIfNeeded(true)
			}
			KeyEvent.KEYCODE_SHIFT_RIGHT -> {
				shift.onKeyUp()
				updateStatusIconIfNeeded(true)
			}
			KeyEvent.KEYCODE_SYM -> {
				sym.onKeyUp()
				onSymPossiblyChanged()
				updateStatusIconIfNeeded(true)
			}
		}

		// Apply any special logic for triple modifiers that may modify key handling.
		if (tripleModifierOnKeyUp(keyCode, event)) {
			return true
		}
		// Use special behavior when the SYM modifier is enabled
		if(sym.get()) {
			return onSymKey(event, false)
		}

		return super.onKeyUp(keyCode, event)
	}

	/**
	 * Handle a triple modifier key up, which may send additional key presses or actions depending on if a key was pressed or how long it was held.
	 */
	private fun tripleModifierOnKeyUp(keyCode: Int, event: KeyEvent): Boolean {
		if (keyCode == MP01_KEYCODE_DICTATE || keyCode == MP01_KEYCODE_EMOJI_PICKER) {
			val modifier = if (event.keyCode == MP01_KEYCODE_EMOJI_PICKER) emojiMeta else dotCtrl;
			val modKey = modifier.getModKey()
			var kbdKey = modifier.getKey()
			var metaState = event.metaState
			modifier.reset();
			updateStatusIconIfNeeded(true)
			if (alt.get() && multipress.overrideAltKeys) {
				// TODO: this may not even be correct.
				kbdKey = modifier.getAltKey()
				metaState = metaState and KeyEvent.META_ALT_ON.inv()
			}

			if (modKey != 0) {
				sendKey(modKey, event, false)
				return true
			} else if (kbdKey != 0) {
				// Simulate tapping the shortpress or longpress key.
				simulateKeyTap(kbdKey, event, metaState)
				consumeModifierNext()
				return true
			}
		}

		// Prioritizing ctrl/meta, so commenting this out:
		// if(sym.get()) { return onSymKey(event, false) }

		if (dotCtrl.getModKey() != 0 || emojiMeta.getModKey() != 0) {
			sendKey(keyCode, event, false)
			return true
		}

		return false
	}

	/**
	 * Handle a key down event when the SYM modifier is enabled.
	 */
	fun onSymKey(event: KeyEvent, pressed: Boolean): Boolean {
		val mapping = SymKeyMappings.getMapping(event.keyCode, deviceType) ?: return if (!event.isPrintingKey) {
			if (pressed) super.onKeyDown(event.keyCode, event) else super.onKeyUp(event.keyCode, event)
		} else true

		if (pressed && event.repeatCount == 0 && !event.isLongPress) {
			when (val action = mapping.action) {
				is SendKey -> sendKey(action.keyCode, event, true)
				is SendChar -> {
					val char = if (shift.get() && action.shiftedCharacter != null) action.shiftedCharacter else action.character
					sendCharacter(char)
				}
				is ShiftPress -> {
					shift.onKeyDown()
					updateStatusIconIfNeeded(true)
				}
			}
		} else if (!pressed) {
			when (val action = mapping.action) {
				is SendKey -> sendKey(action.keyCode, event, false)
				is SendChar -> { /* No action on key up for characters */ }
				is ShiftPress -> {
					shift.onKeyUp()
					updateStatusIconIfNeeded(true)
				}
			}
		}
		return true
	}

	// Event passed back to us from the Popup for sym key presses.
	fun forceSymKeyEvent(event: KeyEvent): Boolean {
		val pressed = event.action == KeyEvent.ACTION_DOWN
		if (event.keyCode == MP01_KEYCODE_DICTATE)
			return onSymKey(makeKeyEvent(event, KeyEvent.KEYCODE_PERIOD), pressed)
		if (onSymKey(event, pressed))
			return true

		return false
	}

	/**
	 * Handle keyboard shortcuts where emojiMeta is held.
	 */
	private fun onEmojiMetaShotcut(event: KeyEvent): Boolean {
		// skip the extra simulateKeyTap logic with sendDownUpKeyEvents.
		emojiMeta.activateSkipKeyUp()
		currentInputConnection?.sendKeyEvent(makeKeyEvent(event, emojiMeta.modKeyCode, 0, KeyEvent.ACTION_UP, InputDevice.SOURCE_KEYBOARD))
		return when (event.keyCode) {
			KeyEvent.KEYCODE_V -> {
				showClipboardHistory()
				true
			}
			KeyEvent.KEYCODE_SPACE -> {
				showEmojiPicker()
				true
			}
			KeyEvent.KEYCODE_M -> {
				sendDownUpKeyEvents(KeyEvent.KEYCODE_MENU)
				true
			}
			KeyEvent.KEYCODE_Q -> {
				sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
				true
			}
			KeyEvent.KEYCODE_DEL -> {
				sendDownUpKeyEvents(KeyEvent.KEYCODE_ESCAPE)
				true
			}
			MP01_KEYCODE_DICTATE -> {
				// TODO: latch control, even if disabled from dotCtrl.
				true
			}
			// Use intents in place of system-level key events.
			KeyEvent.KEYCODE_ENTER -> { // Home
				launchApp(Intent.ACTION_MAIN, Intent.CATEGORY_HOME)
				true
			}
			KeyEvent.KEYCODE_E -> { // Email
				launchApp(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)
				true
			}
			KeyEvent.KEYCODE_A -> { // Assistant (uses a different action)
				launchApp(Intent.ACTION_ASSIST)
				true
			}
			KeyEvent.KEYCODE_S -> { // Messaging
				launchApp(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING )
				true
			}
			KeyEvent.KEYCODE_C -> { // Contacts
				launchApp(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS)
				true
			}
			KeyEvent.KEYCODE_B -> { // Browser
				launchApp(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
				true
			}
			KeyEvent.KEYCODE_I -> { // Settings
				launchApp(Settings.ACTION_SETTINGS)
				true
			}
			KeyEvent.KEYCODE_P -> { // Music
				launchApp(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
				true
			}
			KeyEvent.KEYCODE_L -> { // Calendar
				launchApp(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
				true
			}
			// Pick the first, second or third word suggestion, if there is one. These keys are
			// otherwise unused, so they fall through to the normal handling when there is nothing to pick.
			KeyEvent.KEYCODE_F -> suggestionController.applySlot(0)
			KeyEvent.KEYCODE_G -> suggestionController.applySlot(1)
			KeyEvent.KEYCODE_H -> suggestionController.applySlot(2)
			// KeyEvent.KEYCODE_N -> // Notification shade. No standard intent for this.
			// We may be able to use an accessibility service, but it's not a priority for me.
			// Menu and Escape will only work for some apps when sent like this as well.
			else -> false
		}
	}

	/**
	 * Send a key press or release.
	 */
	private fun sendKey(code: Int, original: KeyEvent, pressed: Boolean) {
		val newState = enhancedMetaState(original)
		forceMatchMetaState(original, newState, pressed)
		currentInputConnection?.sendKeyEvent(makeKeyEvent(original, code, newState, if(pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, InputDevice.SOURCE_KEYBOARD))
		forceMatchMetaState(original, newState, false)
	}

	/**
	 * Send a character, possibly uppercased depending on the Shift modifier.
	 */
	private fun sendCharacter(str: String, strict: Boolean = false) {
		var text = str
		if (!strict && (shift.get() || caps.get())) {
			text = text.uppercase(Locale.getDefault())
		}
		currentInputConnection?.commitText(text, 1)
	}

	private fun simulateKeyTap(code: Int, original: KeyEvent, metaState: Int) {
		if (code == KeyEvent.KEYCODE_PICTSYMBOLS) {
			if (!emojiMeta.skipKeyUp()) {
				showEmojiPicker()
				emojiMeta.reset()
			}
			return
		} else if (code == KeyEvent.KEYCODE_VOICE_ASSIST) {
			startVoiceInput()
			dotCtrl.reset()
			return
		}
		val event = makeKeyEvent(original, code, metaState, original.action, original.source, original.deviceId)
		if (sym.get()) {
			onSymKey(event, true)
			onSymKey(event, false)
		} else {
			val charInt = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).get(code, 0)
			if (multipress.overrideAltKeys && charInt != 0) {
				currentInputConnection?.commitText(charInt.toChar().toString(), 1)
				return
			}
			sendKey(code, event, true)
			sendKey(code, event, false)
		}
	}

	/**
	 * Forcefully match the metastate by pressing any missing modifier keys.
	 */
	private fun forceMatchMetaState(original: KeyEvent, enhanced: Int, pressed: Boolean) {
		val origMeta = original.metaState
		for ((metaOn, metaKey) in forceModifierPairs) {
			if (origMeta and metaOn == 0 && enhanced and metaOn != 0) {
				currentInputConnection?.sendKeyEvent(makeKeyEvent(original, metaKey, enhanced, if(pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, InputDevice.SOURCE_KEYBOARD))
			}
		}
	}

	/**
	 * Make the device vibrate.
	 */
	private fun vibrate() {
		vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
	}

	fun updateModStateIcon() {
		updateStatusIconIfNeeded(true)
	}

	/**
	 * Update the icon in the status bar according to modifier states.
	 */
	private fun updateStatusIconIfNeeded(force: Boolean = false) {
		val shiftState = shift.get()
		val altState = alt.get()
		val symState = sym.get()
		val ctrlState = dotCtrl.get()
		val capsState = caps.get()
		val metaState = emojiMeta.get()
		if(force || symState != lastSym || altState != lastAlt || shiftState != lastShift || capsState != lastCaps || ctrlState != lastDotCtrl || metaState != lastEmojiMeta) {
			if(sym.get()) {
				if (shift.get()) {
					showStatusIcon(R.drawable.symshift)
				} else {
					showStatusIcon(R.drawable.sym)
				}
			} else if(emojiMeta.get()) {
				showStatusIcon(R.drawable.meta)
			} else if (dotCtrl.get()) {
				showStatusIcon(if (dotCtrl.isLocked()) R.drawable.ctrllock else R.drawable.ctrl)
			} else if(alt.get()) {
				showStatusIcon(if (alt.isLocked()) R.drawable.altlock else R.drawable.alt)
			} else if(shift.get()) {
				showStatusIcon(if(shift.isLocked()) R.drawable.shiftlock else R.drawable.shift)
			} else if(caps.get()) {
				showStatusIcon(if(caps.isLocked()) R.drawable.capslock else R.drawable.caps)
			} else {
				hideStatusIcon()
			}
			updateToolbarModifiers()
		}
		lastShift = shiftState
		lastAlt = altState
		lastSym = symState
		lastDotCtrl = ctrlState
		lastCaps = capsState
		lastEmojiMeta = metaState
	}

	/**
	 * Show every active modifier in the toolbar, unlike the status bar icon which can only show one.
	 */
	private fun updateToolbarModifiers() {
		val row = stripModifierRow ?: return
		row.removeAllViews()

		fun addIcon(iconResId: Int, description: String) {
			val icon = layoutInflater.inflate(R.layout.toolbar_modifier_icon, row, false) as ImageView
			icon.setImageResource(iconResId)
			icon.contentDescription = description
			row.addView(icon)
		}

		if (sym.get()) addIcon(R.drawable.sym, "Sym")
		if (emojiMeta.get()) addIcon(R.drawable.meta, "Meta")
		if (dotCtrl.get()) addIcon(if (dotCtrl.isLocked()) R.drawable.ctrllock else R.drawable.ctrl, "Ctrl")
		if (alt.get()) addIcon(if (alt.isLocked()) R.drawable.altlock else R.drawable.alt, "Alt")
		if (shift.get()) addIcon(if (shift.isLocked()) R.drawable.shiftlock else R.drawable.shift, "Shift")
		if (caps.get()) addIcon(if (caps.isLocked()) R.drawable.capslock else R.drawable.caps, "Caps")
	}

	/**
	 * Update the Shift modifier state for auto-capitalization.
	 */
	private fun updateAutoCapitalization() {
		if(!autoCapitalize) {
			return
		}
		if(currentInputEditorInfo == null || currentInputConnection == null) {
			return
		}

		if(currentInputConnection.getCursorCapsMode(TextUtils.CAP_MODE_SENTENCES) > 0 && canUseSuggestions(currentInputEditorInfo)) {
			caps.activateForNext()
			updateStatusIconIfNeeded()
		}
	}

	/**
	 * Inform modifiers that the "next" key press has been consumed.
	 */
	private fun consumeModifierNext() {
		shift.nextDidConsume()
		alt.nextDidConsume()
		caps.nextDidConsume()
		dotCtrl.nextDidConsume()
		emojiMeta.nextDidConsume()
		updateStatusIconIfNeeded()
	}

	/**
	 * @return The metaState of the given event, enhanced with our own modifiers.
	 */
	private fun enhancedMetaState(original: KeyEvent): Int {
		var metaState = original.metaState
		if(shift.get()) {
			metaState = metaState or KeyEvent.META_SHIFT_ON
		}
		if(caps.get()) {
			metaState = metaState or KeyEvent.META_CAPS_LOCK_ON
		}
		if(alt.get()) {
			metaState = metaState or KeyEvent.META_ALT_ON
		}
		if (dotCtrl.getModKey() != 0) {
			metaState = metaState or KeyEvent.META_CTRL_ON
		}
		if (emojiMeta.getModKey() != 0) {
			metaState = metaState or KeyEvent.META_META_ON
		}
		// Strip the sym state if it is pressed.
		return metaState and KeyEvent.META_SYM_ON.inv()
	}

	/**
	 * Handle what happens when the SYM modifier has possibly changed.
	 */
	private fun onSymPossiblyChanged() {
		if(sym.get() && !lastSym) {
			if(shift.get() && !shift.isHeld()) {
				shift.reset()
			}
		} else if(!sym.get() && lastSym) {
			updateAutoCapitalization()
		}
	}

	/**
	 * Update values from the preferences.
	 */
	private fun updateFromPreferences() {
		val context = createDeviceProtectedStorageContext()
		val preferences = PreferenceManager.getDefaultSharedPreferences(context)

		showToolbar = preferences.getBoolean("pref_show_toolbar", false)
		this.inputViewStrip?.visibility = if (showToolbar) View.VISIBLE else View.GONE

		// Suggestions are shown in the toolbar, so there is nothing to do when it is hidden.
		suggestionController.enabled = showToolbar && preferences.getBoolean("pref_suggestions", true)
		suggestionController.learnWords = preferences.getBoolean("pref_learn_words", true)
		suggestionController.useCommonWords = preferences.getBoolean("pref_common_words", true)
		suggestionController.autoCorrectLevel = AutoCorrectLevel.fromPreference(preferences.getString("pref_autocorrect", "medium"))
		suggestionController.grammarLevel = GrammarLevel.fromPreference(preferences.getString("pref_grammar", "full"))
		suggestionController.autoSpace = preferences.getBoolean("pref_autospace", true)
		suggestionController.splitWords = preferences.getBoolean("pref_split_words", true)
		suggestionController.fixOnEnter = preferences.getBoolean("pref_fix_on_enter", true)
		suggestionController.capitalizeSentences = preferences.getBoolean("AutoCapitalize", true)
		suggestionController.personal = PersonalDictionary.parse(preferences.getString("pref_shortcuts", ""))
		val resetTime = preferences.getLong("pref_learned_words_reset", 0L)
		if (learnedWordsResetTime >= 0 && resetTime > learnedWordsResetTime) {
			suggestionController.clearLearnedWords()
		}
		learnedWordsResetTime = resetTime
		val reloadTime = preferences.getLong("pref_learned_words_reload", 0L)
		if (learnedWordsReloadTime >= 0 && reloadTime > learnedWordsReloadTime) {
			suggestionController.reloadLearnedWords()
		}
		learnedWordsReloadTime = reloadTime

		voiceInput.engine = VoiceInput.Engine.fromPreference(preferences.getString("pref_voice_engine", "google"))

		pickerManager?.applySettings(
			skinTone = preferences.getString("pref_emoji_skin_tone", SkinTone.DEFAULT) ?: SkinTone.DEFAULT,
			clipboardMaxUnpinned = preferences.getInt("pref_clipboard_size", ClipboardHistoryModel.DEFAULT_MAX_UNPINNED),
			clipboardExpireMillis = (preferences.getString("pref_clipboard_expire", "0")?.toLongOrNull() ?: 0L) * 60_000L,
			clipboardSkipSensitive = preferences.getBoolean("pref_clipboard_skip_sensitive", true)
		)

		autoCapitalize = preferences.getBoolean("AutoCapitalize", true)

		val lockThreshold = preferences.getInt("ModifierLockThreshold", 250)
		shift.lockThreshold = lockThreshold
		alt.lockThreshold = lockThreshold
		sym.lockThreshold = lockThreshold

		val nextThreshold = preferences.getInt("ModifierNextThreshold", 350)
		shift.nextThreshold = nextThreshold
		alt.nextThreshold = nextThreshold

		multipress.multipressThreshold = preferences.getInt("MultipressThreshold", 750)
		multipress.ignoreDotSpace = !preferences.getBoolean("DotSpace", true)
		multipress.ignoreFirstLevel = !preferences.getBoolean("UseFirstLevel", false)
		multipress.ignoreConsonantsOnFirstLevel = preferences.getBoolean("FirstLevelOnlyVowels", false)
		multipress.ligaturesEnabled = preferences.getBoolean("pref_enable_ligatures", false)
		// The phone's own alt key map is always replaced by ours.
		multipress.overrideAltKeys = true

		val templateId = preferences.getString("FirstLevelTemplate", "fr-es")
		if(templates.containsKey(templateId)) {
			multipress.substitutions[0] = templates[templateId]!!
		}

		dotCtrl.shortPressKeyCode = preferenceToKeyCode(preferences.getString("pref_dotctrl_tap", "period"))
		dotCtrl.longPressKeyCode = preferenceToKeyCode(preferences.getString("pref_dotctrl_long_press", "voice"))
		dotCtrl.modKeyCode = preferenceToKeyCode(preferences.getString("pref_dotctrl_hold", "ctrl"))

		emojiMeta.shortPressKeyCode = preferenceToKeyCode(preferences.getString("pref_emojimeta_tap", "emoji"))
		emojiMeta.longPressKeyCode = preferenceToKeyCode(preferences.getString("pref_emojimeta_long_press", "0"))
		emojiMeta.modKeyCode = preferenceToKeyCode(preferences.getString("pref_emojimeta_hold", "meta"))


		// TODO: Separate modifier and special-key logic and add better handling for sym and right shift.
	}

	private fun preferenceToKeyCode(preferenceValue: String?): Int {
		return when (preferenceValue) {
			"period" -> KeyEvent.KEYCODE_PERIOD
			"voice" -> KeyEvent.KEYCODE_VOICE_ASSIST
			"ctrl" -> KeyEvent.KEYCODE_CTRL_RIGHT
			"emoji" -> KeyEvent.KEYCODE_PICTSYMBOLS
			"0" -> KeyEvent.KEYCODE_0
			"meta" -> KeyEvent.KEYCODE_META_LEFT
			else -> 0 // "none" or any other value
		}
	}

	private fun startVoiceInput() {
		voiceInput.start()
	}

	/**
	 * Launches an application using an Intent.
	 */
	private fun launchApp(action: String, category: String? = null) {
		val intent = Intent(action)
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		if (category != null) {
			intent.addCategory(category)
		}
		try {
			startActivity(intent)
		} catch (e: Exception) {
			// Handle cases where the app isn't found or another error occurs
			e.printStackTrace()
		}
	}

	fun clearModifiers() {
		shift.reset()
		alt.reset()
		sym.reset()
		dotCtrl.reset()
		emojiMeta.reset()
		caps.reset()
		updateStatusIconIfNeeded(true)
	}

	/**
	 * Reset only the Emoji Meta modifier state and refresh the status icon.
	 *
	 * This is used by the picker when it gets dismissed via the emoji button
	 * (or close/back). Without this, if the picker was opened using an
	 * emoji meta shortcut (e.g., emoji + space), the modifier could remain
	 * latched, keeping the keyboard in the shortcut mode.
	 */
	fun resetEmojiMeta() {
		emojiMeta.reset()
		updateStatusIconIfNeeded(true)
	}
}