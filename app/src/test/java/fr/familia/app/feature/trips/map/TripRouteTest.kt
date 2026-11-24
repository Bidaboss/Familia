package fr.familia.app.feature.trips.map

import fr.familia.app.core.model.TripWord
import fr.familia.app.feature.trips.TestTrips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripRouteTest {
    private fun stop(id: String, number: Int, total: Int, place: String, day: Int): MapStop {
        val step = TestTrips.step(id, LocalDate.of(2026, 4, day)).copy(place = place)
        return MapStop(step, number, total, null)
    }

    private fun word(stepId: String, photos: Int) = TripWord(stepId, "Un mot", List(photos) { "p$it" }, "a1", "Camille")

    @Test
    fun `every step is a line in its order with its words and photos`() {
        val stops = listOf(stop("s1", 1, 2, "Lyon", 12), stop("s2", 2, 2, "La Samoure", 14))
        val entries = routeEntries(stops, mapOf("s2" to word("s2", 3)))
        assertEquals(listOf("Lyon", "La Samoure"), entries.map { it.place })
        assertEquals(listOf(1, 2), entries.map { it.number })
        assertEquals(0, entries[0].words)
        assertEquals(0, entries[0].photos)
        assertEquals(1, entries[1].words)
        assertEquals(3, entries[1].photos)
    }

    @Test
    fun `the animated map stays still with no animation or with TalkBack`() {
        assertFalse(prefersStillMap(animatorScale = 1f, touchExploration = false))
        assertTrue(prefersStillMap(animatorScale = 0f, touchExploration = false))
        assertTrue(prefersStillMap(animatorScale = 1f, touchExploration = true))
        assertFalse(prefersStillMap(animatorScale = 0.5f, touchExploration = false))
    }
}
