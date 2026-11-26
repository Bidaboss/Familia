package fr.familia.app.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedCardsMemoryTest {
    @Test
    fun cardsComeBackForTheSameMemberOnly() {
        FeedCardsMemory.keep("m1", emptyList())
        assertEquals(emptyList<FeedCard>(), FeedCardsMemory.of("m1"))
        assertNull(FeedCardsMemory.of("m2"))
    }
}
