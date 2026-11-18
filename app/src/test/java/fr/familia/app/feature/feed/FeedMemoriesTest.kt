package fr.familia.app.feature.feed

import fr.familia.app.core.model.Capsule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Se souvenir en un seul lieu (récit 71): the souvenirs that come into the fil. Fictional first names only. */
class FeedMemoriesTest {
    private fun capsule(id: String, openedAt: String?) = Capsule(
        id = id,
        text = "Pour plus tard",
        photoCount = 0,
        recipientIds = listOf("ines"),
        openAt = LocalDate.of(2026, 11, 1),
        openedFor = if (openedAt != null) listOf("ines") else emptyList(),
        openedAt = openedAt?.let(Instant::parse),
        authorId = "noe",
        authorName = "Noé",
        createdAt = null,
    )

    @Test
    fun `the capsules opened in the window, newest first, never one still sealed`() {
        val kept = openedCapsules(
            listOf(
                capsule("a", "2026-11-10T06:00:00Z"),
                capsule("b", "2026-11-17T06:00:00Z"),
                capsule("old", "2026-10-01T06:00:00Z"),
                capsule("sealed", null),
            ),
            Instant.parse("2026-11-04T00:00:00Z"),
            10,
        )
        assertEquals(listOf("b", "a"), kept.map { it.id })
        assertEquals(listOf("b"), openedCapsules(kept, Instant.EPOCH, 1).map { it.id })
    }

    @Test
    fun `the week of the family comes on Sundays only`() = runBlocking {
        val window = FeedWindow(1, Instant.parse("2026-11-18T12:00:00Z"))
        val monday = WeekFeedSource(null, null, emptyList(), "ines", LocalDate.of(2026, 11, 16))
        assertTrue(monday.cards(window).first().isEmpty())
        // A Sunday with nothing to tell makes no card either.
        val sunday = WeekFeedSource(null, null, emptyList(), "ines", LocalDate.of(2026, 11, 22))
        assertTrue(sunday.cards(window).first().isEmpty())
    }

    @Test
    fun `our year shows only when its card does, under the 26th of December`() = runBlocking {
        val window = FeedWindow(1, Instant.parse("2026-11-18T12:00:00Z"))
        assertTrue(YearInReviewFeedSource(null).cards(window).first().isEmpty())
        val card = YearInReviewFeedSource(2026).cards(window).first().single()
        assertEquals("yearInReview:2026", card.key)
        assertEquals(LocalDate.of(2026, 12, 26), feedDay(card))
    }
}
