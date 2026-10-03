package io.github.rickybrent.minimal_symlayer_keyboard

/** One key on a page of the map: its label, and the one symbol that the page shows on it (or none). */
data class PageKey(val label: String, val symbol: String = "", val weight: Float = 1f)

data class PageRow(val keys: List<PageKey>, val margin: Float)

/**
 * A page of the map of the keyboard. Every page has the keys in the same places, and no key has more than one symbol.
 * @param title What the page shows, for the top of it.
 * @param note How to read the page.
 */
data class MapPage(val title: String, val note: String, val rows: List<PageRow>)

/** Turns the keyboard and the [SymbolPages] into the pages that the map shows, one page of symbols after the other. */
object MapPages {
	fun build(rows: List<MapRow> = KeyboardMap.rows(), symbolPages: List<SymbolPages.Page> = SymbolPages.pages): List<MapPage> =
		symbolPages.mapIndexed { index, page ->
			// The key that turns the pages on a BlackBerry says which page it turns to: "2/2" on the first.
			val other = "${(index + 1) % symbolPages.size + 1}/${symbolPages.size}"
			MapPage(page.title, page.note, rows.map { row ->
				PageRow(row.keys.map { key ->
					PageKey(if (key.pageSwitch) other else key.label, page.symbols[key.keyCode].orEmpty(), key.weight)
				}, row.margin)
			})
		}
}
