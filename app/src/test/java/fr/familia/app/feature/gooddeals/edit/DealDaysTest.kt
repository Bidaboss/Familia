package fr.familia.app.feature.gooddeals.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DealDaysTest {
    private val thursday = LocalDate.of(2026, 11, 19)
    private val sunday = LocalDate.of(2026, 11, 22)

    @Test
    fun `the weekend is the coming Sunday`() {
        assertEquals(sunday, DealDay.Weekend.dayFrom(thursday))
        assertEquals(sunday, DealDay.Weekend.dayFrom(LocalDate.of(2026, 11, 21)))
        assertEquals(LocalDate.of(2026, 11, 29), DealDay.Weekend.dayFrom(LocalDate.of(2026, 11, 23)))
    }

    @Test
    fun `on a Sunday the weekend is today and only Today is lit`() {
        assertEquals(sunday, DealDay.Weekend.dayFrom(sunday))
        assertEquals(DealDay.Today, dealDayOf(sunday, sunday))
    }

    @Test
    fun `in a week is 7 days on and stays within the 3 months`() {
        assertEquals(LocalDate.of(2026, 11, 26), DealDay.InAWeek.dayFrom(thursday))
        DealDay.entries.forEach { assert(!it.dayFrom(thursday).isAfter(thursday.plusDays(90))) }
    }

    @Test
    fun `a day of the calendar matches no pill`() {
        assertEquals(DealDay.Weekend, dealDayOf(sunday, thursday))
        assertNull(dealDayOf(LocalDate.of(2026, 12, 3), thursday))
        assertNull(dealDayOf(null, thursday))
    }
}
