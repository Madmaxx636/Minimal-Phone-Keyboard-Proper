package io.github.rickybrent.minimal_symlayer_keyboard

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.util.Log
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast

/**
 * Starts voice typing, either by switching to a voice keyboard such as Google voice typing, or by
 * showing the system's speech recognizer.
 */
class VoiceInput(private val service: InputMethodService) {
	enum class Engine(val preferenceValue: String) {
		/** Google voice typing if it is turned on as a keyboard, otherwise the speech dialog. */
		GOOGLE("google"),
		/** Any voice keyboard (preferring Google's), otherwise the speech dialog. */
		ANY("any"),
		/** Always the speech dialog. It needs no voice keyboard to be turned on. */
		DIALOG("dialog");

		companion object {
			fun fromPreference(value: String?): Engine = values().firstOrNull { it.preferenceValue == value } ?: GOOGLE
		}
	}

	var engine = Engine.GOOGLE

	fun start() {
		if (engine != Engine.DIALOG && switchToVoiceKeyboard()) {
			return
		}
		if (!startSpeechDialog()) {
			Log.w(TAG, "No voice keyboard or speech recognizer found.")
			Toast.makeText(service, "No voice input found. Install or enable Google voice typing.", Toast.LENGTH_LONG).show()
		}
	}

	/**
	 * Switch to an enabled keyboard that has a voice subtype. Voice keyboards return to the
	 * previous keyboard when they are done.
	 * @return true if the switch was made.
	 */
	private fun switchToVoiceKeyboard(): Boolean {
		val imm = service.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
		val candidates = imm.enabledInputMethodList
			.filter { it.packageName != service.packageName }
			.mapNotNull { method -> voiceSubtype(method)?.let { method to it } }
			.filter { engine != Engine.GOOGLE || it.first.packageName.startsWith(GOOGLE_PACKAGE_PREFIX) }
			.sortedBy { rank(it.first.packageName) }
		val (method, subtype) = candidates.firstOrNull() ?: return false

		return try {
			service.switchInputMethod(method.id, subtype)
			true
		} catch (e: Exception) {
			Log.w(TAG, "Could not switch to ${method.id}", e)
			false
		}
	}

	private fun voiceSubtype(method: InputMethodInfo): InputMethodSubtype? {
		for (i in 0 until method.subtypeCount) {
			val subtype = method.getSubtypeAt(i)
			if (subtype.mode == "voice") return subtype
		}
		return null
	}

	/** Lower is better: Google voice typing first, then other Google keyboards, then the rest. */
	private fun rank(packageName: String): Int = when {
		packageName == GOOGLE_VOICE_PACKAGE -> 0
		packageName.startsWith(GOOGLE_PACKAGE_PREFIX) -> 1
		else -> 2
	}

	/**
	 * Show the system speech recognizer (provided by the Google app on most devices). The text is
	 * typed in once the app being typed into has focus again, see [takePendingText].
	 * @return false if there is no speech recognizer.
	 */
	private fun startSpeechDialog(): Boolean {
		if (Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(service.packageManager) == null) {
			return false
		}
		val intent = Intent(service, VoiceInputActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		return try {
			service.startActivity(intent)
			true
		} catch (e: Exception) {
			Log.w(TAG, "Could not show the speech recognizer", e)
			false
		}
	}

	companion object {
		private const val TAG = "VoiceInput"

		private const val GOOGLE_VOICE_PACKAGE = "com.google.android.googlequicksearchbox"
		private const val GOOGLE_PACKAGE_PREFIX = "com.google."

		// Text that was dictated but not typed in yet is dropped after this long.
		private const val PENDING_MAX_AGE_MS = 30_000L

		private var pendingText: String? = null
		private var pendingTime = 0L

		/** Called by [VoiceInputActivity] with the recognized text. */
		fun deliver(text: String) {
			pendingText = text
			pendingTime = SystemClock.elapsedRealtime()
		}

		/** @return The text that was dictated and has not been typed in yet, if any. */
		fun takePendingText(): String? {
			val text = pendingText
			pendingText = null
			return if (text != null && SystemClock.elapsedRealtime() - pendingTime < PENDING_MAX_AGE_MS) text else null
		}
	}
}
