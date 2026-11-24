package fr.familia.app.feature.trips.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripFormDaysTest {
    private val today = LocalDate.of(2026, 11, 24)
    private val past = TripForm(startDate = LocalDate.of(2025, 8, 1), endDate = LocalDate.of(2025, 8, 6))

    @Test
    fun `only a trip that started before today asks for days`() {
        assertTrue(startsInPast(past, today))
        assertFalse(startsInPast(past.copy(startDate = today), today))
        assertFalse(startsInPast(TripForm(), today))
    }

    @Test
    fun `the days count the first and the last day`() {
        assertEquals(6, formDays(past))
        assertEquals(1, formDays(past.copy(endDate = past.startDate)))
        assertNull(formDays(past.copy(endDate = null)))
    }

    @Test
    fun `a number of days moves the return date, within 1 and the maximum`() {
        assertEquals(LocalDate.of(2025, 8, 3), withDays(past, 3).endDate)
        assertEquals(past.startDate, withDays(past, 0).endDate)
        assertEquals(past.startDate!!.plusDays(MAX_PAST_TRIP_DAYS - 1L), withDays(past, 10_000).endDate)
        assertEquals(TripForm(), withDays(TripForm(), 4))
    }
}
