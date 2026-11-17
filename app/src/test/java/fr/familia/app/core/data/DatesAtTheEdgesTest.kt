package fr.familia.app.core.data

import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.upcomingBirthdays
import fr.familia.app.core.navigation.nextEventId
import fr.familia.app.core.ui.formatDay
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.home.homeDays
import fr.familia.app.feature.recap.WeekRecap
import fr.familia.app.feature.wishes.BirthdayWishes
import fr.familia.app.feature.yearago.YearAgo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.TimeZone

/**
 * The days and hours at their edges (PRODUIT, 17/11), on a simulated clock: an event at 0 h 30 or
 * on the whole day, midnight during the meal of the 21st, a birthday on 29 February, « Il y a un
 * an » on 1 January, the recap of Sunday at 18 h on a phone set to another time zone, and the
 * dates written in French. Fictional data.
 */
class DatesAtTheEdgesTest {

    private val me = TestFamily.adult

    /** Runs [block] as if the phone were set to [zone], then puts the phone back. */
    private fun <T> onAPhoneIn(zone: String, block: () -> T): T {
        val before = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        return try { block() } finally { TimeZone.setDefault(before) }
    }

    // Today, midnight and the time zone.

    @Test
    fun `today is the Paris day, whatever the zone of the phone`() {
        // Paris is one hour ahead of UTC in November: 0 h 10 in Paris is still 23 h 10 the day before in UTC.
        val lateInParis = Instant.parse("2026-11-21T22:30:00Z") // 23 h 30 in Paris
        val afterMidnight = Instant.parse("2026-11-21T23:10:00Z") // 0 h 10 in Paris, 23 h 10 in UTC
        onAPhoneIn("America/New_York") {
            assertEquals(LocalDate.of(2026, 11, 21), familyToday(lateInParis))
            assertEquals(LocalDate.of(2026, 11, 22), familyToday(afterMidnight))
        }
        onAPhoneIn("UTC") { assertEquals(LocalDate.of(2026, 11, 22), familyToday(afterMidnight)) }
    }

    @Test
    fun `midnight during the meal, the frise moves on to Sunday and the meal of Saturday leaves it`() {
        val saturday = LocalDate.of(2026, 11, 21)
        val meal = TestFamily.event("meal", me, date = saturday).copy(title = "Repas d'anniversaire", time = LocalTime.of(19, 30))
        val brunch = TestFamily.event("brunch", me, date = saturday.plusDays(1)).copy(title = "Brunch", time = LocalTime.of(11, 0))
        val sunday = familyToday(Instant.parse("2026-11-21T23:05:00Z"))
        val days = homeDays(me, sunday, listOf(meal, brunch), emptyList(), emptyList())
        assertEquals(sunday, days.first().date)
        assertEquals(listOf("Brunch"), days.first().items.map { it.word })
        assertTrue(days.flatMap { it.items }.none { it.what == meal.title })
    }

    // An event at 0 h 30, an event on the whole day.

    @Test
    fun `an event at 0 h 30 comes first in its day, one on the whole day after the timed ones`() {
        val day = LocalDate.of(2026, 11, 22)
        val night = TestFamily.event("night", me, date = day).copy(title = "Feu d'artifice", time = LocalTime.of(0, 30))
        val morning = TestFamily.event("morning", me, date = day).copy(title = "Marché", time = LocalTime.of(9, 0))
        val allDay = TestFamily.event("allDay", me, date = day).copy(title = "Vide-grenier")
        val items = homeDays(me, day, listOf(allDay, morning, night), emptyList(), emptyList()).first().items
        assertEquals(listOf("Feu", "Marché", "Vide"), items.map { it.word })
    }

    @Test
    fun `the next event, at 0 h 15, is the one of 0 h 30, then the one on the whole day until midnight`() {
        val day = LocalDate.of(2026, 11, 22)
        val night = TestFamily.event("night", me, date = day).copy(time = LocalTime.of(0, 30))
        val allDay = TestFamily.event("allDay", me, date = day)
        // A day without an hour comes before the hours of that day, as on the Planning.
        assertEquals("allDay", nextEventId(listOf(night, allDay), LocalDateTime.of(day, LocalTime.of(0, 15))))
        assertEquals("night", nextEventId(listOf(night), LocalDateTime.of(day, LocalTime.of(0, 15))))
        assertNull(nextEventId(listOf(night), LocalDateTime.of(day, LocalTime.of(0, 31))))
        assertEquals("allDay", nextEventId(listOf(night, allDay), LocalDateTime.of(day, LocalTime.of(23, 59))))
        assertNull(nextEventId(listOf(night, allDay), LocalDateTime.of(day.plusDays(1), LocalTime.of(0, 0))))
    }

    // 29 February.

    private fun member(id: String, birth: LocalDate) =
        Member(id, id.replaceFirstChar { it.uppercase() }, null, birth, "h1", MemberProfile.Adult, false, emptyList(), null)

