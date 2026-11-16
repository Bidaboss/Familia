package fr.familia.app.core.viewas

import fr.familia.app.core.data.NotificationRepository
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.feature.events.FakeEvents
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.FakeRsvps
import fr.familia.app.feature.events.TestFamily
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** « Voir comme » (PRD, rôles): reads pass, writes never reach the real repository. */
class ReadOnlyRepositoriesTest {

    private var refused = 0
    private val guard = ViewAsGuard { refused++ }

    private suspend fun assertRefused(write: suspend () -> Unit) {
        try {
            write()
            fail("the write should have been refused")
        } catch (e: ReadOnlyViewException) {
            // Expected: nothing written.
        }
    }

    @Test
    fun `an event is read, never saved nor deleted`() = runTest {
        val event = TestFamily.event("e1", TestFamily.adult)
        val real = FakeEvents(listOf(event))
        val events = ReadOnlyEventRepository(real, guard)
        assertEquals(event, events.observeEvent("e1").first())
        assertEquals(event, events.getEvent("e1"))
        assertRefused { events.saveEvent(event.copy(title = "Autre"), isNew = false) }
        assertRefused { events.deleteEvent("e1") }
        assertTrue(real.saved.isEmpty())
        assertTrue(real.deleted.isEmpty())
        assertEquals(2, refused)
    }

    @Test
    fun `no answer is given for anyone`() = runTest {
        val real = FakeRsvps()
        val rsvps = ReadOnlyRsvpRepository(real, guard)
        assertRefused { rsvps.setRsvp("e1", "adult1", RsvpStatus.Yes, "adult1") }
        assertTrue(real.answers.value.isEmpty())
        assertTrue(rsvps.observeRsvps("e1").first().isEmpty())
    }

    @Test
    fun `the family is read, no member is changed`() = runTest {
        val members = ReadOnlyMemberRepository(FakeMembers(), guard)
        assertEquals(TestFamily.family, members.observeFamily().first())
        assertRefused { members.saveMember(TestFamily.adult.copy(firstName = "Autre"), null) }
        assertRefused { members.removeMember(TestFamily.adult, emptyList()) }
        assertRefused { members.linkAccount("adult1", "uid") }
        assertEquals(3, refused)
    }

    @Test
    fun `the phone is never registered for the member looked at, and silently`() = runTest {
        var registered = false
        val real = object : NotificationRepository {
            override suspend fun registerThisDevice(memberId: String) { registered = true }
            override fun observeDayBeforeReminder(memberId: String): Flow<Boolean> = flowOf(false)
            override suspend fun setDayBeforeReminder(memberId: String, enabled: Boolean) = fail("written")
        }
        val notifications = ReadOnlyNotificationRepository(real, guard)
        notifications.registerThisDevice("adult1")
        assertFalse(registered)
        assertEquals(0, refused)
        assertFalse(notifications.observeDayBeforeReminder("adult1").first())
        assertRefused { notifications.setDayBeforeReminder("adult1", true) }
        assertEquals(1, refused)
    }
}
