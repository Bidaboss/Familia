package fr.familia.app.feature.feed

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Recipe
import fr.familia.app.core.model.Trip
import fr.familia.app.feature.yearago.MemoryAlbum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The photos of the albums and the recipes of the fil (récit 67). Fictional first names only. */
class FeedAlbumsTest {
    private fun m(id: String, household: String) = Member(id, id, null, null, household, MemberProfile.Adult, false, emptyList(), null)

    private val family = Family(
        listOf(Household("h1", "Les Martin"), Household("h2", "Les Durand")),
        listOf(m("ines", "h1"), m("noe", "h1"), m("sam", "h2")),
    )
    private val today = LocalDate.of(2026, 11, 18)
    private val since = LocalDate.of(2026, 11, 4)

    private fun trip(id: String, readers: List<String>, start: LocalDate = today.minusDays(5), end: LocalDate = today.minusDays(2)) =
        Trip(id, "Voyage $id", start, end, listOf("ines"), readers, "ines", "ines")

    private fun event(id: String, date: LocalDate, target: EventTarget = EventTarget.ForFamily) =
        Event(id, "Fête $id", date, null, null, null, target, "ines", "ines")

    private fun photo(folder: String, author: String, slot: Int, at: String?) =
        AlbumPhoto(folder, author, author, slot, at?.let(Instant::parse))

    @Test
    fun `a trip hidden from someone never reaches the fil, even for who follows it`() {
        val albums = feedAlbums(
            trips = listOf(trip("t1", listOf("ines", "noe", "sam")), trip("t2", listOf("ines", "noe"))),
            events = emptyList(),
            family = family,
            meId = "ines",
            since = since,
            today = today,
        )
        assertEquals(listOf("trips/t1"), albums.map { it.folder })
    }

    @Test
    fun `the events that concern me, from a month before the window, never one to come`() {
        val albums = feedAlbums(
            trips = emptyList(),
            events = listOf(
                event("old", since.minusDays(FEED_ALBUM_MARGIN_DAYS + 1)),
                event("kept", since.minusDays(10)),
                event("later", today.plusDays(1)),
                event("other", today, EventTarget.ForHousehold("h2")),
                event("mine", today, EventTarget.ForHousehold("h1")),
            ),
            family = family,
            meId = "ines",
            since = since,
            today = today,
        )
        assertEquals(listOf("events/mine", "events/kept"), albums.map { it.folder })
        assertEquals(MemoryAlbum.Kind.Event, albums.first().album.kind)
    }

    @Test
    fun `one card per album and per day of Paris, the photos of the window only, newest first`() {
        val trip = FeedAlbum(MemoryAlbum(MemoryAlbum.Kind.Trip, "t1", "Voyage"), "trips/t1", today)
        val party = FeedAlbum(MemoryAlbum(MemoryAlbum.Kind.Event, "e1", "Fête"), "events/e1", today)
        val cards = albumCards(
            albums = listOf(trip, party),
            photos = listOf(
                listOf(
                    // 23 h 30 UTC on the 16th is the 17th in Paris: with the photo of the 17th.
                    photo("trips/t1", "ines", 1, "2026-11-16T23:30:00Z"),
                    photo("trips/t1", "noe", 2, "2026-11-17T09:00:00Z"),
                    photo("trips/t1", "ines", 3, "2026-11-16T10:00:00Z"),
                    // Before the window, and not yet dated by the server: left out.
                    photo("trips/t1", "ines", 4, "2026-11-01T10:00:00Z"),
                    photo("trips/t1", "ines", 5, null),
                ),
                listOf(photo("events/e1", "sam", 1, "2026-11-18T08:00:00Z")),
            ),
            since = Instant.parse("2026-11-04T12:00:00Z"),
            limit = 10,
        )
        assertEquals(
            listOf("album:events/e1:2026-11-18", "album:trips/t1:2026-11-17", "album:trips/t1:2026-11-16"),
            cards.map { it.key },
        )
        val seventeenth = cards[1]
        assertEquals(listOf("noe_2", "ines_1"), seventeenth.photos.map { it.id })
        assertEquals("noe", seventeenth.authorId)
        assertEquals(listOf("noe", "ines"), seventeenth.authorNames)
        assertTrue(albumCards(listOf(trip, party), listOf(emptyList(), emptyList()), Instant.EPOCH, 2).isEmpty())
    }

    @Test
    fun `the cards stop at the limit of the window`() {
        val trip = FeedAlbum(MemoryAlbum(MemoryAlbum.Kind.Trip, "t1", "Voyage"), "trips/t1", today)
        val photos = (1..5).map { photo("trips/t1", "ines", it, "2026-11-1${it}T10:00:00Z") }
        val cards = albumCards(listOf(trip), listOf(photos), Instant.parse("2026-11-04T00:00:00Z"), 3)
        assertEquals(listOf("album:trips/t1:2026-11-15", "album:trips/t1:2026-11-14", "album:trips/t1:2026-11-13"), cards.map { it.key })
    }

    @Test
    fun `the recipes added in the window, newest first`() {
        fun r(id: String, at: String?) = Recipe(id, "Tarte $id", "", null, null, "", "", emptyList(), "ines", "ines", at?.let(Instant::parse))
        val kept = newRecipes(
            listOf(r("a", "2026-11-10T10:00:00Z"), r("b", "2026-11-17T10:00:00Z"), r("c", "2026-10-01T10:00:00Z"), r("d", null)),
            Instant.parse("2026-11-04T12:00:00Z"),
            10,
        )
        assertEquals(listOf("b", "a"), kept.map { it.id })
        assertEquals(listOf("b"), newRecipes(kept, Instant.EPOCH, 1).map { it.id })
    }
}
