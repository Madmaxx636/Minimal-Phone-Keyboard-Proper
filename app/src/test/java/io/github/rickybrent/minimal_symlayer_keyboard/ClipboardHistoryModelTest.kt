package io.github.rickybrent.minimal_symlayer_keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardHistoryModelTest {
	private var time = 1_000L
	private fun model(max: Int = 50, expire: Long = 0L) = ClipboardHistoryModel(max, expire) { time }
	private fun ClipboardHistoryModel.texts() = items().map { it.text }

	@Test
	fun newestFirst() {
		val m = model()
		m.add("a"); m.add("b"); m.add("c")
		assertEquals(listOf("c", "b", "a"), m.texts())
	}

	@Test
	fun copyingAgainMovesToFront() {
		val m = model()
		m.add("a"); m.add("b")
		assertTrue(m.add("a"))
		assertEquals(listOf("a", "b"), m.texts())
		assertFalse(m.add("a"))
		assertFalse(m.add(""))
	}

	@Test
	fun pinnedFirstAndNeverTrimmed() {
		val m = model(max = 2)
		m.add("old")
		m.togglePin(m.items().first())
		m.add("b"); m.add("c"); m.add("d")
		assertEquals(listOf("old", "d", "c"), m.texts())
	}

	@Test
	fun limitOnlyCountsUnpinned() {
		val m = model(max = 1)
		m.addPinned("p1"); m.addPinned("p2")
		m.add("x"); m.add("y")
		assertEquals(listOf("p1", "p2", "y"), m.texts())
	}

	@Test
	fun unpinningTrimsToTheLimit() {
		val m = model(max = 1)
		m.add("a")
		m.togglePin(m.items().first())
		m.add("b")
		m.togglePin(m.items().first { it.text == "a" })
		assertEquals(listOf("b"), m.texts())
	}

	@Test
	fun shrinkingTheLimitDropsTheOldest() {
		val m = model()
		for (t in listOf("a", "b", "c", "d")) m.add(t)
		m.maxUnpinned = 2
		m.trim()
		assertEquals(listOf("d", "c"), m.texts())
	}

	@Test
	fun clearUnpinnedKeepsPinned() {
		val m = model()
		m.add("a"); m.add("b")
		m.togglePin(m.items().first { it.text == "a" })
		assertTrue(m.clearUnpinned())
		assertEquals(listOf("a"), m.texts())
		assertFalse(m.clearUnpinned())
	}

	@Test
	fun removingAPinnedClippingForgetsThePin() {
		val m = model()
		m.addPinned("a")
		m.remove(m.items().first())
		assertEquals(emptySet<String>(), m.pinnedTexts())
		assertTrue(m.items().isEmpty())
	}

	@Test
	fun expiresOnlyOldUnpinnedClippings() {
		val m = model(expire = 100)
		m.add("pinned"); m.togglePin(m.items().first())
		m.add("old")
		time += 50
		m.add("recent")
		time += 60 // "old" is now 110 old, "recent" 60, and "pinned" is exempt.
		assertEquals(listOf("pinned", "recent"), m.texts())
	}

	@Test
	fun neverExpiresWhenTheSettingIsZero() {
		val m = model(expire = 0)
		m.add("a")
		time += 1_000_000_000L
		assertEquals(listOf("a"), m.texts())
	}

	@Test
	fun searchIgnoresCase() {
		val m = model()
		m.add("Hello World"); m.add("other")
		assertEquals(listOf("Hello World"), m.search("WORLD").map { it.text })
		assertEquals(2, m.search("").size)
		assertEquals(2, m.search(null).size)
	}
}
