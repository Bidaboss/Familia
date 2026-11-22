package fr.familia.app.feature.trips.story

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.model.TripWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** « Le voyage jour par jour » (récit 90). */
class TripStoryTest {
    private val trip = Trip("t1", "La Palmyre", LocalDate.of(2026, 4, 11), LocalDate.of(2026, 4, 16), listOf("ines"), listOf("ines"), "ines", "ines")

    private fun step(id: String, day: Int, place: String, lat: Double? = null, lng: Double? = null) =
        TripStep(id, StepType.Lodging, place, LocalDate.of(2026, 4, day), null, null, null, null, lat = lat, lng = lng)

    private fun word(step: String, vararg photos: String) = TripWord(step, "Un mot", photos.toList(), "ines", "Inès")

    private fun photo(slot: Int) = AlbumPhoto("trips/t1", "ines", "Inès", slot, null)

    @Test
    fun `only the days with a word count, numbered from the first day of the trip`() {
        val days = storyDays(trip, listOf(step("a", 11, "Lyon"), step("b", 12, "Bordeaux"), step("c", 14, "Palmyre")), mapOf("a" to word("a"), "c" to word("c")), emptyList())
        assertEquals(listOf(LocalDate.of(2026, 4, 11), LocalDate.of(2026, 4, 14)), days.map { it.date })
        assertEquals(listOf(1, 4), days.map { it.number })
    }

    @Test
    fun `a word is told on the first day of its step, not on every day of a long stay`() {
        val long = TripStep("a", StepType.Lodging, "Camping", LocalDate.of(2026, 4, 12), null, LocalDate.of(2026, 4, 16), null, null)
        val days = storyDays(trip, listOf(long), mapOf("a" to word("a")), emptyList())
        assertEquals(listOf(LocalDate.of(2026, 4, 12)), days.map { it.date })
    }

    @Test
    fun `no word, no day`() = assertTrue(storyDays(trip, listOf(step("a", 11, "Lyon")), emptyMap(), emptyList()).isEmpty())

    @Test
    fun `the screens are each photo of each word, a word with no photo alone, then the route`() {
        val album = listOf(photo(1), photo(2))
        val day = storyDays(trip, listOf(step("a", 11, "Lyon"), step("b", 11, "Bordeaux")), mapOf("a" to word("a", "ines_1", "ines_2"), "b" to word("b")), album).single()
        val frames = storyFrames(day)
        assertEquals(4, frames.size)
        assertTrue(frames[0] is StoryFrame.Photo && frames[1] is StoryFrame.Photo)
        assertTrue(frames[2] is StoryFrame.Text)
        assertEquals(StoryFrame.End, frames[3])
    }

    @Test
    fun `a photo gone from the album drops out and the word stands alone`() {
        val day = storyDays(trip, listOf(step("a", 11, "Lyon")), mapOf("a" to word("a", "ines_9")), listOf(photo(1))).single()
        assertTrue(storyFrames(day).first() is StoryFrame.Text)
    }

    @Test
    fun `the route of a day with two placed steps has its length and its places`() {
        val day = storyDays(
            trip,
            listOf(step("a", 11, "Bordeaux", 44.84, -0.58), step("b", 11, "La Palmyre", 45.68, -1.18)),
            mapOf("a" to word("a")),
            emptyList(),
        ).single()
        assertEquals("Bordeaux → La Palmyre", day.routeLabel)
        assertTrue("km ${day.distanceKm}", day.distanceKm!! in 100..130)
        assertEquals(false, day.hasUnplaced)
    }

    @Test
    fun `a step with no position leaves the route not placed`() {
        val day = storyDays(trip, listOf(step("a", 11, "Bordeaux", 44.84, -0.58), step("b", 11, "Chez Mamie")), mapOf("a" to word("a")), emptyList()).single()
        assertNull(day.distanceKm)
        assertTrue(day.hasUnplaced)
    }

    @Test
    fun `the first day opened is the one asked, else today, else the last with content`() {
        val days = storyDays(trip, listOf(step("a", 11, "Lyon"), step("b", 13, "Bordeaux"), step("c", 15, "Palmyre")), mapOf("a" to word("a"), "b" to word("b"), "c" to word("c")), emptyList())
        assertEquals(1, firstStoryDay(days, LocalDate.of(2026, 4, 13), LocalDate.of(2026, 4, 15)))
        assertEquals(2, firstStoryDay(days, null, LocalDate.of(2026, 4, 15)))
        assertEquals(2, firstStoryDay(days, null, LocalDate.of(2026, 5, 1)))
        assertEquals(2, firstStoryDay(days, LocalDate.of(2026, 4, 12), LocalDate.of(2026, 4, 14)))
        assertNull(firstStoryDay(emptyList(), null, LocalDate.of(2026, 4, 12)))
    }
}
