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
 * switches between: the symbols that Alt and Sym type, laid out like the symbols page of a touch keyboard with the
 * key that types each one written under it (and a tap on a symbol types it), and a map of the keyboard with the
 * additional characters of every key.
 * @param onSymbol Called with what to type when a symbol is tapped.
 */
class SymMapView(context: Context, private val onSymbol: (String) -> Unit) : LinearLayout(context) {
	private val density = resources.displayMetrics.density
	private val ink = themeColor(android.R.attr.textColorPrimary, Color.BLACK)

	private var symbols: List<SymbolRow> = emptyList()
	private var extras: List<CharacterRow> = emptyList()
	private var showsSymbols = true

	private val symbolsTab = tab("Symbols") { show(true) }
	private val extrasTab = tab("Extras") { show(false) }
	private val page = FrameLayout(context)

	init {
		orientation = VERTICAL
		setBackgroundColor(themeColor(android.R.attr.colorBackground, Color.WHITE))
		setPadding(dp(6), dp(4), dp(6), dp(4))
		val tabs = LinearLayout(context)
		tabs.orientation = HORIZONTAL
		tabs.addView(symbolsTab, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, dp(6), dp(3)) })
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

	/** Show these symbols and extra characters. Call it again to show something else. The page shown stays. */
	fun setContent(symbols: List<SymbolRow>, extras: List<CharacterRow>) {
		this.symbols = symbols
		this.extras = extras
		render()
	}

	private fun show(symbolsPage: Boolean) {
		showsSymbols = symbolsPage
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
		styleTab(symbolsTab, showsSymbols)
		styleTab(extrasTab, !showsSymbols)
		page.removeAllViews()
		page.addView(if (showsSymbols) symbolsPage() else extrasPage())
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

	private fun keyView(vararg lines: Triple<String, Float, Int>): LinearLayout {
		val view = LinearLayout(context)
		view.orientation = VERTICAL
		view.gravity = Gravity.CENTER
		view.setBackgroundResource(R.drawable.kbd_key_background)
		view.setPadding(dp(2), dp(2), dp(2), dp(2))
		for ((text, size, style) in lines) {
			if (text.isEmpty()) continue
			view.addView(TextView(context).apply {
				this.text = text
				textSize = size
				setTypeface(null, style)
				setTextColor(Color.BLACK)
				gravity = Gravity.CENTER_HORIZONTAL
			})
		}
		return view
	}

	// --- Symbols ---

	private fun symbolsPage(): View {
		val view = LinearLayout(context)
		view.orientation = VERTICAL
		view.addView(note("Tap a symbol to type it, or use its keys: Alt O is Alt and then O (or hold O), Sym I is Sym and then I."))
		for (row in symbols) {
			val cells = row.cells.map { cell ->
				val key = keyView(Triple(cell.text, 18f, Typeface.BOLD), Triple(cell.hint, 9f, Typeface.NORMAL))
				key.setOnClickListener { onSymbol(cell.text) }
				key to 1f
			}
			view.addView(rowView(row.margin, cells))
		}
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
				keyView(
					Triple(cell.label, 13f, Typeface.BOLD),
					Triple(cell.extras.joinToString(" "), 12f, Typeface.NORMAL),
					Triple(cell.accents.joinToString(" "), 12f, Typeface.ITALIC)
				) to cell.weight
			}
			view.addView(rowView(row.margin, cells))
		}
		return view
	}
}
