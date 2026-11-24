package fr.familia.app.feature.trips.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class TripDatesTest {
    private val aug2 = LocalDate.of(2027, 8, 2)
    private val aug9 = LocalDate.of(2027, 8, 9)

    @Test
    fun `the first touch is the start, the second the end`() {
        val one = pickDay(DateRange(null, null), aug2)
        assertEquals(DateRange(aug2, null), one)
        assertNull(one.nights)
        val two = pickDay(one, aug9)
        assertEquals(DateRange(aug2, aug9), two)
        assertEquals(7L, two.nights)
    }

    @Test
    fun `a day before the start becomes the start`() {
        assertEquals(DateRange(aug2, null), pickDay(DateRange(aug9, null), aug2))
    }

    @Test
    fun `the same day twice is a trip of one day`() {
        val range = pickDay(DateRange(aug2, null), aug2)
        assertEquals(DateRange(aug2, aug2), range)
        assertEquals(0L, range.nights)
    }

    @Test
    fun `a touch once both are chosen starts again`() {
        assertEquals(DateRange(aug9, null), pickDay(DateRange(aug2, aug9), aug9))
    }

    @Test
    fun `a trip cannot last longer than the longest trip`() {
        val far = aug2.plusDays(MAX_PAST_TRIP_DAYS.toLong())
        assertEquals(DateRange(far, null), pickDay(DateRange(aug2, null), far))
    }

    @Test
    fun `weeks start on Monday and have seven places`() {
        // November 2026 starts on a Sunday: six empty places before the 1st.
        val lines = calendarLines(YearMonth.of(2026, 11), YearMonth.of(2026, 12))
        assertEquals(CalendarLine.Month(YearMonth.of(2026, 11)), lines.first())
        val firstWeek = lines[1] as CalendarLine.Week
        assertEquals(List(6) { null } + LocalDate.of(2026, 11, 1), firstWeek.days)
        lines.filterIsInstance<CalendarLine.Week>().forEach { assertEquals(7, it.days.size) }
        assertEquals(2, lines.count { it is CalendarLine.Month })
        val days = lines.filterIsInstance<CalendarLine.Week>().flatMap { it.days }.filterNotNull()
        assertEquals(30 + 31, days.size)
    }

    @Test
    fun `save waits for both days, a single day counts`() {
        assertFalse(DateRange(null, null).complete)
        assertFalse(DateRange(aug2, null).complete)
        assertTrue(DateRange(aug2, aug2).complete)
        assertTrue(DateRange(aug2, aug9).complete)
    }
}
