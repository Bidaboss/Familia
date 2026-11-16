package fr.familia.app.feature.events

import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.feature.events.album.eventAlbumRights
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The album of an event (récit 41): e1 by adult1 on 24 December, adult2 says « je viens ». */
class EventAlbumRightsTest {

    private val christmas = LocalDate.of(2026, 12, 24)
    private val events = FakeEvents(listOf(TestFamily.event("e1", TestFamily.adult, date = christmas)))
    private val rsvps = FakeRsvps().apply {
        answers.value = mapOf(
            "e1" to listOf(Rsvp("adult2", RsvpStatus.Yes, "adult2"), Rsvp("kid1", RsvpStatus.Maybe, "adult1")),
        )
    }

    private suspend fun rights(me: String, today: LocalDate) =
        eventAlbumRights(events, FakeMembers(), rsvps, "e1", me, flowOf(today)).first()

    @Test
    fun `those who said je viens add photos from the day of the event`() = runTest {
        assertFalse(rights("adult2", christmas.minusDays(1)).canAdd)
        assertTrue(rights("adult2", christmas).canAdd)
        assertTrue(rights("adult2", christmas.plusDays(30)).canAdd)
    }

    @Test
    fun `a maybe or no answer only sees the album`() = runTest {
        assertFalse(rights("kid1", christmas).canAdd)
        assertFalse(rights("adult1", christmas).canAdd)
    }

    @Test
    fun `the author of the event and the admin principal remove any photo`() = runTest {
        assertTrue(rights("adult1", christmas).canManage)
        assertTrue(rights("admin1", christmas).canManage)
        assertFalse(rights("adult2", christmas).canManage)
    }

    @Test
    fun `a deleted event has no album`() = runTest {
        events.events.value = emptyList()
        assertNull(rights("adult2", christmas).me)
    }
}
