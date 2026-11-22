package fr.familia.app.core.model

import fr.familia.app.core.album.AlbumPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TripWordTest {
    private fun photo(author: String, slot: Int) = AlbumPhoto("trips/t1", author, author, slot, Instant.EPOCH)

    private fun word(vararg ids: String, hearts: Set<String> = emptySet()) =
        TripWord("s1", "Bien arrivés", ids.toList(), "lou", "Lou", hearts)

    @Test
    fun `the chosen photos come back in the order chosen and a removed one drops out`() {
        val album = listOf(photo("lou", 1), photo("lou", 2), photo("noe", 1))
        val shown = word("noe_1", "lou_9", "lou_1").photosIn(album)
        assertEquals(listOf("noe_1", "lou_1"), shown.map { it.id })
    }

    @Test
    fun `touching a photo chooses it, touching it again takes it off, the others keep their order`() {
        var chosen = emptyList<String>()
        chosen = TripWord.toggled(chosen, "a_1")
        chosen = TripWord.toggled(chosen, "b_2")
        chosen = TripWord.toggled(chosen, "c_3")
        assertEquals(listOf("a_1", "b_2", "c_3"), chosen)
        assertEquals(listOf("a_1", "c_3"), TripWord.toggled(chosen, "b_2"))
    }

    @Test
    fun `a fifth photo is not chosen`() {
        val four = listOf("a_1", "a_2", "a_3", "a_4")
        assertEquals(four, TripWord.toggled(four, "a_5"))
        assertEquals(listOf("a_1", "a_2", "a_3"), TripWord.toggled(four, "a_4"))
    }

    @Test
    fun `the word is trimmed and cut at 280 characters, and a blank one is no word`() {
        assertEquals("Salut", TripWord.clean("  Salut \n"))
        assertEquals(TripWord.MAX_TEXT, TripWord.clean("a".repeat(400))!!.length)
        assertNull(TripWord.clean("   \n "))
    }

    @Test
    fun `a member has put a heart or not`() {
        val w = word(hearts = setOf("sam"))
        assertTrue(w.hearted("sam"))
        assertFalse(w.hearted("noe"))
    }
}
