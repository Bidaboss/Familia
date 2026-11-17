package fr.familia.app.feature.albums

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.trips.TestTrips
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class AlbumsTest {

    private val today = LocalDate.of(2027, 7, 25)

    @Test
    fun `a trip already started is an album, a trip to come or of more than a year ago is not`() {
        val now = TestTrips.trip("t1")
        val later = TestTrips.trip("t2", start = LocalDate.of(2027, 8, 1), end = LocalDate.of(2027, 8, 5))
        val old = TestTrips.trip("t3", start = LocalDate.of(2026, 5, 1), end = LocalDate.of(2026, 5, 3))
        val albums = albumCandidates(listOf(now, later, old), emptyList(), TestFamily.family, "kid1", today)
        assertEquals(listOf("t1"), albums.map { it.id })
        assertEquals("trips/t1", albums.single().folder)
    }

    @Test
    fun `nothing for a member the family does not know`() {
        assertEquals(emptyList<AlbumEntry>(), albumCandidates(listOf(TestTrips.trip()), emptyList(), TestFamily.family, "nobody", today))
    }

    @Test
    fun `only the albums with photos are kept, with their newest photo and their months`() {
        val trip = AlbumEntry(AlbumEntry.Kind.Trip, "t1", "Corse", LocalDate.of(2027, 7, 10))
        val event = AlbumEntry(AlbumEntry.Kind.Event, "e1", "Pique-nique", LocalDate.of(2027, 6, 2))
        val empty = AlbumEntry(AlbumEntry.Kind.Event, "e2", "Réunion", LocalDate.of(2027, 5, 2))
        val newest = AlbumPhoto("trips/t1", "a", "A", 2, Instant.parse("2027-07-12T08:00:00Z"))
        val older = AlbumPhoto("trips/t1", "a", "A", 1, Instant.parse("2027-07-11T08:00:00Z"))
        val party = AlbumPhoto("events/e1", "b", "B", 1, Instant.parse("2027-06-02T18:00:00Z"))
        val state = albumsState(listOf(trip, event, empty), listOf(listOf(newest, older), listOf(party), emptyList()))
        assertEquals(listOf("t1", "e1"), state.albums.map { it.id })
        assertEquals(2, state.albums.first().photoCount)
        assertEquals(newest.thumbnailPath, state.albums.first().coverThumb)
        assertEquals(listOf(YearMonth.of(2027, 7), YearMonth.of(2027, 6)), state.months)
    }
}
