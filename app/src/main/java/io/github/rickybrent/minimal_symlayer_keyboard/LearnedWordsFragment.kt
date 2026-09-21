package io.github.rickybrent.minimal_symlayer_keyboard

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Lists what the keyboard has learned, so that words and corrections that are unwanted can be forgotten
 * one at a time. The settings screen's filter box searches the list.
 */
class LearnedWordsFragment : Fragment() {
	private sealed class Row {
		class Header(val title: String) : Row()
		class Word(val word: LearnedWord) : Row()
		class Habit(val correction: LearnedCorrection) : Row()
	}

	private var learned = LearnedWords()
	private var query = ""
	private var rows: List<Row> = emptyList()
	private val adapter = RowAdapter()
	private val handler = Handler(Looper.getMainLooper())
	// Saves happen one after another, so that quick changes are never written out of order.
	private val saver = Executors.newSingleThreadExecutor()

	private var summary: TextView? = null

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		return inflater.inflate(R.layout.fragment_learned_words, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		summary = view.findViewById(R.id.learned_summary)
		summary?.text = "Loading..."
		view.findViewById<RecyclerView>(R.id.learned_list).apply {
			layoutManager = LinearLayoutManager(requireContext())
			adapter = this@LearnedWordsFragment.adapter
		}
		val file = learnedFile()
		Thread {
			val fresh = LearnedWords()
			try {
				if (file.exists()) fresh.load(file.readText())
			} catch (e: IOException) {
				// Nothing could be read, so there is nothing to show.
			}
			handler.post {
				if (isAdded) {
					learned = fresh
					update()
				}
			}
		}.start()
	}

	override fun onDestroy() {
		super.onDestroy()
		saver.shutdown()
	}

	/** Show only the words that contain [text], see the filter box of the settings screen. */
	fun filter(text: String) {
		query = text.trim().lowercase()
		update()
	}

	private fun learnedFile() = File(requireContext().filesDir, SuggestionController.LEARNED_WORDS_FILE)

	private fun update() {
		val words = learned.words().filter { query.isEmpty() || it.word.lowercase().contains(query) }
		val habits = learned.corrections().filter {
			query.isEmpty() || it.typo.contains(query) || it.fix.lowercase().contains(query)
		}
		rows = buildList {
			if (habits.isNotEmpty()) {
				add(Row.Header("Corrections it makes for you"))
				habits.forEach { add(Row.Habit(it)) }
			}
			if (words.isNotEmpty()) {
				add(Row.Header("Words"))
				words.forEach { add(Row.Word(it)) }
			}
		}
		summary?.text = when {
			learned.size == 0 && learned.corrections().isEmpty() -> "Nothing has been learned yet. Words are learned as you type."
			rows.isEmpty() -> "Nothing matches."
			else -> "${learned.size} words learned. Tap one to forget it."
		}
		adapter.notifyDataSetChanged()
	}

	private fun confirmForget(title: String, message: String, forget: () -> Boolean) {
		AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
			.setTitle(title)
			.setMessage(message)
			.setPositiveButton("Forget") { _, _ ->
				if (forget()) {
					save()
					update()
					Toast.makeText(requireContext(), "Forgotten", Toast.LENGTH_SHORT).show()
				}
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	/** Write the learned words, and tell the keyboard to read them again. */
	private fun save() {
		val text = learned.serialize()
		val file = learnedFile()
		val dataStore = DeviceProtectedPreferenceDataStore(requireContext())
		saver.execute {
			try {
				val temp = File(file.path + ".tmp")
				temp.writeText(text)
				if (temp.renameTo(file)) dataStore.putLong("pref_learned_words_reload", System.currentTimeMillis())
			} catch (e: IOException) {
				handler.post { context?.let { Toast.makeText(it, "Could not save the change", Toast.LENGTH_LONG).show() } }
			}
		}
	}

	private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
		val title: TextView = view.findViewById(R.id.learned_header)
	}

	private class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
		val text: TextView = view.findViewById(R.id.learned_text)
		val detail: TextView = view.findViewById(R.id.learned_detail)
	}

	private inner class RowAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
		override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) TYPE_HEADER else TYPE_ITEM

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val inflater = LayoutInflater.from(parent.context)
			return if (viewType == TYPE_HEADER) {
				HeaderHolder(inflater.inflate(R.layout.item_learned_header, parent, false))
			} else {
				ItemHolder(inflater.inflate(R.layout.item_learned_word, parent, false))
			}
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (val row = rows[position]) {
				is Row.Header -> (holder as HeaderHolder).title.text = row.title
				is Row.Word -> bind(holder as ItemHolder, row.word.word, "×${row.word.count}") {
					confirmForget(
						"Forget “${row.word.word}”?",
						"It will no longer be suggested, and the words it follows and the words after it are forgotten too."
					) { learned.forgetWord(row.word.word) }
				}
				is Row.Habit -> bind(holder as ItemHolder, "${row.correction.typo} → ${row.correction.fix}", "×${row.correction.count}") {
					confirmForget(
						"Stop changing “${row.correction.typo}”?",
						"It will be left as you type it, unless the spell checker says otherwise."
					) { learned.forgetCorrection(row.correction.typo) }
				}
			}
		}

		private fun bind(holder: ItemHolder, text: String, detail: String, onClick: () -> Unit) {
			holder.text.text = text
			holder.detail.text = detail
			holder.itemView.setOnClickListener { onClick() }
		}

		override fun getItemCount() = rows.size
	}

	private companion object {
		const val TYPE_HEADER = 0
		const val TYPE_ITEM = 1
	}
}
