package io.github.rickybrent.minimal_symlayer_keyboard

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast

/**
 * A transparent activity that shows the system speech recognizer for [VoiceInput] and hands back
 * what was said. A keyboard can't do this itself because it has no way to get a result from an activity.
 */
class VoiceInputActivity : Activity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		// After a rotation or process restart the recognizer is already showing, or is gone.
		if (savedInstanceState != null) return

		val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
			.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
			.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
		try {
			startActivityForResult(intent, REQUEST_RECOGNIZE)
		} catch (e: ActivityNotFoundException) {
			Log.w(TAG, "No speech recognizer", e)
			Toast.makeText(this, "No speech recognizer found.", Toast.LENGTH_SHORT).show()
			finish()
		}
	}

	@Deprecated("Deprecated in Java")
	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (requestCode == REQUEST_RECOGNIZE && resultCode == RESULT_OK) {
			val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
			if (!text.isNullOrBlank()) {
				VoiceInput.deliver(text)
			}
		}
		// The keyboard types the text in when the app that was being typed into has focus again.
		finish()
	}

	companion object {
		private const val TAG = "VoiceInputActivity"
		private const val REQUEST_RECOGNIZE = 1
	}
}
