package fr.familia.app.feature.albums

import fr.familia.app.core.model.GoodNews
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.trips.TestTrips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** Albums by month and the « Photos du fil » (récit 71). Fictional first names only. */
class AlbumsByMonthTest {
    private fun news(id: String, at: String?, photos: List<String> = listOf("p1"), hiddenBy: List<String> = emptyList(), pending: Boolean = false) =
        GoodNews(
            id = id,
            text = "Une nouvelle",
            photoIds = photos,
            authorId = "adult1",
            authorName = "Ines",
            forMemberId = "adult1",
            householdId = "h1",
            hiddenByIds = hiddenBy,
            createdAt = at?.let(Instant::parse),
            pending = pending,
        )

    @Test
    fun `one album of the fil per month of Paris, newest first, never a hidden or undated photo`() {
        val albums = feedPhotoAlbums(
            listOf(
                news("a", "2026-11-17T10:00:00Z", photos = listOf("p1", "p2")),
                // 23 h 30 UTC on the 31st of October is the 1st of November in Paris.
                news("b", "2026-10-31T23:30:00Z"),
                news("c", "2026-10-12T10:00:00Z"),
                news("hidden", "2026-11-10T10:00:00Z", hiddenBy = listOf("kid1")),
                news("sent", null),
                news("offline", "2026-11-10T10:00:00Z", pending = true),
                news("text", "2026-11-10T10:00:00Z", photos = emptyList()),
            ),
            meId = "kid1",
        )
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), albums.map { it.month })
        assertEquals(listOf("a", "a", "b"), albums.first().photos.map { it.newsId })
        assertEquals(listOf("c"), albums[1].photos.map { it.newsId })
        assertEquals(listOf("Ines"), albums.first().authorNames)
    }

    @Test
    fun `each month shows the photos of the fil first, then its albums, newest month first`() {
        val trip = AlbumEntry(AlbumEntry.Kind.Trip, "t1", "Voyage", LocalDate.of(2026, 10, 3), photoCount = 4)
        val party = AlbumEntry(AlbumEntry.Kind.Event, "e1", "Fête", LocalDate.of(2026, 11, 8), photoCount = 2)
        val feed = feedPhotoAlbums(listOf(news("a", "2026-11-17T10:00:00Z")), "kid1")
        val sections = albumSections(listOf(trip, party), feed)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), sections.map { it.month })
        assertEquals(listOf("feed-2026-11", "Event-e1"), sections.first().tiles.map { it.key })
        assertEquals(listOf("Trip-t1"), sections[1].tiles.map { it.key })
        assertTrue(albumSections(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `with the fil, a surprise trip never shows in Albums, even for who follows it`() {
        val today = LocalDate.of(2027, 7, 25)
        val open = TestTrips.trip("t1")
        val surprise = TestTrips.trip("t2", readers = listOf("adult1", "adult2"))
        assertEquals(listOf("t1", "t2"), albumCandidates(listOf(open, surprise), emptyList(), TestFamily.family, "adult1", today).map { it.id }.sorted())
        assertEquals(listOf("t1"), albumCandidates(listOf(open, surprise), emptyList(), TestFamily.family, "adult1", today, hideSurprises = true).map { it.id })
    }
}
