package fr.familia.app.feature.home

import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.HelpStatus
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.helprequests.helpRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HomeDaysTest {

    private val today = LocalDate.of(2026, 11, 17)
    private val me = TestFamily.adult

    @Test
    fun `seven days from today, empty ones included`() {
        val days = homeDays(me, today, emptyList(), emptyList(), emptyList())
        assertEquals(7, days.size)
        assertEquals(today, days.first().date)
        assertEquals(today.plusDays(6), days.last().date)
        assertTrue(days.all { it.items.isEmpty() })
    }

    @Test
    fun `a birthday first, then the events by hour, the ones without an hour last`() {
        val day = today.plusDays(1)
        val late = TestFamily.event("e1", me, date = day).copy(title = "Le judo de Lou", time = LocalTime.of(18, 0))
        val early = TestFamily.event("e2", me, date = day).copy(title = "Dentiste", time = LocalTime.of(9, 0))
        val noHour = TestFamily.event("e3", me, date = day).copy(title = "Repas de famille")
        val birthday = Birthday(TestFamily.otherAdult, day, 40)
        val items = homeDays(me, today, listOf(late, noHour, early), emptyList(), listOf(birthday))[1].items
        assertEquals(listOf("Adult2", "Dentiste", "Judo", "Repas"), items.map { it.word })
        assertEquals(listOf(DayTone.Party, DayTone.Organise, DayTone.Organise, DayTone.Party), items.map { it.tone })
    }

    @Test
    fun `a help request open or taken by me shows, in green, and taken by another it does not`() {
        val open = helpRequest("h1", TestFamily.otherAdult, listOf(me.id), date = today).copy(title = "Garder Noé")
        val mine = helpRequest("h2", TestFamily.otherAdult, listOf(me.id), date = today, status = HelpStatus.Taken).copy(volunteerId = me.id)
        val other = helpRequest("h3", TestFamily.otherAdult, listOf(me.id), date = today, status = HelpStatus.Taken).copy(volunteerId = "x")
        val items = homeDays(me, today, emptyList(), listOf(open, mine, other), emptyList()).first().items
        assertEquals(listOf(DayOpen.Help("h1"), DayOpen.Help("h2")), items.map { it.open })
        assertTrue(items.all { it.tone == DayTone.Help })
    }

    @Test
    fun `one short word, without the small words`() {
        assertEquals("Repas", shortWord("Repas d'anniversaire de Sam"))
        assertEquals("Judo", shortWord("Le judo"))
        assertEquals("Quiz", shortWord("Quiz"))
        assertEquals("Anniversa", shortWord("L'anniversaire", max = 9))
        assertEquals("", shortWord("   "))
    }
}
