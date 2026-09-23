package io.github.rickybrent.minimal_symlayer_keyboard

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.text.InputType
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Helps with the word being typed, and with the next one, in the toolbar, and fixes typos as they are made.
 *
 * Corrections come from the spell checker chosen in the system settings. Completions come from the words
 * the user has typed before (see [LearnedWords]) and then from a built in list of common words in
 * [dictionaryLanguage] (see [BaseDictionary]). Next words come from what the user has typed after the words before, and at the
 * start of a sentence from how the user starts sentences, then from built in guesses (see [NextWordHints]).
 * Typos are fixed when the word is finished, as far as [autoCorrectLevel] allows, and so are the spaces that
 * were left out, words that ran together, shortcuts, capitals and grammar. Everything that is learned is stored
 * on the device only.
 */
class SuggestionController(
	private val service: InputMethodService,
	/** Called after a suggestion is applied. */
	private val onApplied: () -> Unit
) {
	/** Whether the user turned suggestions on. Nothing is shown while this is false. */
	var enabled = false
		set(value) {
			if (field == value) return
			field = value
			if (!isActive()) reset()
		}

	/** How readily typos are fixed without asking. */
	var autoCorrectLevel = AutoCorrectLevel.OFF
		set(value) {
			if (field == value) return
			field = value
			if (!isActive()) reset()
		}

	/** How much grammar and punctuation is fixed as it is typed. */
	var grammarLevel = GrammarLevel.OFF
		set(value) {
			if (field == value) return
			field = value
			if (!isActive()) reset()
		}

	/** Whether a space is put in where punctuation was typed right up against the next word ("hello.world"). */
	var autoSpace = true

	/** Whether a word that is two or three words run together is split ("lovehahaha"). */
	var splitWords = true

	/** Whether a word that starts a sentence gets a capital (where the field asks for capitals at all). */
	var capitalizeSentences = true

	/** Whether the last word is fixed when Enter is pressed, see [fixBeforeEnter]. */
	var fixOnEnter = true

	/** The shortcuts and the words that the user added by hand. */
	var personal: PersonalDictionary = PersonalDictionary.EMPTY
		set(value) {
			field = value
			if (!isActive()) reset()
		}

	/** Whether words are learned as they are typed. */
	var learnWords = true

	/** Whether to complete words with the built in list of common words. */
	var useCommonWords = true

	/** Which language the common words, and the built in spell checker, are in: "en", "es" or "fr". */
	var dictionaryLanguage = "en"

	private val handler = Handler(Looper.getMainLooper())
	private val learned = LearnedWords()
	private var learnedLoaded = false
	private val fixer = TextFixer(learned, dictionary = { commonWords() }, hints = { hints() })

	private var views: List<TextView> = emptyList()
	private var shown: List<Suggestion> = emptyList()

	// The words that the suggestions that are shown follow, for learning from them and for forgetting them.
	private var contextPrev = ""
	private var contextBefore = ""

	private var editorAllows = false
	private var editorLearns = false
	private var editorCapitalizes = false

	private var session: SpellCheckerSession? = null
	private var noSpellChecker = false
	private var requestId = 0

	// The word before the cursor, what the spell checker said about it, and where the cursor was.
	private var word = ""
	private var spellResult: SpellResult? = null
	private var lastCursor = -1

	/** A finished word that is waiting for the spell checker to say if it is a typo. */
	private class Pending(val word: String, val boundary: Char, val textBefore: String, val id: Int)

	private var pending: Pending? = null
	private var pendingCounter = 0

	/**
	 * The last fix, so that it can be undone: the text that was there, and what it was changed into (both end
	 * with the character that finished the word), and the typo if a misspelled word was fixed.
	 */
	private class Correction(val original: String, val replacement: String, val typo: String?)

	private var lastCorrection: Correction? = null

	/** Text that a fix was undone for, which is then left alone until the next field. */
	private val ignoredFixes = HashSet<String>()

	private val spellListener = object : SpellCheckerSession.SpellCheckerSessionListener {
		override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
			val info = results?.firstOrNull() ?: return
			// The framework echoes back the sequence number of the request, if it sets it at all.
			val waiting = pending
			if (waiting != null && (info.sequence == waiting.id || (info.sequence == 0 && word.isEmpty()))) {
				pending = null
				service.currentInputConnection?.let {
					completeWord(it, waiting.textBefore, waiting.boundary, waiting.word, toSpellResult(info), fromSystem = true)
				}
				return
			}
			if (info.sequence != 0 && info.sequence != requestId) return
			if (word.isEmpty()) return
			spellResult = toSpellResult(info)
			display(suggestionsForWord(service.currentInputConnection?.getTextBeforeCursor(LOOKBEHIND, 0) ?: ""))
		}

		override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {}
	}

	private val spellRunnable = Runnable { requestSpellCheck() }

	/** Use these views (one per suggestion slot) to display the suggestions. */
	fun setViews(views: List<TextView>) {
		this.views = views
		views.forEachIndexed { index, view ->
			view.setOnClickListener { shown.getOrNull(index)?.let { apply(it) } }
			// Holding a suggestion forgets it, for when the keyboard has learned something unwanted.
			view.setOnLongClickListener { shown.getOrNull(index)?.let { forget(it) } != null }
		}
		show(shown, force = true)
	}

	/**
	 * A field was focused. This is called for every field, unlike [onStartInputView] which only happens when
	 * the keyboard's window is shown, so that typos are fixed either way.
	 */
	fun onStartInput(info: EditorInfo?) {
		reset()
		noSpellChecker = false
		ignoredFixes.clear()
		editorAllows = info != null && suggestionsAllowed(info)
		editorLearns = info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0
		editorCapitalizes = info != null && (info.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0
	}

	fun onStartInputView(info: EditorInfo?) {
		onStartInput(info)
		refresh(-1)
	}

	/** The field lost focus. */
	fun onFinishInput() {
		reset()
		closeSession()
		save()
	}

	fun onFinishInputView() = onFinishInput()

	fun onDestroy() {
		handler.removeCallbacks(spellRunnable)
		closeSession()
		save()
	}

	/**
	 * Called when the text or the cursor in the editor changed.
	 */
	fun onSelectionUpdate(newSelStart: Int, newSelEnd: Int) {
		if (!isActive()) return
		if (newSelStart != newSelEnd) {
			reset()
			return
		}
		refresh(newSelStart)
	}

	/**
	 * Apply the suggestion shown in the given slot.
	 * @return true if there was a suggestion in that slot.
	 */
	fun applySlot(slot: Int): Boolean {
		val suggestion = shown.getOrNull(slot) ?: return false
		apply(suggestion)
		return true
	}

	/**
	 * Undo the fix that was just made, if that is what the user wants by pressing Backspace: the words go back
	 * to how they were typed, and are not fixed again.
	 * @return true if a fix was undone, so that the key press is used up.
	 */
	fun undoAutoCorrect(): Boolean {
		val correction = lastCorrection ?: return false
		val ic = service.currentInputConnection ?: return false
		val before = ic.getTextBeforeCursor(correction.replacement.length + 2, 0)
		lastCorrection = null
		if (before == null || !before.endsWith(correction.replacement)) return false

		ic.beginBatchEdit()
		ic.deleteSurroundingText(correction.replacement.length, 0)
		// Back to just after the word, without the character that ended it.
		ic.commitText(correction.original.dropLast(1), 1)
		ic.endBatchEdit()
		// The user does mean it: stop fixing this, and stop the habit of doing so.
		ignoredFixes.add(correction.original.lowercase())
		correction.typo?.let {
			learned.forgetCorrection(it)
			if (learnAllowed()) learned.learn(it, TYPED_WORD_WEIGHT)
		}
		return true
	}

	/** Forget every learned word, in memory and on disk. */
	fun clearLearnedWords() {
		learned.clear()
		learned.markSaved()
		learnedFile()?.delete()
		refresh(-1)
	}

	/** Read the learned words from disk again, after they were changed by an import. */
	fun reloadLearnedWords() {
		learned.clear()
		learnedLoaded = false
		ensureLearnedLoaded()
		learned.markSaved()
		refresh(-1)
	}

	private fun isActive() = (enabled || fixesText()) && editorAllows

	/** @return true if anything is turned on that changes what was typed. */
	private fun fixesText() = autoCorrectLevel != AutoCorrectLevel.OFF || grammarLevel != GrammarLevel.OFF ||
		autoSpace || splitWords || !personal.isEmpty

	/** @return true if a word that starts a sentence is to get a capital right now. */
	private fun sentenceCaps() = capitalizeSentences && editorCapitalizes

	private fun isKnownWord(word: String) = learned.isKnown(word) || personal.contains(word)

	private fun settings() = TextFixer.Settings(
		autoCorrectLevel, grammarLevel, autoSpace, splitWords, sentenceCaps(), personal
	)

	private fun learnAllowed() = learnWords && editorLearns

	private fun suggestionsAllowed(info: EditorInfo): Boolean {
		if (info.inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
		if (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
		return canUseSuggestions(info)
	}

	private fun reset() {
		handler.removeCallbacks(spellRunnable)
		word = ""
		spellResult = null
		lastCursor = -1
		pending = null
		lastCorrection = null
		show(emptyList())
	}

	private fun refresh(cursor: Int) {
		if (!isActive()) {
			show(emptyList())
			return
		}
		ensureLearnedLoaded()

		val ic = service.currentInputConnection
		val before = ic?.getTextBeforeCursor(LOOKBEHIND, 0)
		if (ic == null || before == null) {
			reset()
			return
		}
		val after = ic.getTextAfterCursor(1, 0)
		// Don't make suggestions while the cursor is in the middle of a word.
		val atWordEnd = after.isNullOrEmpty() || !WordUtils.isWordChar(after[0])
		val newWord = if (atWordEnd) {
			WordUtils.trailingWord(before).takeIf { it.length <= MAX_WORD_LENGTH } ?: ""
		} else {
			""
		}

		if (word.isNotEmpty() && newWord.isEmpty() && cursor == lastCursor + 1 &&
			before.isNotEmpty() && !WordUtils.isWordChar(before.last()) &&
			WordUtils.trailingWord(before.subSequence(0, before.length - 1)) == word
		) {
			// A single character that ended the word was typed.
			finishWord(ic, before.toString(), word, spellResult)
		} else if (cursor == lastCursor + 1) {
			removeSpaceBeforePunctuation(ic, before)
		}
		lastCursor = cursor
		// Carrying on typing means the last fix can no longer be undone with Backspace.
		if (newWord.isNotEmpty()) lastCorrection = null

		if (newWord != word) {
			word = newWord
			spellResult = null
			requestId++
			handler.removeCallbacks(spellRunnable)
			if (word.isNotEmpty()) {
				handler.postDelayed(spellRunnable, SPELL_CHECK_DELAY_MS)
			}
		}
		display(currentSuggestions(before, atWordEnd))
	}

	// --- Finishing a word ---

	/**
	 * A word was finished by typing a character. Fix it if it is a typo that is to be fixed, and learn from it.
	 * @param textBefore The text before the cursor, which ends with the word and the character that ended it.
	 */
	private fun finishWord(ic: InputConnection, textBefore: String, word: String, spell: SpellResult?) {
		val boundary = textBefore.last()
		val textBeforeBoundary = textBefore.substring(0, textBefore.length - 1)
		val habit = autoCorrectLevel != AutoCorrectLevel.OFF && learned.correctionFor(word) != null
		if (spell == null && !habit && wantsSpellResult() && ensureSession() == null) {
			// No spell checker is turned on, so make do with the common words.
			completeWord(ic, textBeforeBoundary + boundary, boundary, word, builtInSpell(word), fromSystem = false)
			return
		}
		// If the spell checker has not answered yet, wait for it rather than learn a typo or miss one.
		if (spell == null && !habit && session != null && wantsSpellResult()) {
			val id = PENDING_ID_BASE + ++pendingCounter
			pending = Pending(word, boundary, textBeforeBoundary + boundary, id)
			try {
				@Suppress("DEPRECATION")
				session?.getSuggestions(TextInfo(word, id, id), MAX_CORRECTIONS)
			} catch (e: Exception) {
				Log.w(TAG, "Spell check request failed", e)
				pending = null
				completeWord(ic, textBeforeBoundary + boundary, boundary, word, null, fromSystem = false)
			}
			return
		}
		completeWord(ic, textBeforeBoundary + boundary, boundary, word, spell, fromSystem = spell != null)
	}

	/** @return true if it is worth waiting for the spell checker: something depends on its answer. */
	private fun wantsSpellResult() = autoCorrectLevel != AutoCorrectLevel.OFF || splitWords || learnAllowed()

	/**
	 * Fix the word that is right before the cursor when Enter is pressed. A word is otherwise only fixed when
	 * something is typed after it, which never happens to the last word of a message that is sent with Enter.
	 * The spell checker has usually had its say by then, and the common words step in when it has not.
	 * @return true if the text was changed. The caller then has to send the Enter key itself, after the change.
	 */
	fun fixBeforeEnter(): Boolean {
		if (!fixOnEnter || !isActive() || !fixesText()) return false
		val ic = service.currentInputConnection ?: return false
		val before = ic.getTextBeforeCursor(LOOKBEHIND, 0)?.toString() ?: return false
		if (before.isEmpty() || !WordUtils.isWordChar(before.last())) return false
		val after = ic.getTextAfterCursor(1, 0)
		if (!after.isNullOrEmpty() && WordUtils.isWordChar(after[0])) return false
		val finished = WordUtils.trailingWord(before)
		if (finished.isEmpty() || finished.length > MAX_WORD_LENGTH) return false
		ensureLearnedLoaded()
		val fromSystem = if (finished == word) spellResult else null
		val spell = fromSystem ?: builtInSpell(finished)
		return completeWord(ic, before, ' ', finished, spell, fromSystem = fromSystem != null, boundaryTyped = false)
	}

	/**
	 * Fix [word], and what comes before it, if that is called for, otherwise learn it, now that the spell
	 * checker's opinion is in.
	 * @param textBefore The text that ended with the word and, if [boundaryTyped], the character [boundary] that ended it.
	 * @param fromSystem Whether [spell] is the system spell checker's opinion and not a guess from the common words.
	 * @return true if the text was changed.
	 */
	private fun completeWord(
		ic: InputConnection,
		textBefore: String,
		boundary: Char,
		word: String,
		spell: SpellResult?,
		fromSystem: Boolean,
		boundaryTyped: Boolean = true
	): Boolean {
		val text = if (boundaryTyped) textBefore.substring(0, textBefore.length - 1) else textBefore
		val typo = spell?.isTypo == true && !isKnownWord(word)
		val fixed = if (boundary !in FIX_BOUNDARIES) {
			null
		} else {
			fixer.fix(settings(), text, boundary, word, spell, systemSaysFine = fromSystem && spell?.isTypo == false, typo = typo, neverAfter = NEVER_FIX_AFTER)
		}
		if (fixed != null && fixed.text != text) {
			// Replace whole words, so that undoing it and ignoring it later are about the same text.
			var start = 0
			while (start < text.length && start < fixed.text.length && text[start] == fixed.text[start]) start++
			while (start > 0 && WordUtils.isWordChar(text[start - 1])) start--
			val suffix = if (boundaryTyped) boundary.toString() else ""
			val original = text.substring(start) + suffix
			val replacement = fixed.text.substring(start) + suffix
			// A fix before Enter is one that a space would have made, which is how an undone fix is remembered.
			if (ignoredFixes.contains(original.lowercase() + if (boundaryTyped) "" else " ")) {
				if (typo) countUnrecognized(word) else learnWord(word, text, WordUtils.isSentenceStart(text.substring(0, text.length - word.length)))
				return false
			}
			if (applyFix(ic, original, replacement, fixed.typo, undoable = boundaryTyped)) {
				if (learnAllowed() && fixed.typo != null && fixed.correction != null) learned.learnCorrection(fixed.typo, fixed.correction)
				val last = WordUtils.trailingWord(fixed.text)
				if (fixed.learn && last.isNotEmpty()) {
					learnWord(last, fixed.text, WordUtils.isSentenceStart(fixed.text.substring(0, fixed.text.length - last.length)))
				}
				return true
			}
			return false
		}
		if (typo) countUnrecognized(word) else learnWord(word, text, WordUtils.isSentenceStart(text.substring(0, text.length - word.length)))
		return false
	}

	/** A comma, full stop, question mark or exclamation mark typed after a space that follows a word takes that space away. */
	private fun removeSpaceBeforePunctuation(ic: InputConnection, before: CharSequence) {
		if (grammarLevel == GrammarLevel.OFF || before.length < 3) return
		val mark = before.last()
		if (mark !in SPACELESS_MARKS || before[before.length - 2] != ' ' || !before[before.length - 3].isLetterOrDigit()) return
		val original = " $mark"
		if (ignoredFixes.contains(original)) return
		applyFix(ic, original, mark.toString(), null, undoable = true)
	}

	private fun learnWord(word: String, textBeforeBoundary: String, startsSentence: Boolean) {
		if (!learnAllowed()) return
		learned.learn(word)
		val previous = WordUtils.wordsBefore(textBeforeBoundary, 2)
		if (previous.isNotEmpty()) learned.learnPair(previous[0], word)
		if (previous.size > 1) learned.learnTriple(previous[1], previous[0], word)
		if (startsSentence) learned.learnStarter(word)
	}

	/**
	 * Count that [word], which the spell checker does not recognize, was typed again, and learn it on its
	 * own once it has been typed enough times that it is worth trusting, the same as tapping its suggestion
	 * chip would (see [Suggestion.Kind.TYPED] in [apply]), so that a name or made up word that keeps getting
	 * typed stops being flagged even if it is never tapped.
	 */
	private fun countUnrecognized(word: String) {
		if (!learnAllowed()) return
		if (learned.sawUnrecognized(word)) learned.learn(word, TYPED_WORD_WEIGHT)
	}

	/**
	 * Replace [original], which must be right before the cursor, with [replacement].
	 * @param typo The word that was misspelled, if this fixes one.
	 * @param undoable Whether Backspace right after puts [original] back. That is not so when the text was
	 * changed before a key that is about to be pressed, like Enter.
	 * @return false if the text is no longer there, so that a slow spell checker can't change text that moved on.
	 */
	private fun applyFix(ic: InputConnection, original: String, replacement: String, typo: String?, undoable: Boolean): Boolean {
		val before = ic.getTextBeforeCursor(original.length + 2, 0) ?: return false
		if (!before.endsWith(original)) return false
		ic.beginBatchEdit()
		ic.deleteSurroundingText(original.length, 0)
		ic.commitText(replacement, 1)
		ic.endBatchEdit()
		lastCorrection = if (undoable) Correction(original, replacement, typo) else null
		return true
	}

	// --- What to suggest ---

	/**
	 * @return What to suggest for the word being typed. Without one: at the start of a sentence or in an
	 * empty field the words that start sentences, and behind a space what could come next.
	 */
	private fun currentSuggestions(before: CharSequence, atWordEnd: Boolean): List<Suggestion> {
		return when {
			word.isNotEmpty() -> suggestionsForWord(before)
			!atWordEnd -> emptyList()
			WordUtils.isSentenceStart(before) -> {
				contextPrev = LearnedWords.START
				contextBefore = ""
				SuggestionBuilder.buildStart(learned, hints = hints())
			}
			before.last() == ' ' -> {
				val previous = WordUtils.wordsBefore(before, 2)
				contextPrev = previous.getOrElse(0) { "" }
				contextBefore = previous.getOrElse(1) { "" }
				SuggestionBuilder.buildNext(contextPrev, learned, hints = hints(), before = contextBefore)
			}
			else -> emptyList()
		}
	}

	private fun suggestionsForWord(before: CharSequence): List<Suggestion> {
		contextPrev = word
		contextBefore = WordUtils.wordsBefore(before, 1).getOrElse(0) { "" }
		return SuggestionBuilder.build(
			word, spellResult, learned, offerTyped = learnAllowed(),
			base = commonWords(), hints = hints(), before = contextBefore, personal = personal
		)
	}

	private fun apply(suggestion: Suggestion) {
		val ic = service.currentInputConnection ?: return
		val before = ic.getTextBeforeCursor(LOOKBEHIND, 0)
		val current = if (before == null) "" else WordUtils.trailingWord(before)
		val nextWord = suggestion.kind == Suggestion.Kind.NEXT_WORD
		val atStart = before != null && (before.isEmpty() || before.last() == ' ' || before.last() == '\n')
		val stale = before == null || current != word ||
			(!nextWord && current.isEmpty()) ||
			(nextWord && suggestion.leadingSpace && current.isEmpty()) ||
			(nextWord && !suggestion.leadingSpace && !(current.isEmpty() && atStart))
		if (stale) {
			// The text changed since the suggestion was made.
			refresh(lastCursor)
			return
		}

		if (nextWord) {
			ic.commitText((if (suggestion.leadingSpace) " " else "") + suggestion.text + " ", 1)
			if (learnAllowed()) learnAccepted(suggestion.text)
			reset()
		} else if (suggestion.kind == Suggestion.Kind.TYPED) {
			// Keep the word as typed, and stop treating it as a typo.
			if (learnAllowed()) learned.learn(word, TYPED_WORD_WEIGHT)
			display(suggestionsForWord(before ?: ""))
		} else {
			ic.beginBatchEdit()
			ic.deleteSurroundingText(current.length, 0)
			ic.commitText(suggestion.text + " ", 1)
			ic.endBatchEdit()
			if (learnAllowed()) {
				learned.learn(suggestion.text, ACCEPTED_WORD_WEIGHT)
				// Picking a correction on purpose is how a habit starts.
				if (suggestion.kind == Suggestion.Kind.CORRECTION) learned.learnCorrection(current, suggestion.text, ACCEPTED_WORD_WEIGHT)
			}
			// The text change is reported through onSelectionUpdate, which starts from a clean slate.
			reset()
		}
		onApplied()
	}

	/** Learn from a next word that was picked, in the context it was offered in. */
	private fun learnAccepted(text: String) {
		learned.learn(text, ACCEPTED_WORD_WEIGHT)
		when {
			contextPrev == LearnedWords.START -> learned.learnStarter(text)
			contextPrev.isNotEmpty() -> {
				learned.learnPair(contextPrev, text)
				if (contextBefore.isNotEmpty()) learned.learnTriple(contextBefore, contextPrev, text)
			}
		}
	}

	/**
	 * Forget what the suggestion is based on: a completion is forgotten as a word, a next word only after
	 * the word it follows. The built in ones can't be forgotten.
	 */
	private fun forget(suggestion: Suggestion) {
		val forgotten = when (suggestion.kind) {
			Suggestion.Kind.COMPLETION -> learned.forgetWord(suggestion.text)
			Suggestion.Kind.NEXT_WORD -> learned.forgetPair(contextPrev, suggestion.text)
			else -> return
		}
		val message = if (forgotten) "Forgot “${suggestion.text}”" else "“${suggestion.text}” is built in"
		Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
		if (forgotten) {
			save()
			refresh(lastCursor)
			onApplied()
		}
	}

	/** Show [suggestions], unless the user has suggestions turned off. */
	private fun display(suggestions: List<Suggestion>) = show(if (enabled) suggestions else emptyList())

	private fun show(suggestions: List<Suggestion>, force: Boolean = false) {
		if (!force && suggestions == shown) return
		shown = suggestions
		views.forEachIndexed { index, view ->
			val suggestion = suggestions.getOrNull(index)
			view.isClickable = suggestion != null
			view.isLongClickable = suggestion != null
			when (suggestion?.kind) {
				Suggestion.Kind.TYPED -> {
					view.text = "“${suggestion.text}”"
					view.setTypeface(null, Typeface.ITALIC)
				}
				Suggestion.Kind.CORRECTION -> {
					view.text = suggestion.text
					view.setTypeface(null, Typeface.BOLD)
				}
				Suggestion.Kind.COMPLETION, Suggestion.Kind.NEXT_WORD -> {
					view.text = suggestion.text
					view.setTypeface(null, Typeface.NORMAL)
				}
				null -> view.text = ""
			}
		}
	}

	// --- Spell checker ---

	private fun requestSpellCheck() {
		if (word.length < WordUtils.MIN_WORD_LENGTH || !isActive()) return
		val session = ensureSession()
		if (session == null) {
			// No spell checker is turned on, so make do with the common words.
			builtInSpell(word)?.let {
				spellResult = it
				display(suggestionsForWord(service.currentInputConnection?.getTextBeforeCursor(LOOKBEHIND, 0) ?: ""))
			}
			return
		}
		try {
			@Suppress("DEPRECATION")
			session.getSuggestions(TextInfo(word, requestId, requestId), MAX_CORRECTIONS)
		} catch (e: Exception) {
			Log.w(TAG, "Spell check request failed", e)
			closeSession()
		}
	}

	private fun ensureSession(): SpellCheckerSession? {
		if (session != null) return session
		if (noSpellChecker) return null
		val manager = service.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
		session = try {
			manager?.newSpellCheckerSession(null, Locale.getDefault(), spellListener, true)
		} catch (e: Exception) {
			Log.w(TAG, "Could not start the spell checker", e)
			null
		}
		if (session == null) {
			// Most likely no spell checker is turned on in the system settings.
			noSpellChecker = true
		}
		return session
	}

	private fun closeSession() {
		try {
			session?.close()
		} catch (e: Exception) {
			Log.w(TAG, "Could not close the spell checker", e)
		}
		session = null
	}

	private fun toSpellResult(info: SuggestionsInfo): SpellResult {
		val attributes = info.suggestionsAttributes
		val corrections = (0 until info.suggestionsCount)
			.mapNotNull { info.getSuggestionAt(it) }
			.filter { it.isNotBlank() }
		val inDictionary = attributes and SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY != 0
		val looksLikeTypo = attributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO != 0
		return SpellResult(looksLikeTypo || (!inDictionary && corrections.isNotEmpty()), corrections)
	}

	// --- Built in words ---

	/** @return What the common words make of [word] when there is no spell checker, or null if they are off. */
	private fun builtInSpell(word: String): SpellResult? {
		val dictionary = commonWords() ?: return null
		// At least one letter change, even with auto-correct off, so a typo is still flagged and offered.
		val maxDistance = maxOf(1, autoCorrectLevel.maxDistance)
		return AutoCorrect.builtInSpell(word, dictionary, wasUsed = learned.count(word) > 0, maxDistance = maxDistance)
	}

	private var commonWords: BaseDictionary? = null
	private var commonWordsLanguage: String? = null
	private var nextWordHints: NextWordHints? = null
	private var nextWordHintsLanguage: String? = null

	/** @return The built in list of common words, or null if it is turned off. It is read when first needed. */
	private fun commonWords(): BaseDictionary? {
		if (!useCommonWords) return null
		if (commonWords == null || commonWordsLanguage != dictionaryLanguage) {
			commonWordsLanguage = dictionaryLanguage
			commonWords = try {
				BaseDictionary.parse(service.resources.openRawResource(commonWordsResource(dictionaryLanguage)).bufferedReader().use { it.readText() })
			} catch (e: IOException) {
				Log.w(TAG, "Could not read the common words", e)
				BaseDictionary(emptyList())
			}
		}
		return commonWords
	}

	/**
	 * @return The built in guesses at the next word, or empty guesses for a language that has none, so
	 * that English predictions are not offered while writing in another language. It is read when first needed.
	 */
	private fun hints(): NextWordHints? {
		if (nextWordHints == null || nextWordHintsLanguage != dictionaryLanguage) {
			nextWordHintsLanguage = dictionaryLanguage
			nextWordHints = if (dictionaryLanguage != "en") {
				NextWordHints.parse("")
			} else {
				try {
					NextWordHints.parse(service.resources.openRawResource(R.raw.next_words).bufferedReader().use { it.readText() })
				} catch (e: IOException) {
					Log.w(TAG, "Could not read the next word hints", e)
					NextWordHints.parse("")
				}
			}
		}
		return nextWordHints
	}

	private fun commonWordsResource(language: String) = when (language) {
		"es" -> R.raw.common_words_es
		"fr" -> R.raw.common_words_fr
		else -> R.raw.common_words_en
	}

	// --- Learned words ---

	private fun learnedFile(): File? {
		val unlocked = service.getSystemService(UserManager::class.java)?.isUserUnlocked == true
		return if (unlocked) File(service.filesDir, LEARNED_WORDS_FILE) else null
	}

	private fun ensureLearnedLoaded() {
		if (learnedLoaded) return
		val file = learnedFile() ?: return
		learnedLoaded = true
		try {
			if (file.exists()) learned.load(file.readText())
		} catch (e: IOException) {
			Log.w(TAG, "Could not read the learned words", e)
		}
	}

	private fun save() {
		if (!learned.isDirty || !learnedLoaded) return
		val file = learnedFile() ?: return
		try {
			val temp = File(file.path + ".tmp")
			temp.writeText(learned.serialize())
			if (temp.renameTo(file)) learned.markSaved()
		} catch (e: IOException) {
			Log.w(TAG, "Could not save the learned words", e)
		}
	}

	companion object {
		private const val TAG = "SuggestionController"
		const val LEARNED_WORDS_FILE = "learned_words.txt"

		/** How much text before the cursor to look at. */
		private const val LOOKBEHIND = 48
		private const val MAX_WORD_LENGTH = 40
		private const val MAX_CORRECTIONS = 5
		private const val SPELL_CHECK_DELAY_MS = 60L

		// Requests for words that are finished are told apart from the ones for the word being typed by this.
		private const val PENDING_ID_BASE = 1_000_000

		// How much each way of adding a word counts towards it being known, see LearnedWords.KNOWN_COUNT.
		private const val ACCEPTED_WORD_WEIGHT = 2
		private const val TYPED_WORD_WEIGHT = 3

		/** A word is only fixed when one of these is typed after it. */
		private const val FIX_BOUNDARIES = " .,!?;:\n"

		/** A space before one of these is taken away. */
		private const val SPACELESS_MARKS = ",.?!"


		/** A word right after one of these is a handle, an address or a file name, not something to fix. */
		private const val NEVER_FIX_AFTER = "@#/\\._-:+=~"
	}
}
