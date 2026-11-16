package fr.familia.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhoIsItTest {

    @Test
    fun `a blank year means we do not know, a typed one must be a real year`() {
        assertEquals(null, parseWhoIsItYear("  ", 2026).getOrThrow())
        assertEquals(1962, parseWhoIsItYear(" 1962 ", 2026).getOrThrow())
        assertTrue(parseWhoIsItYear("1700", 2026).isFailure)
        assertTrue(parseWhoIsItYear("2030", 2026).isFailure)
        assertTrue(parseWhoIsItYear("années 60", 2026).isFailure)
    }

    @Test
    fun `the photo and its thumbnail live in the folder of the author`() {
        val post = WhoIsItPost("w1", "adult1", "Camille", "", null)
        assertEquals("whoIsIt/adult1/w1/photo.jpg", post.photoPath)
        assertEquals("whoIsIt/adult1/w1/photo-thumb.jpg", post.thumbPath)
    }
}
