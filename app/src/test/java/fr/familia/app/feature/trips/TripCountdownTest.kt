package fr.familia.app.feature.trips

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TripCountdownTest {
    private val start = LocalDate.of(2027, 4, 12)
    private val end = LocalDate.of(2027, 4, 17)

    private fun on(day: LocalDate) = tripCountdown(start, end, day)

    @Test
    fun `before the trip, the nights are counted`() {
        assertEquals(TripCountdown.NightsBefore(4), on(LocalDate.of(2027, 4, 8)))
        assertEquals(TripCountdown.NightsBefore(2), on(LocalDate.of(2027, 4, 10)))
    }

    @Test
    fun `the day before is tomorrow and the first day is the day itself`() {
        assertEquals(TripCountdown.Tomorrow, on(LocalDate.of(2027, 4, 11)))
        assertEquals(TripCountdown.DepartureDay, on(start))
    }

    @Test
    fun `during the trip, the day among all of them`() {
        assertEquals(TripCountdown.During(2, 6), on(LocalDate.of(2027, 4, 13)))
        assertEquals(TripCountdown.During(6, 6), on(end))
    }

    @Test
    fun `after the trip there is nothing`() {
        assertEquals(TripCountdown.Over, on(LocalDate.of(2027, 4, 18)))
    }
}
