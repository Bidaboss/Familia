package fr.familia.app.core.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendJournalTest {

    private class MemoryStore : SendStore {
        val entries = linkedMapOf<String, SendEntry>()
        override fun all(): List<SendEntry> = entries.values.toList()
        override fun put(entry: SendEntry) {
            entries[entry.path] = entry
        }
        override fun remove(path: String) {
            entries.remove(path)
        }
    }

    private val news = SendEntry("goodNews/n1", SendKind.GoodNews, "Inès a son permis", "camille")
    private val help = SendEntry("helpRequests/r1", SendKind.HelpRequest, "Garde de mercredi", "camille")
    private val deal = SendEntry("goodDeals/d1", SendKind.GoodDeal, "Fraises à cueillir", "noe")

    @Test
    fun `a creation answered in this launch leaves nothing to tell`() = runTest {
        val store = MemoryStore()
        val journal = SendJournal(store) { error("nothing to check") }
        journal.started(news)
        journal.settled(news.path)
        assertTrue(store.entries.isEmpty())
        assertEquals(emptyList<SendEntry>(), journal.leftovers("camille"))
    }

    @Test
    fun `what a former launch left and the server refused is said once, the rest is forgotten`() = runTest {
        val store = MemoryStore().apply { put(news); put(help) }
        var asked = listOf<String>()
        val journal = SendJournal(store) { paths -> asked = paths; setOf(help.path) }
        assertEquals(listOf(help), journal.leftovers("camille"))
        assertEquals(listOf(news.path, help.path), asked)
        assertTrue(store.entries.isEmpty())
        assertEquals(emptyList<SendEntry>(), journal.leftovers("camille"))
    }

    @Test
    fun `without network nothing is said and the entries wait for the next launch`() = runTest {
        val store = MemoryStore().apply { put(news) }
        val journal = SendJournal(store) { null }
        assertEquals(emptyList<SendEntry>(), journal.leftovers("camille"))
        assertEquals(listOf(news), store.all())
    }

    @Test
    fun `on a shared phone, what another member left is dropped without a word`() = runTest {
        val store = MemoryStore().apply { put(deal); put(news) }
        var asked = listOf<String>()
        val journal = SendJournal(store) { paths -> asked = paths; paths.toSet() }
        assertEquals(listOf(news), journal.leftovers("camille"))
        assertEquals(listOf(news.path), asked)
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun `a creation still on its way in this launch is not checked`() = runTest {
        val store = MemoryStore()
        val journal = SendJournal(store) { paths -> paths.toSet() }
        journal.started(help)
        assertEquals(emptyList<SendEntry>(), journal.leftovers("camille"))
        assertEquals(listOf(help), store.all())
    }

    @Test
    fun `an item removed on purpose is not told as lost`() = runTest {
        val store = MemoryStore().apply { put(news) }
        val journal = SendJournal(store) { paths -> paths.toSet() }
        journal.settled(news.path)
        assertEquals(emptyList<SendEntry>(), journal.leftovers("camille"))
    }
}
