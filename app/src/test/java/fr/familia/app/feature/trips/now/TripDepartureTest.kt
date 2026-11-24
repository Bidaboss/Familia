package fr.familia.app.feature.trips.now

import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.TripStep
import fr.familia.app.feature.trips.TestTrips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TripDepartureTest {
    private val start = LocalDate.of(2027, 7, 10)
    private val trip = TestTrips.trip(start = start)

    @Test
    fun `the day before, those who go see tomorrow's departure`() {
        assertEquals(TripDeparture("t1", "Voyage t1", place = null, tomorrow = true), tripDeparture(listOf(trip), "adult1", start.minusDays(1)))
    }

    @Test
    fun `on the day itself, the departure of today`() {
        assertEquals(false, tripDeparture(listOf(trip), "adult2", start)?.tomorrow)
    }

    @Test
    fun `nothing two days before, the day after, or for those who only follow`() {
        assertNull(tripDeparture(listOf(trip), "adult1", start.minusDays(2)))
        assertNull(tripDeparture(listOf(trip), "adult1", start.plusDays(1)))
        assertNull(tripDeparture(listOf(trip), "kid1", start.minusDays(1)))
    }

    @Test
    fun `with two departures, the one that leaves first`() {
        val tomorrow = TestTrips.trip(id = "t2", start = start.plusDays(1))
        assertEquals("t1", tripDeparture(listOf(tomorrow, trip), "adult1", start)?.tripId)
    }

    @Test
    fun `the first destination of the trip is said, not the car that leaves`() {
        val car = TripStep("car", StepType.Car, "Maison", start, null, null, null, null)
        val stay = TripStep("stay", StepType.Lodging, "La Samoure", start, null, start.plusDays(3), null, null)
        assertEquals("La Samoure", firstDestination(trip, listOf(stay, car)))
        // Named as « Le programme » names it: the part before the comma.
        assertEquals("La Samoure", firstDestination(trip, listOf(stay.copy(place = "La Samoure, Les Camilles, France"), car)))
        assertNull(firstDestination(trip, listOf(car)))
    }
}
