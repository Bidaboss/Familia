package fr.familia.app.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

/** The line of the header of the gazette (récit 115): what is on today, and what waits for me. */
class MyDaySummaryTest {
    private val ready = previewHome()
    private fun item(what: String) = DayItem(LocalTime.NOON, what, null, what, DayTone.Organise, DayOpen.Event(what))

    @Test
    fun `a calm day counts nothing today, and the answers that wait`() {
        val days = listOf(HomeDay(ready.today, emptyList()), HomeDay(ready.today.plusDays(1), listOf(item("e1"))))
        assertEquals(MyDaySummary(0, 2), myDaySummary(ready, days, emptyList(), toRate = 0, helpTodos = 0, surprisesOn = false))
    }

    @Test
    fun `the things of today, the coups de main and the surprises to rate add up`() {
        val days = listOf(HomeDay(ready.today, listOf(item("e1"), item("e2"))))
        assertEquals(MyDaySummary(2, 2 + 1 + 3), myDaySummary(ready, days, emptyList(), toRate = 1, helpTodos = 3, surprisesOn = true))
    }

    @Test
    fun `surprises off, nothing of the surprises counts`() {
        val days = listOf(HomeDay(ready.today, emptyList()))
        assertEquals(MyDaySummary(0, 2), myDaySummary(ready, days, emptyList(), toRate = 4, helpTodos = 0, surprisesOn = false))
    }
}
