package io.github.rickybrent.minimal_symlayer_keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The map of the keyboard that a tap of Sym opens: the keyboard as it sits on the phone, with one big symbol on a
 * key and a small letter, so that it is clear at a glance. There are several pages with the keys in the same places,
 * and each tap of Sym goes to the next one.
 */
class SymMapView(context: Context) : LinearLayout(context) {
	private val density = resources.displayMetrics.density
	private val ink = themeColor(android.R.attr.textColorPrimary, Color.BLACK)

	private var pages: List<MapPage> = emptyList()
	private var index = 0

	init {
		orientation = VERTICAL
		setBackgroundColor(themeColor(android.R.attr.colorBackground, Color.WHITE))
		setPadding(dp(6), dp(4), dp(6), dp(4))
	}

	private fun dp(value: Int) = (value * density).toInt()

	private fun themeColor(attribute: Int, fallback: Int): Int {
		val value = TypedValue()
		if (!context.theme.resolveAttribute(attribute, value, true)) return fallback
		return if (value.resourceId != 0) context.getColor(value.resourceId) else value.data
	}

	/** Show these pages, starting with the first. */
	fun setPages(pages: List<MapPage>) {
		this.pages = pages
		index = 0
		render()
	}

	/** @return true if it went on to the next page, false if it was already on the last one. */
	fun showNext(): Boolean {
		if (index >= pages.size - 1) return false
		index++
		render()
		return true
	}

	private fun render() {
		removeAllViews()
		val page = pages.getOrNull(index) ?: return
		val more = index < pages.size - 1
		addView(TextView(context).apply {
			text = "${page.title}  ·  ${index + 1} of ${pages.size}  ·  " + if (more) "tap Sym for the next page" else "tap Sym to close"
			textSize = 12f
			setTypeface(null, Typeface.BOLD)
			setTextColor(ink)
		})
		addView(TextView(context).apply {
			text = page.note
			textSize = 11f
			setTextColor(ink)
			setPadding(0, 0, 0, dp(2))
		})
		for (row in page.rows) addView(rowView(row))
	}

	private fun rowView(row: PageRow): View {
		val view = LinearLayout(context)
		view.orientation = HORIZONTAL
		view.isBaselineAligned = false
		fun spacer() = view.addView(View(context), LayoutParams(0, 0, row.margin))
		if (row.margin > 0f) spacer()
		for (key in row.keys) {
			view.addView(keyView(key), LayoutParams(0, LayoutParams.MATCH_PARENT, key.weight).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
		}
		if (row.margin > 0f) spacer()
		return view
	}

	private fun text(text: String, size: Float, style: Int = Typeface.NORMAL) = TextView(context).apply {
		this.text = text
		textSize = size
		setTypeface(null, style)
		setTextColor(Color.BLACK)
	}

	private fun keyView(key: PageKey): View {
		val view = FrameLayout(context)
		view.setBackgroundResource(R.drawable.kbd_key_background)
		view.minimumHeight = dp(50)
		val letter = key.label.length == 1 && key.label[0].isLetter()
		if (key.symbol.isNotEmpty()) {
			// One symbol, as big as the key allows. A pair like "€/£" is smaller so that it fits.
			val single = key.symbol.length == 1
			view.addView(
				text(key.symbol, if (single) 26f else 14f, if (single) Typeface.BOLD else Typeface.NORMAL),
				FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
			)
			// The letter of the key is small, in the corner.
			view.addView(
				text(key.label, 9f),
				FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
					setMargins(dp(3), dp(1), dp(3), dp(1))
				}
			)
		} else {
			// A key with nothing on this page is just marked, so that the keys stay where they are.
			view.addView(
				text(key.label, if (letter) 10f else if (key.label.length > 1) 12f else 15f),
				FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
			)
		}
		return view
	}
}
