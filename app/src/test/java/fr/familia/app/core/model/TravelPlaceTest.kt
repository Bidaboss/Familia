package fr.familia.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** « Ajouter une destination » au carnet de baroudeur (récit 107, règle 38). */
class TravelPlaceTest {
    private val today = LocalDate.of(2026, 11, 24)

    @Test
    fun `a chosen place, a past day and 1 to 365 days can be saved`() {
        assertNull(travelPlaceProblem(true, today.minusDays(1), 1, today))
        assertNull(travelPlaceProblem(true, LocalDate.of(1998, 7, 12), 365, today))
    }

    @Test
    fun `the place comes first, then the date, then the days`() {
        assertEquals(TravelPlaceProblem.NoPlace, travelPlaceProblem(false, null, null, today))
        assertEquals(TravelPlaceProblem.NoDate, travelPlaceProblem(true, null, null, today))
        assertEquals(TravelPlaceProblem.Days, travelPlaceProblem(true, today.minusDays(3), null, today))
    }

    @Test
    fun `today and later are not past`() {
        assertEquals(TravelPlaceProblem.DateNotPast, travelPlaceProblem(true, today, 3, today))
        assertEquals(TravelPlaceProblem.DateNotPast, travelPlaceProblem(true, today.plusDays(10), 3, today))
    }

    @Test
    fun `zero days or more than a year is refused`() {
        assertEquals(TravelPlaceProblem.Days, travelPlaceProblem(true, today.minusDays(3), 0, today))
        assertEquals(TravelPlaceProblem.Days, travelPlaceProblem(true, today.minusDays(3), 366, today))
    }
}
