package fr.familia.app.core.navigation

import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class NextEventTest {
    private val now = LocalDateTime.of(2026, 11, 21, 15, 0)

    private fun event(id: String, date: LocalDate, time: LocalTime? = null) = Event(
        id = id,
        title = id,
        date = date,
        time = time,
        place = null,
        note = null,
        target = EventTarget.ForSelf("camille"),
        authorId = "camille",
        authorName = "Camille",
    )

    @Test
    fun `nothing to come gives null, so Voir où opens the Planning`() {
        assertNull(nextEventId(emptyList(), now))
        assertNull(nextEventId(listOf(event("midi", now.toLocalDate(), LocalTime.NOON)), now))
    }

    @Test
    fun `an event of today whose time has passed is skipped`() {
        val events = listOf(
            event("midi", now.toLocalDate(), LocalTime.NOON),
            event("soir", now.toLocalDate(), LocalTime.of(19, 30)),
        )
        assertEquals("soir", nextEventId(events, now))
    }

    @Test
    fun `an event of today with no time still counts, before the ones with a time`() {
        val events = listOf(
            event("soir", now.toLocalDate(), LocalTime.of(19, 30)),
            event("journee", now.toLocalDate()),
        )
        assertEquals("journee", nextEventId(events, now))
    }

    @Test
    fun `the closest day comes first, whatever the order of the list`() {
        val events = listOf(
            event("noel", LocalDate.of(2026, 12, 25)),
            event("demain", LocalDate.of(2026, 11, 22), LocalTime.of(10, 0)),
        )
        assertEquals("demain", nextEventId(events, now))
    }
}
