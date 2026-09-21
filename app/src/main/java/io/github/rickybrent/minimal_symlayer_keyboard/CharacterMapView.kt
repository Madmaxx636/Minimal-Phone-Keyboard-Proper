package io.github.rickybrent.minimal_symlayer_keyboard

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A map of the keyboard that shows the additional characters of every key: what holding it gives and pressing it
 * again gives, and, if accents are turned on, what pressing it twice quickly gives.
 */
class CharacterMapView(context: Context) : LinearLayout(context) {
	private val density = resources.displayMetrics.density

	init {
		orientation = VERTICAL
		setBackgroundColor(resolveBackground(context))
		val pad = dp(6)
		setPadding(pad, dp(4), pad, dp(4))
	}

	private fun dp(value: Int) = (value * density).toInt()

	private fun resolveBackground(context: Context): Int {
		val value = android.util.TypedValue()
		context.theme.resolveAttribute(android.R.attr.colorBackground, value, true)
		return value.data
	}

	/** Show [rows]. Call this again to show something else. */
	fun setRows(rows: List<CharacterRow>) {
		removeAllViews()
		val showsAccents = rows.any { row -> row.cells.any { it.accents.isNotEmpty() } }
		addView(TextView(context).apply {
			text = "Additional characters"
			textSize = 14f
			setTypeface(null, Typeface.BOLD)
			setTextColor(textColor())
		})
		addView(TextView(context).apply {
			text = "Hold a key for the first one and press it again for the next." +
				if (showsAccents) " In italics: accents, by pressing a vowel twice quickly." else ""
			textSize = 11f
			setTextColor(textColor())
			setPadding(0, 0, 0, dp(2))
		})
		for (row in rows) addView(rowView(row))
	}

	private fun textColor(): Int {
		val value = android.util.TypedValue()
		context.theme.resolveAttribute(android.R.attr.textColorPrimary, value, true)
		return if (value.resourceId != 0) context.getColor(value.resourceId) else value.data
	}

	private fun rowView(row: CharacterRow): View {
		val view = LinearLayout(context)
		view.orientation = HORIZONTAL
		view.isBaselineAligned = false
		fun spacer() = View(context).also { view.addView(it, LayoutParams(0, 0, row.margin)) }
		if (row.margin > 0f) spacer()
		for (cell in row.cells) {
			view.addView(cellView(cell), LayoutParams(0, LayoutParams.MATCH_PARENT, cell.weight).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
		}
		if (row.margin > 0f) spacer()
		return view
	}

	private fun cellView(cell: CharacterCell): View {
		val view = LinearLayout(context)
		view.orientation = VERTICAL
		view.gravity = Gravity.CENTER_HORIZONTAL
		view.setBackgroundResource(R.drawable.kbd_key_background)
		view.setPadding(dp(2), dp(2), dp(2), dp(2))
		fun line(text: String, size: Float, style: Int) {
			if (text.isEmpty()) return
			view.addView(TextView(context).apply {
				this.text = text
				textSize = size
				setTypeface(null, style)
				setTextColor(android.graphics.Color.BLACK)
				gravity = Gravity.CENTER_HORIZONTAL
			})
		}
		line(cell.label, 13f, Typeface.BOLD)
		line(cell.extras.joinToString(" "), 12f, Typeface.NORMAL)
		line(cell.accents.joinToString(" "), 12f, Typeface.ITALIC)
		return view
	}
}
