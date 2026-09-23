package io.github.rickybrent.minimal_symlayer_keyboard

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.os.Handler
import android.os.Looper
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import android.widget.Toast
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.TwoStatePreference

class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

	private lateinit var settingsFilter: EditText

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.settings_activity)
		setSupportActionBar(findViewById(R.id.toolbar))
		supportActionBar?.setDisplayShowHomeEnabled(false)
		supportActionBar?.setIcon(R.mipmap.ic_launcher)
		supportActionBar?.setDisplayHomeAsUpEnabled(false)

		if (savedInstanceState == null) {
			supportFragmentManager
				.beginTransaction()
				.replace(R.id.settings, SettingsFragment())
				.commit()
		}

		if (!isImeEnabled()) {
			showEnableImeDialog()
		}

		findViewById<ImageView>(R.id.keyboard_switcher).setOnClickListener {
			val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
			imm.showInputMethodPicker()
		}

		settingsFilter = findViewById(R.id.settings_filter)
		val filterActionButton = findViewById<ImageView>(R.id.filter_action_button)
		settingsFilter.hint = "Filter settings..."

		settingsFilter.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
				val fragment = supportFragmentManager.findFragmentById(R.id.settings) as? SettingsFragment
				fragment?.filterPreferences(s.toString())
				(supportFragmentManager.findFragmentById(R.id.settings) as? LearnedWordsFragment)?.filter(s.toString())
				if (s.isNullOrEmpty()) {
					filterActionButton.setImageResource(R.drawable.ic_menu_back)
				} else {
					filterActionButton.setImageResource(R.drawable.ic_clear_text)
				}
			}

			override fun afterTextChanged(s: Editable?) {}
		})

		filterActionButton.setOnClickListener {
			if (settingsFilter.text.isEmpty()) {
				onBackPressedDispatcher.onBackPressed()
			} else {
				settingsFilter.text.clear()
			}
		}
	}

	override fun onBackPressed() {
		if (settingsFilter.text.isNotEmpty()) {
			settingsFilter.text.clear()
		} else {
			super.onBackPressed()
		}
	}

	private fun isImeEnabled(): Boolean {
		val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
		val enabledImes = imm.enabledInputMethodList
		for (ime in enabledImes) {
			if (ime.packageName == packageName) {
				return true
			}
		}
		return false
	}

	private fun showEnableImeDialog() {
		AlertDialog.Builder(this, R.style.AlertDialogTheme)
			.setTitle("Enable Keyboard")
			.setMessage("${getString(R.string.app_name)} is not enabled. Please enable it in the settings to use it.")
			.setPositiveButton("Enable") { _, _ ->
				val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
				startActivity(intent)
			}
			.setNegativeButton("Cancel", null)
			.show()
	}

	override fun onSupportNavigateUp(): Boolean {
		if (supportFragmentManager.popBackStackImmediate()) {
			return true
		}
		return super.onSupportNavigateUp()
	}

	override fun onPreferenceStartFragment(
		caller: PreferenceFragmentCompat,
		pref: Preference
	): Boolean {
		// Instantiate the new Fragment
		val args = pref.extras
		val fragment = supportFragmentManager.fragmentFactory.instantiate(
			classLoader,
			pref.fragment!!
		)
		fragment.arguments = args
		fragment.setTargetFragment(caller, 0)
		// Replace the existing Fragment with the new one
		supportFragmentManager.beginTransaction()
			.replace(R.id.settings, fragment)
			.addToBackStack(null)
			.commit()
		return true
	}

	class SettingsFragment : PreferenceFragmentCompat() {
		private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
			if (uri != null) importText(uri)
		}

		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
			preferenceManager.preferenceDataStore = DeviceProtectedPreferenceDataStore(requireContext())
			setPreferencesFromResource(R.xml.preferences, rootKey)
			val context = activity
			if (context != null) {
				findPreference<Preference>("pref_test_spellcheck")?.setOnPreferenceClickListener {
					testSpellChecker()
					true
				}
				findPreference<EditTextPreference>("pref_shortcuts")?.setOnBindEditTextListener { editText ->
					editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
					editText.isSingleLine = false
					editText.minLines = 5
					editText.hint = "omw = on my way"
				}
				findPreference<Preference>("pref_import_text")?.setOnPreferenceClickListener {
					importLauncher.launch(arrayOf("text/*"))
					true
				}
				findPreference<Preference>("pref_clear_learned_words")?.setOnPreferenceClickListener {
					AlertDialog.Builder(context, R.style.AlertDialogTheme)
						.setTitle("Clear learned words")
						.setMessage("Forget every word that was learned from typing?")
						.setPositiveButton(android.R.string.ok) { _, _ ->
							java.io.File(context.filesDir, SuggestionController.LEARNED_WORDS_FILE).delete()
							// Tells the keyboard to forget the words it has in memory.
							preferenceManager.preferenceDataStore?.putLong("pref_learned_words_reset", System.currentTimeMillis())
							Toast.makeText(context, "Learned words cleared", Toast.LENGTH_SHORT).show()
						}
						.setNegativeButton(android.R.string.no, null)
						.show()
					true
				}
				findPreference<Preference>("Reset")?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
					AlertDialog.Builder(context, R.style.AlertDialogTheme)
						.setTitle("Reset settings")
						.setMessage("Do you really want to reset all the settings to their default value?")
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setPositiveButton(android.R.string.yes, DialogInterface.OnClickListener { dialog, which ->
							val sharedPreferences =
								context.createDeviceProtectedStorageContext().getSharedPreferences(
									"${context.packageName}_preferences",
									Context.MODE_PRIVATE
								)
							sharedPreferences.edit { clear() }
							setPreferencesFromResource(R.xml.preferences, rootKey)
						})
						.setNegativeButton(android.R.string.no, null)
						.show()
					true
				}
			}
		}

		/**
		 * Ask the spell checker about a few misspelled words and show what it says and what auto-correct would
		 * do about it, to find out why typos are not being fixed.
		 */
		private fun testSpellChecker() {
			val context = requireContext()
			val words = listOf("teh", "recieve", "definately", "wierd", "lovehahaha", "hello")
			val answers = arrayOfNulls<SpellResult>(words.size)
			val level = AutoCorrectLevel.fromPreference(preferenceManager.preferenceDataStore?.getString("pref_autocorrect", "medium"))
			// These test words are English, so the English list is what they are tested against.
			val dictionary = try {
				BaseDictionary.parse(context.resources.openRawResource(R.raw.common_words_en).bufferedReader().use { it.readText() })
			} catch (e: java.io.IOException) {
				BaseDictionary(emptyList())
			}
			val handler = Handler(Looper.getMainLooper())
			var session: SpellCheckerSession? = null
			var shown = false
			var next = 0

			fun report() {
				if (shown) return
				shown = true
				session?.close()
				val version = try {
					context.packageManager.getPackageInfo(context.packageName, 0).versionName
				} catch (e: Exception) {
					"?"
				}
				val text = StringBuilder("${getString(R.string.app_name)} $version\nAuto-correct: ${level.preferenceValue}\n")
				text.append(if (session == null) "Spell checker: none is turned on\n" else "Spell checker: turned on\n")
				for ((i, word) in words.withIndex()) {
					val answer = answers[i]
					text.append("\n").append(word).append('\n')
					if (session != null) {
						text.append("  spell checker: ").append(
							when {
								answer == null -> "no answer"
								answer.isTypo -> "typo, suggests " + answer.corrections.take(3).joinToString(", ").ifEmpty { "nothing" }
								else -> "fine"
							}
						).append('\n')
					}
					// Without a spell checker the common words stand in for it.
					val spell = if (session != null) answer else AutoCorrect.builtInSpell(word, dictionary, false)
					val fix = if (spell != null && spell.isTypo) {
						Corrector.choose(
							word, level, true, isKnown = false, isCommon = dictionary.contains(word),
							system = spell.corrections, dictionary = dictionary, learned = null
						)
					} else {
						null
					}
					val pieces = if (dictionary.contains(word)) null else Spacing.split(word) { dictionary.rank(it) }
					text.append("  auto-correct: ").append(
						when {
							pieces != null -> "splits it into " + pieces.joinToString(" ")
							fix != null -> "changes it to $fix"
							else -> "leaves it alone"
						}
					).append('\n')
				}
				AlertDialog.Builder(context, R.style.AlertDialogTheme)
					.setTitle("Spell checker test")
					.setMessage(text.toString().trimEnd())
					.setPositiveButton(android.R.string.ok, null)
					.show()
			}

			fun ask(i: Int) {
				try {
					@Suppress("DEPRECATION")
					session?.getSuggestions(TextInfo(words[i], i, i), 5)
				} catch (e: Exception) {
					report()
				}
			}

			val listener = object : SpellCheckerSession.SpellCheckerSessionListener {
				override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
					val info = results?.firstOrNull()
					if (info != null && next < words.size) {
						val corrections = (0 until info.suggestionsCount).mapNotNull { info.getSuggestionAt(it) }.filter { it.isNotBlank() }
						val inDictionary = info.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY != 0
						val typo = info.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO != 0
						answers[next] = SpellResult(typo || (!inDictionary && corrections.isNotEmpty()), corrections)
					}
					next++
					if (next < words.size) ask(next) else report()
				}

				override fun onGetSentenceSuggestions(results: Array<android.view.textservice.SentenceSuggestionsInfo>?) {}
			}

			val manager = context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
			session = try {
				manager?.newSpellCheckerSession(null, Locale.getDefault(), listener, true)
			} catch (e: Exception) {
				null
			}
			if (session == null) {
				report()
			} else {
				Toast.makeText(context, "Asking the spell checker...", Toast.LENGTH_SHORT).show()
				ask(0)
				// Don't wait for ever if it does not answer.
				handler.postDelayed({ report() }, 6000)
			}
		}

		/**
		 * Learn the words in a text file, on top of the ones already learned, and tell the keyboard to
		 * read them again.
		 */
		private fun importText(uri: Uri) {
			val context = requireContext().applicationContext
			val dataStore = preferenceManager.preferenceDataStore
			val handler = Handler(Looper.getMainLooper())
			Toast.makeText(context, "Learning from the file...", Toast.LENGTH_SHORT).show()
			Thread {
				val message = try {
					val file = java.io.File(context.filesDir, SuggestionController.LEARNED_WORDS_FILE)
					val words = LearnedWords()
					if (file.exists()) words.load(file.readText())
					val wordsBefore = words.size
					val pairsBefore = words.pairSize

					val text = StringBuilder()
					context.contentResolver.openInputStream(uri)?.reader(Charsets.UTF_8)?.use { reader ->
						val buffer = CharArray(8192)
						while (text.length < MAX_IMPORT_CHARS) {
							val read = reader.read(buffer)
							if (read < 0) break
							text.append(buffer, 0, read)
						}
					}
					val truncated = text.length >= MAX_IMPORT_CHARS
					val read = words.learnText(text)

					val temp = java.io.File(file.path + ".tmp")
					temp.writeText(words.serialize())
					check(temp.renameTo(file)) { "Could not save the learned words" }
					dataStore?.putLong("pref_learned_words_reload", System.currentTimeMillis())
					"Read $read words: ${words.size - wordsBefore} new words and ${words.pairSize - pairsBefore} new word pairs" +
						if (truncated) " (only the first ${MAX_IMPORT_CHARS / 1_000_000} MB was used)" else ""
				} catch (e: Exception) {
					"Could not import the file: ${e.message ?: e.javaClass.simpleName}"
				}
				handler.post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
			}.start()
		}

		fun filterPreferences(query: String?) {
			val preferenceScreen = preferenceScreen
			val lowerCaseQuery = query?.lowercase()?.trim()

			for (i in 0 until preferenceScreen.preferenceCount) {
				val preference = preferenceScreen.getPreference(i)
				if (preference is PreferenceGroup) {
					filterPreferenceGroup(preference, lowerCaseQuery)
				} else {
					filterPreference(preference, lowerCaseQuery)
				}
			}
		}

		private fun filterPreference(preference: Preference, query: String?): Boolean {
			val title = preference.title.toString().lowercase()
			val summary = preference.summary?.toString()?.lowercase() ?: ""
			val visible = query.isNullOrEmpty() || title.contains(query) || summary.contains(query)
			preference.isVisible = visible
			return visible
		}

		private fun filterPreferenceGroup(preferenceGroup: PreferenceGroup, query: String?): Boolean {
			var visible = false
			for (i in 0 until preferenceGroup.preferenceCount) {
				val preference = preferenceGroup.getPreference(i)
				if (preference is PreferenceGroup) {
					if (filterPreferenceGroup(preference, query)) {
						visible = true
					}
				} else {
					if (filterPreference(preference, query)) {
						visible = true
					}
				}
			}
			preferenceGroup.isVisible = visible
			return visible
		}
	}

	class AboutFragment : PreferenceFragmentCompat() {
		data class LicenseInfo(
			val componentName: String,
			val licenseName: String,
			val upstream: Uri,
			val resourceId: Int
		)

		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
			setPreferencesFromResource(R.xml.preferences_about, rootKey)

			try {
				val version = requireActivity().packageManager.getPackageInfo(
					requireActivity().packageName,
					0
				).versionName
				findPreference<Preference>("about_version")?.summary = "Version $version"
			} catch (e: Exception) {
				findPreference<Preference>("about_version")?.summary = "Version not available"
			}

			findPreference<Preference>("about_main_license")?.setOnPreferenceClickListener {
				showLicenseTextDialog("Application License", R.raw.license_gpl)
				true
			}

			findPreference<Preference>("about_third_party")?.setOnPreferenceClickListener {
				showThirdPartyLicenseList()
				true
			}

			findPreference<Preference>("about_github")?.setOnPreferenceClickListener {
				val intent = Intent(
					Intent.ACTION_VIEW,
					"https://github.com/rickybrent/minimal-symlayer-keyboard".toUri()
				)
				startActivity(intent)
				true
			}
		}

		private fun showThirdPartyLicenseList() {
			val licenses = listOf(
				LicenseInfo(
					"all_emojis.txt",
					"Unicode License",
					"https://github.com/Mange/emoji-data".toUri(),
					R.raw.license_unicode
				),
				LicenseInfo(
					"common_words_en.txt",
					"CC BY-SA 4.0",
					"https://github.com/hermitdave/FrequencyWords".toUri(),
					R.raw.license_common_words_en
				),
				LicenseInfo(
					"common_words_es.txt",
					"CC BY-SA 4.0",
					"https://github.com/hermitdave/FrequencyWords".toUri(),
					R.raw.license_common_words_es
				),
				LicenseInfo(
					"common_words_fr.txt",
					"CC BY-SA 4.0",
					"https://github.com/hermitdave/FrequencyWords".toUri(),
					R.raw.license_common_words_fr
				),
				LicenseInfo(
					"Material Symbols Icons",
					"Apache License v2.0",
					"https://fonts.google.com/icons".toUri(),
					R.raw.license_apache2
				)
				// Add other licenses here, for example:
				// LicenseInfo("Custom Font Name", "SIL Open Font License", R.raw.license_sil_ofl)
			)

			val licenseDisplayNames =
				licenses.map { "${it.componentName} - ${it.licenseName}" }.toTypedArray()

			AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
				.setTitle("Third-Party Licenses")
				.setItems(licenseDisplayNames) { _, which ->
					val selectedLicense = licenses[which]
					showLicenseTextDialog(selectedLicense.componentName, selectedLicense.resourceId)
				}
				.setPositiveButton(android.R.string.ok, null)
				.show()
		}

		private fun showLicenseTextDialog(title: String, resourceId: Int) {
			val licenseText = try {
				resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
			} catch (e: Exception) {
				"Could not load license."
			}

			AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
				.setTitle(title)
				.setMessage(licenseText)
				.setPositiveButton(android.R.string.ok, null)
				.show()
		}
	}
}

