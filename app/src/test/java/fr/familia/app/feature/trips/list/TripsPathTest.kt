package fr.familia.app.feature.trips.list

import fr.familia.app.core.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripsPathTest {
    private fun trip(id: String, start: LocalDate, end: LocalDate) =
        TripSummary(Trip(id, "Voyage $id", start, end, listOf("a"), listOf("a"), "a", "Camille"), listOf("Camille"))

    private val today = LocalDate.of(2026, 11, 20)

    @Test
    fun `the next trip goes in the landscape and the others under Ensuite by date`() {
        val far = trip("far", LocalDate.of(2027, 7, 3), LocalDate.of(2027, 7, 10))
        val near = trip("near", LocalDate.of(2027, 4, 11), LocalDate.of(2027, 4, 16))
        val plan = tripsPathPlan(emptyList(), listOf(far, near))
        assertEquals(near, plan.hero)
        assertFalse(plan.heroOngoing)
        assertEquals(listOf(far), plan.next)
        assertTrue(plan.now.isEmpty())
    }

    @Test
    fun `two trips at once show the one that started first, the other under En ce moment`() {
        val second = trip("second", LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 22))
        val first = trip("first", LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 21))
        val coming = trip("coming", LocalDate.of(2027, 1, 2), LocalDate.of(2027, 1, 4))
        val plan = tripsPathPlan(listOf(second, first), listOf(coming))
        assertEquals(first, plan.hero)
        assertTrue(plan.heroOngoing)
        assertEquals(listOf(second), plan.now)
        assertEquals(listOf(coming), plan.next)
    }

    @Test
    fun `without a trip to come the landscape is empty`() {
        val plan = tripsPathPlan(emptyList(), emptyList())
        assertNull(plan.hero)
        assertTrue(plan.next.isEmpty())
    }

    @Test
    fun `days to wait, length and day of the trip`() {
        val t = trip("t", LocalDate.of(2027, 4, 11), LocalDate.of(2027, 4, 16)).trip
        assertEquals(142, daysUntil(t, today))
        assertEquals(6, tripLength(t))
        assertEquals(3, tripDayNumber(t, LocalDate.of(2027, 4, 13)))
    }

    @Test
    fun `short dates in one month, across two months and across two years`() {
        assertEquals("dim. 11" to "ven. 16 avril 2027", tripRangeEnds(trip("a", LocalDate.of(2027, 4, 11), LocalDate.of(2027, 4, 16)).trip, weekday = true))
        assertEquals("3" to "10 juillet 2027", tripRangeEnds(trip("b", LocalDate.of(2027, 7, 3), LocalDate.of(2027, 7, 10)).trip, weekday = false))
        assertEquals("30 juillet" to "1er août 2027", tripRangeEnds(trip("c", LocalDate.of(2027, 7, 30), LocalDate.of(2027, 8, 1)).trip, weekday = false))
        assertEquals("30 décembre 2026" to "2 janvier 2027", tripRangeEnds(trip("d", LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2)).trip, weekday = false))
        assertEquals("Août 2026", tripMonthLabel(trip("e", LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 19)).trip))
    }
}
