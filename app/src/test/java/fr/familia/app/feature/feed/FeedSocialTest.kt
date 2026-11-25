package fr.familia.app.feature.feed

import fr.familia.app.core.data.GazetteThread
import fr.familia.app.core.data.gazetteThreadId
import org.junit.Assert.assertEquals
import org.junit.Test

/** J'aime and comments of the cards of the gazette (récit 115). */
class FeedSocialTest {
    @Test
    fun `the thread of a card is its key, the slashes of a folder turned into colons`() {
        assertEquals("album:trips:t1:2026-11-20", gazetteThreadId("album:trips/t1:2026-11-20"))
        assertEquals("help:r1", gazetteThreadId("help:r1"))
    }

    @Test
    fun `the J'aime shown first are the most given, then in the order of the four`() {
        val thread = GazetteThread(reactions = mapOf("a" to "🔥", "b" to "💛", "c" to "🔥", "d" to "🎉"))
        assertEquals(listOf("🔥", "💛", "🎉"), reactionSummary(thread))
        assertEquals(emptyList<String>(), reactionSummary(GazetteThread()))
    }

    @Test
    fun `my own J'aime is read from the map`() {
        val thread = GazetteThread(reactions = mapOf("a" to "👏"))
        assertEquals("👏", thread.reactionOf("a"))
        assertEquals(null, thread.reactionOf("b"))
    }
}
