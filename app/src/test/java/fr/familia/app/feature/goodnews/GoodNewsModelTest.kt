package fr.familia.app.feature.goodnews

import fr.familia.app.core.model.GoodNews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GoodNewsModelTest {

    private val news = GoodNews("n1", "Texte", listOf("p1"), "adult1", "Adult1", "adult1", "h1")

    @Test
    fun `photos live in the folder of the author, next to their thumbnail`() {
        assertEquals("goodNews/adult1/n1/p1.jpg", news.photoPath("p1"))
        assertEquals("goodNews/adult1/n1/p1-thumb.jpg", news.thumbnailPath("p1"))
    }

    @Test
    fun `a good news is shown as changed only after a real edit`() {
        val at = Instant.parse("2026-11-21T10:00:00Z")
        assertFalse(news.copy(createdAt = at, updatedAt = at).isEdited)
        assertFalse(news.copy(createdAt = at, updatedAt = at.plusSeconds(30)).isEdited)
        assertTrue(news.copy(createdAt = at, updatedAt = at.plusSeconds(3600)).isEdited)
        assertFalse(news.isEdited)
    }
}
