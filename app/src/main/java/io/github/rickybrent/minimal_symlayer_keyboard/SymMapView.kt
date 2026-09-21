package io.github.rickybrent.minimal_symlayer_keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The popup that opens when Sym is pressed and left alone for a moment. It has two pages that a tap on the tabs
 * switches between: a map of the keyboard as it sits on the phone, with what Alt types written in the top left
 * corner of every key like the printed symbols, and what Sym does in the bottom right corner, and a map of the
 * additional characters of every key.
 */
class SymMapView(context: Context) : LinearLayout(context) {
	private val density = resources.displayMetrics.density
	private val ink = themeColor(android.R.attr.textColorPrimary, Color.BLACK)

	private var keys: List<MapRow> = emptyList()
	private var extras: List<CharacterRow> = emptyList()
	private var showsKeys = true

	private val keysTab = tab("Keys") { show(true) }
	private val extrasTab = tab("Extras") { show(false) }
	private val page = FrameLayout(context)

	init {
		orientation = VERTICAL
		setBackgroundColor(themeColor(android.R.attr.colorBackground, Color.WHITE))
		setPadding(dp(6), dp(4), dp(6), dp(4))
		val tabs = LinearLayout(context)
		tabs.orientation = HORIZONTAL
		tabs.addView(keysTab, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, dp(6), dp(3)) })
		tabs.addView(extrasTab, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, dp(6), dp(3)) })
		addView(tabs)
		addView(page)
	}

	private fun dp(value: Int) = (value * density).toInt()

	private fun themeColor(attribute: Int, fallback: Int): Int {
		val value = TypedValue()
		if (!context.theme.resolveAttribute(attribute, value, true)) return fallback
		return if (value.resourceId != 0) context.getColor(value.resourceId) else value.data
	}

	/** Show these keys and extra characters. Call it again to show something else. The page shown stays. */
	fun setContent(keys: List<MapRow>, extras: List<CharacterRow>) {
		this.keys = keys
		this.extras = extras
		render()
	}

	private fun show(keysPage: Boolean) {
		showsKeys = keysPage
		render()
	}

	private fun tab(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
		text = label
		textSize = 13f
		gravity = Gravity.CENTER
		setPadding(dp(12), dp(4), dp(12), dp(4))
		setOnClickListener { onClick() }
	}

	private fun styleTab(tab: TextView, selected: Boolean) {
		tab.setTextColor(if (selected) Color.WHITE else ink)
		tab.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
		tab.background = GradientDrawable().apply {
			setColor(if (selected) Color.BLACK else Color.TRANSPARENT)
			setStroke(dp(1), ink)
			cornerRadius = dp(6).toFloat()
		}
	}

	private fun render() {
		styleTab(keysTab, showsKeys)
		styleTab(extrasTab, !showsKeys)
		page.removeAllViews()
		page.addView(if (showsKeys) keysPage() else extrasPage())
	}

	private fun note(text: String) = TextView(context).apply {
		this.text = text
		textSize = 11f
		setTextColor(ink)
		setPadding(0, 0, 0, dp(2))
	}

	private fun rowView(margin: Float, cells: List<Pair<View, Float>>): View {
		val row = LinearLayout(context)
		row.orientation = HORIZONTAL
		row.isBaselineAligned = false
		fun spacer() = row.addView(View(context), LayoutParams(0, 0, margin))
		if (margin > 0f) spacer()
		for ((cell, weight) in cells) {
			row.addView(cell, LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
		}
		if (margin > 0f) spacer()
		return row
	}

	private fun text(text: String, size: Float, style: Int = Typeface.NORMAL) = TextView(context).apply {
		this.text = text
		textSize = size
		setTypeface(null, style)
		setTextColor(Color.BLACK)
	}

	// --- Keys ---

	private fun keysPage(): View {
		val view = LinearLayout(context)
		view.orientation = VERTICAL
		view.addView(note("Top left: what Alt and the key type. Bottom right: what Sym and the key do. Hold a key for more, see Extras."))
		for (row in keys) view.addView(rowView(row.margin, row.keys.map { mapKeyView(it) to it.weight }))
		return view
	}

	private fun mapKeyView(key: MapKey): View {
		val view = FrameLayout(context)
		view.setBackgroundResource(R.drawable.kbd_key_background)
		view.minimumHeight = dp(42)
		fun corner(text: String, gravity: Int) {
			if (text.isEmpty()) return
			view.addView(text(text, 10f), FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, gravity).apply {
				setMargins(dp(3), dp(1), dp(3), dp(1))
			})
		}
		corner(key.alt, Gravity.TOP or Gravity.START)
		corner(key.sym, Gravity.BOTTOM or Gravity.END)
		// A letter or a symbol is big, a word like "space" is not.
		val word = key.label.length > 1
		view.addView(
			text(key.label, if (word) 12f else 16f, if (word) Typeface.NORMAL else Typeface.BOLD),
			FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
		)
		return view
	}

	// --- Extras ---

	private fun extrasPage(): View {
		val view = LinearLayout(context)
		view.orientation = VERTICAL
		val showsAccents = extras.any { row -> row.cells.any { it.accents.isNotEmpty() } }
		view.addView(note(
			"Hold a key for the first one and press it again for the next." +
				if (showsAccents) " In italics: accents, by pressing a vowel twice quickly." else ""
		))
		for (row in extras) {
			val cells = row.cells.map { cell ->
				val key = LinearLayout(context)
				key.orientation = VERTICAL
				key.gravity = Gravity.CENTER_HORIZONTAL
				key.setBackgroundResource(R.drawable.kbd_key_background)
				key.setPadding(dp(2), dp(2), dp(2), dp(2))
				fun line(text: String, size: Float, style: Int) {
					if (text.isEmpty()) return
					key.addView(text(text, size, style).apply { gravity = Gravity.CENTER_HORIZONTAL })
				}
				line(cell.label, 13f, Typeface.BOLD)
				line(cell.extras.joinToString(" "), 12f, Typeface.NORMAL)
				line(cell.accents.joinToString(" "), 12f, Typeface.ITALIC)
				key to cell.weight
			}
			view.addView(rowView(row.margin, cells))
		}
		return view
	}
}
