package fr.familia.app.feature.trips.now

import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.trips.TestTrips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NowTravellingTest {

    private val family = TestFamily.family
    private val today = LocalDate.of(2027, 7, 12)

    @Test
    fun `a trip under way gives its day and who goes`() {
        val now = nowTravelling(listOf(TestTrips.trip()), family, "kid1", today)
        assertEquals(NowTravelling("t1", "Voyage t1", listOf("Adult1", "Adult2"), withMe = false, day = 3), now)
    }

    @Test
    fun `a member who goes is not among the others`() {
        val now = nowTravelling(listOf(TestTrips.trip()), family, "adult1", today)
        assertEquals(listOf("Adult2"), now?.others)
        assertEquals(true, now?.withMe)
    }

    @Test
    fun `nothing before or after the trip`() {
        assertNull(nowTravelling(listOf(TestTrips.trip()), family, "kid1", LocalDate.of(2027, 7, 9)))
        assertNull(nowTravelling(listOf(TestTrips.trip()), family, "kid1", LocalDate.of(2027, 7, 21)))
        assertEquals(11, nowTravelling(listOf(TestTrips.trip()), family, "kid1", LocalDate.of(2027, 7, 20))?.day)
    }

    @Test
    fun `with two trips under way, the one that started first`() {
        val later = TestTrips.trip(id = "t2", start = LocalDate.of(2027, 7, 11))
        assertEquals("t1", nowTravelling(listOf(later, TestTrips.trip()), family, "kid1", today)?.tripId)
    }
}