// The most that is read from a file to learn from, in characters.
private const val MAX_IMPORT_CHARS = 8_000_000

class DeviceProtectedPreferenceDataStore(context: Context) : PreferenceDataStore() {
	private val sharedPreferences by lazy {
		val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			context.createDeviceProtectedStorageContext()
		} else {
			context
		}
		storageContext.getSharedPreferences(
			"${context.packageName}_preferences",
			Context.MODE_PRIVATE
		)
	}

	override fun putString(key: String?, value: String?) {
		sharedPreferences.edit { putString(key, value) }
	}

	override fun getString(key: String?, defValue: String?): String? {
		return sharedPreferences.getString(key, defValue)
	}

	override fun putBoolean(key: String?, value: Boolean) {
		sharedPreferences.edit { putBoolean(key, value) }
	}

	override fun getBoolean(key: String?, defValue: Boolean): Boolean {
		return sharedPreferences.getBoolean(key, defValue)
	}

	override fun putInt(key: String?, value: Int) {
		sharedPreferences.edit { putInt(key, value) }
	}

	override fun getInt(key: String?, defValue: Int): Int {
		return sharedPreferences.getInt(key, defValue)
	}

	override fun putLong(key: String?, value: Long) {
		sharedPreferences.edit { putLong(key, value) }
	}

	override fun getLong(key: String?, defValue: Long): Long {
		return sharedPreferences.getLong(key, defValue)
	}
}