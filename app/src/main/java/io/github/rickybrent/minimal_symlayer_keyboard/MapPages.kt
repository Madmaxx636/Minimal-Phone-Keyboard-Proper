package io.github.rickybrent.minimal_symlayer_keyboard

/** One key on a page of the map: its letter, and the one symbol that the page shows on it (or none). */
data class PageKey(val label: String, val symbol: String = "", val weight: Float = 1f)

data class PageRow(val keys: List<PageKey>, val margin: Float)

/**
 * A page of the map of the keyboard. Every page has the keys in the same places, and no key has more than one symbol.
 * @param title What the page shows, for the top of it.
 * @param note How to read the page.
 */
data class MapPage(val title: String, val note: String, val rows: List<PageRow>)

/**
 * Splits the map of the keyboard into pages with one symbol to a key, so that every symbol can be big and clear.
 * Each tap of the Sym key while the map shows goes to the next page. The pages are: what Alt types, what Sym does,
 * then what pressing a key again after holding it gives, first time, second time and so on, and, if accents are
 * turned on, the accents in the same way. A page that no key has anything for is left out.
 */
object MapPages {
	fun build(rows: List<MapRow>): List<MapPage> {
		val pages = ArrayList<MapPage>()

		fun add(title: String, note: String, symbol: (MapKey) -> String) {
			val page = MapPage(title, note, rows.map { row ->
				PageRow(row.keys.map { PageKey(it.label, symbol(it), it.weight) }, row.margin)
			})
			if (page.rows.any { row -> row.keys.any { it.symbol.isNotEmpty() } }) pages.add(page)
		}

		add("Alt", "What Alt and the key type. Holding the key gives the same.") { it.alt }
		add("Sym", "What Sym and the key do.") { it.sym }

		val extras = rows.maxOfOrNull { row -> row.keys.maxOfOrNull { it.extras.size } ?: 0 } ?: 0
		for (n in 1..extras) {
			add("Press again $n", "Hold a key, then press it again ${times(n)} for this.") { it.extras.getOrElse(n - 1) { "" } }
		}
		val accents = rows.maxOfOrNull { row -> row.keys.maxOfOrNull { it.accents.size } ?: 0 } ?: 0
		for (n in 1..accents) {
			add("Accent $n", "Press the vowel ${n + 1} times quickly for this.") { it.accents.getOrElse(n - 1) { "" } }
		}
		return pages
	}

	private fun times(n: Int) = when (n) {
		1 -> "once"
		2 -> "twice"
		else -> "$n times"
	}
}
