package fr.familia.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The same days as the server (functions/test/series.test.ts), so the app and it agree. */
class SeriesRuleTest {

    private fun d(text: String) = LocalDate.parse(text)
    private fun days(rule: SeriesRule, to: String) = rule.days(d(to)).map { it.toString() }

    @Test
    fun `every week and every 2 weeks`() {
        assertEquals(listOf("2027-01-05", "2027-01-12", "2027-01-19"), days(SeriesRule(RepeatFreq.Weekly, start = d("2027-01-05")), "2027-01-20"))
        assertEquals(listOf("2027-01-05", "2027-01-19"), days(SeriesRule(RepeatFreq.Weekly, 2, start = d("2027-01-05")), "2027-01-20"))
    }

    @Test
    fun `the 31 of each month only where it exists`() {
        val rule = SeriesRule(RepeatFreq.Monthly, 1, MonthlyBy.Date, d("2027-01-31"))
        assertEquals(listOf("2027-01-31", "2027-03-31", "2027-05-31"), days(rule, "2027-06-30"))
    }

    @Test
    fun `a 5th Sunday only in the months that have one`() {
        val rule = SeriesRule(RepeatFreq.Monthly, 1, MonthlyBy.Weekday, d("2027-01-31"))
        assertEquals(listOf("2027-01-31", "2027-05-30"), days(rule, "2027-06-30"))
    }

    @Test
    fun `the 1st Sunday of each month`() {
        val rule = SeriesRule(RepeatFreq.Monthly, 1, MonthlyBy.Weekday, d("2027-01-03"))
        assertEquals(listOf("2027-01-03", "2027-02-07", "2027-03-07"), days(rule, "2027-03-31"))
    }

    @Test
    fun `a 29 February only in leap years`() {
        val rule = SeriesRule(RepeatFreq.Yearly, start = d("2028-02-29"))
        assertEquals(listOf("2028-02-29", "2032-02-29"), days(rule, "2033-01-01"))
    }

    @Test
    fun `an end day and a number of times`() {
        assertEquals(listOf("2027-01-05", "2027-01-12"), days(SeriesRule(RepeatFreq.Weekly, start = d("2027-01-05"), until = d("2027-01-15")), "2027-12-31"))
        assertEquals(3, SeriesRule(RepeatFreq.Weekly, start = d("2027-01-05"), count = 3).days(d("2027-12-31")).size)
    }

    @Test
    fun `the days still to come before a day leave out the past and those taken out`() {
        val series = EventSeries(
            "s1", "Piscine", null, null, null, EventTarget.ForFamily, "a", "Camille",
            SeriesRule(RepeatFreq.Weekly, start = d("2027-01-05")), exdates = listOf(d("2027-01-19")),
        )
        assertEquals(listOf(d("2027-01-12")), series.comingBefore(d("2027-01-26"), today = d("2027-01-10")))
        assertEquals(emptyList<LocalDate>(), series.comingBefore(d("2027-01-12"), today = d("2027-01-10")))
    }

    @Test
    fun `a choice and its end come back from the rule`() {
        val start = d("2027-01-03")
        RepeatChoice.entries.filter { it != RepeatChoice.None }.forEach { choice ->
            listOf(RepeatEnd.Never, RepeatEnd.OnDay(d("2027-06-01")), RepeatEnd.After(5)).forEach { end ->
                assertEquals(choice to end, choiceOf(ruleFor(choice, end, start)!!))
            }
        }
        assertNull(ruleFor(RepeatChoice.None, RepeatEnd.Never, start))
    }
}
