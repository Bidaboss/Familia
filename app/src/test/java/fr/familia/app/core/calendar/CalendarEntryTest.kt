package fr.familia.app.core.calendar

import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class CalendarEntryTest {

    private val meal = Event(
        id = "e1",
        title = "Repas de famille",
        date = LocalDate.of(2026, 12, 19),
        time = LocalTime.of(12, 30),
        place = "Chez Camille",
        note = "Apporter le dessert",
        target = EventTarget.ForFamily,
        authorId = "a",
        authorName = "Camille",
    )

    @Test
    fun `an event with a time starts at that hour in Paris and lasts an hour`() {
        val entry = CalendarEntry.forEvent(meal, "Créé depuis Familia")
        assertFalse(entry.allDay)
        assertEquals(Instant.parse("2026-12-19T11:30:00Z").toEpochMilli(), entry.beginMillis)
        assertEquals(Instant.parse("2026-12-19T12:30:00Z").toEpochMilli(), entry.endMillis)
        assertEquals("Chez Camille", entry.location)
        assertEquals("Apporter le dessert\n\nCréé depuis Familia", entry.description)
        assertNull(entry.rrule)
    }

    @Test
    fun `in summer, Paris is two hours ahead of UTC`() {
        val entry = CalendarEntry.forEvent(meal.copy(date = LocalDate.of(2027, 7, 14)), "Créé depuis Familia")
        assertEquals(Instant.parse("2027-07-14T10:30:00Z").toEpochMilli(), entry.beginMillis)
    }

    @Test
    fun `an event without a time takes the whole day, and an empty place is left out`() {
        val entry = CalendarEntry.forEvent(meal.copy(time = null, place = " ", note = null), "Créé depuis Familia")
        assertTrue(entry.allDay)
        assertEquals(24 * 3_600_000L, entry.endMillis - entry.beginMillis)
        assertNull(entry.location)
        assertEquals("Créé depuis Familia", entry.description)
    }

    @Test
    fun `a birthday is a whole day that comes back every year`() {
        val member = Member("n", "Noé", null, LocalDate.of(2016, 3, 2), "h1", MemberProfile.ChildWithApp, false, emptyList(), null)
        val entry = CalendarEntry.forBirthday(Birthday(member, LocalDate.of(2027, 3, 2), 11), "Anniversaire de Noé", "Créé depuis Familia")
        assertTrue(entry.allDay)
        assertEquals("Anniversaire de Noé", entry.title)
        assertEquals("FREQ=YEARLY", entry.rrule)
        assertEquals(Instant.parse("2027-03-01T23:00:00Z").toEpochMilli(), entry.beginMillis)
    }
}