    @Test
    fun `born on 29 February, the birthday is on the 28th in other years, with the right age`() {
        val leap = member("lou", LocalDate.of(2016, 2, 29))
        val in2027 = upcomingBirthdays(listOf(leap), LocalDate.of(2027, 2, 28)).single()
        assertEquals(LocalDate.of(2027, 2, 28), in2027.date)
        assertEquals(11, in2027.age)
        val in2028 = upcomingBirthdays(listOf(leap), LocalDate.of(2028, 2, 28)).single()
        assertEquals(LocalDate.of(2028, 2, 29), in2028.date)
        assertEquals(12, in2028.age)
    }

    @Test
    fun `the cards of vœux of someone born on 29 February belong to the right year`() {
        val born = LocalDate.of(2016, 2, 29)
        assertEquals(2026, BirthdayWishes.lastBirthdayYear(born, LocalDate.of(2027, 2, 27)))
        assertEquals(2027, BirthdayWishes.lastBirthdayYear(born, LocalDate.of(2027, 2, 28)))
        assertEquals(2027, BirthdayWishes.lastBirthdayYear(born, LocalDate.of(2028, 2, 28)))
        assertEquals(2028, BirthdayWishes.lastBirthdayYear(born, LocalDate.of(2028, 2, 29)))
        assertEquals(2028, BirthdayWishes.lastBirthdayYear(born, LocalDate.of(2028, 3, 1)))
    }

    // « Il y a un an » across the change of year, and on 29 February.

    @Test
    fun `on 1 January, a year ago is 1 January, not the 31 December just before`() {
        val newYear = LocalDate.of(2027, 1, 1)
        assertEquals(LocalDate.of(2026, 1, 1), YearAgo.dayOf(newYear))
        assertNull(YearAgo.yearsAgo(newYear, LocalDate.of(2026, 12, 31)))
        assertNull(YearAgo.yearsAgo(newYear, LocalDate.of(2025, 12, 31)))
        // The day is the Paris one: the evening of 31 December in UTC is already the 1st.
        val (from, to) = YearAgo.bounds(newYear).first()
        assertEquals(Instant.parse("2025-12-31T23:00:00Z"), from)
        assertEquals(Instant.parse("2026-01-01T23:00:00Z"), to)
    }

    @Test
    fun `on 29 February, a year ago is the 28th, and the next year the 1st of March is not the 29th`() {
        assertEquals(LocalDate.of(2027, 2, 28), YearAgo.dayOf(LocalDate.of(2028, 2, 29)))
        assertEquals(1, YearAgo.yearsAgo(LocalDate.of(2028, 2, 29), LocalDate.of(2027, 2, 28)))
        assertNull(YearAgo.yearsAgo(LocalDate.of(2029, 3, 1), LocalDate.of(2028, 2, 29)))
    }

    // The recap of Sunday at 18 h, on a phone in another zone.

    private fun news(id: String, author: String, at: Instant) = GoodNews(
        id = id,
        text = "",
        photoIds = listOf("p1"),
        authorId = author,
        authorName = author,
        forMemberId = author,
        householdId = "h1",
        createdAt = at,
    )

    @Test
    fun `the recap of Sunday 18 h counts its days in Paris, on a phone set to New York`() {
        val sunday18h = Instant.parse("2026-11-22T17:00:00Z")
        val list = listOf(
            // Sunday before, 18 h 30 in Paris: inside the 7 days.
            news("in", "camille", Instant.parse("2026-11-15T17:30:00Z")),
            // Sunday before, 17 h 30 in Paris: more than 7 days ago.
            news("out", "ines", Instant.parse("2026-11-15T16:30:00Z")),
            // Friday 23 h 30 and Saturday 0 h 30 in Paris: two days, though the same day in New York.
            news("friday", "noe", Instant.parse("2026-11-20T22:30:00Z")),
            news("saturday", "lou", Instant.parse("2026-11-20T23:30:00Z")),
        )
        val ids = onAPhoneIn("America/New_York") { WeekRecap.photos(list, "sam", sunday18h).map { it.newsId } }
        assertEquals(listOf("saturday", "friday", "in"), ids)
    }

    // The dates in French.

    @Test
    fun `the dates are written in French, the first of the month as 1er`() {
        val today = LocalDate.of(2026, 11, 17)
        assertEquals("Samedi 21 novembre", formatDay(LocalDate.of(2026, 11, 21), today))
        assertEquals("Dimanche 1er novembre", formatDay(LocalDate.of(2026, 11, 1), today))
        assertEquals("Vendredi 1er janvier 2027", formatDay(LocalDate.of(2027, 1, 1), today))
        assertEquals("1er décembre 2026", frenchDate(LocalDate.of(2026, 12, 1), "d MMMM yyyy"))
        assertEquals("12 décembre", frenchDate(LocalDate.of(2026, 12, 12), "d MMMM"))
        assertEquals("21 août", frenchDate(LocalDate.of(2026, 8, 21), "d MMMM"))
        // Two-figure days are left as they are.
        assertEquals("01/12", frenchDate(LocalDate.of(2026, 12, 1), "dd/MM"))
    }

    @Test
    fun `the dates stay in French on a phone set to English`() {
        val before = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.US)
        try {
            assertEquals("Samedi 21 novembre", formatDay(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 17)))
        } finally {
            java.util.Locale.setDefault(before)
        }
    }
}
