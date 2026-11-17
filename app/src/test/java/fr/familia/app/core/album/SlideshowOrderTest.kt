package fr.familia.app.core.album

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SlideshowOrderTest {

    private fun photo(author: String, slot: Int, at: String?) = AlbumPhoto("trips/t1", author, author, slot, at?.let(Instant::parse))

    @Test
    fun `the diaporama goes from the oldest photo to the newest, one just sent last`() {
        val first = photo("a", 1, "2027-07-10T08:00:00Z")
        val second = photo("b", 1, "2027-07-11T08:00:00Z")
        val sending = photo("a", 2, null)
        assertEquals(listOf(first, second, sending), slideshowOrder(listOf(sending, second, first)))
    }
}
