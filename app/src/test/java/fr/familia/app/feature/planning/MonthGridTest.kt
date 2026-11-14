package fr.familia.app.feature.planning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthGridTest {

    @Test
    fun `weeks start on Monday, padded so every row has seven cells`() {
        val cells = monthCells(YearMonth.of(2026, 11)) // 1 November 2026 is a Sunday.
        assertEquals(42, cells.size)
        (0 until 6).forEach { assertNull(cells[it]) }
        assertEquals(LocalDate.of(2026, 11, 1), cells[6])
        assertEquals(LocalDate.of(2026, 11, 30), cells[35])
        assertNull(cells[36])
    }

    @Test
    fun `a month starting on Monday and ending on Sunday needs no padding`() {
        val cells = monthCells(YearMonth.of(2027, 2))
        assertEquals(28, cells.size)
        assertEquals(LocalDate.of(2027, 2, 1), cells.first())
        assertEquals(LocalDate.of(2027, 2, 28), cells.last())
    }
}
