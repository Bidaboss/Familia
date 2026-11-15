package fr.familia.app.feature.helprequests

import fr.familia.app.core.model.HelpStatus
import fr.familia.app.feature.events.TestFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** TestFamily: admin1 (admin principal) and adult1 in h1, adult2 in h2, kid1 (parent adult1), kid2 (parent adult2). */
class HelpRightsTest {
    private val family = TestFamily.family
    private val today = LocalDate.of(2026, 12, 1)

    @Test
    fun `an adult asks for themselves or their children, a child for themselves, the admin principal for anyone`() {
        assertEquals(listOf("adult1", "kid1"), helpTargets(TestFamily.adult, family).map { it.id })
        assertEquals(listOf("kid1"), helpTargets(TestFamily.kid, family).map { it.id })
        assertEquals(5, helpTargets(TestFamily.admin, family).size)
    }

    @Test
    fun `only adults can be warned, never the author`() {
        assertEquals(listOf("admin1", "adult2"), notifiableAdults(TestFamily.adult, family).map { it.id })
    }

    @Test
    fun `the parents of a child are always warned, except the author`() {
        assertEquals(setOf("adult1"), requiredNotifyIds("kid1", "kid1", family))
        assertEquals(emptySet<String>(), requiredNotifyIds("adult1", "kid1", family))
        assertEquals(emptySet<String>(), requiredNotifyIds("adult1", "adult1", family))
    }

    @Test
    fun `a warned adult volunteers, not the author nor a child`() {
        val request = helpRequest("r1", TestFamily.adult, listOf("adult2", "kid1"))
        assertTrue(canVolunteer(request, TestFamily.otherAdult))
        assertFalse(canVolunteer(request, TestFamily.adult))
        assertFalse(canVolunteer(request, TestFamily.kid))
        assertFalse(canVolunteer(request, TestFamily.admin))
        assertFalse(canVolunteer(request.copy(status = HelpStatus.Taken), TestFamily.otherAdult))
    }

    @Test
    fun `only the single person warned may decline`() {
        assertTrue(canDecline(helpRequest("r1", TestFamily.adult, listOf("adult2")), TestFamily.otherAdult))
        assertFalse(canDecline(helpRequest("r1", TestFamily.adult, listOf("adult2", "admin1")), TestFamily.otherAdult))
    }

    @Test
    fun `the author, the parent of the child and the admin principal manage a request`() {
        val request = helpRequest("r1", TestFamily.kid, listOf("adult1", "adult2"))
        assertTrue(canManageHelp(request, TestFamily.kid, family))
        assertTrue(canManageHelp(request, TestFamily.adult, family))
        assertTrue(canManageHelp(request, TestFamily.admin, family))
        assertFalse(canManageHelp(request, TestFamily.otherAdult, family))
    }

    @Test
    fun `« Merci » from the day of the request, once`() {
        val taken = helpRequest("r1", TestFamily.adult, listOf("adult2"), date = today, status = HelpStatus.Taken, volunteer = TestFamily.otherAdult)
        assertTrue(canThank(taken, TestFamily.adult, family, today))
        assertFalse(canThank(taken, TestFamily.adult, family, today.minusDays(1)))
        assertFalse(canThank(taken.copy(thankedAt = java.time.Instant.EPOCH), TestFamily.adult, family, today))
        assertFalse(canThank(taken, TestFamily.otherAdult, family, today))
    }

    @Test
    fun `a past request leaves the list the next day, except to say « Merci » for a week`() {
        val past = helpRequest("r1", TestFamily.adult, listOf("adult2"), date = today.minusDays(1))
        assertFalse(isListed(past, TestFamily.adult, family, today))
        val taken = past.copy(status = HelpStatus.Taken, volunteerId = "adult2", volunteerName = "Adult2")
        assertTrue(isListed(taken, TestFamily.adult, family, today))
        assertFalse(isListed(taken, TestFamily.otherAdult, family, today))
        assertFalse(isListed(taken, TestFamily.adult, family, today.plusDays(8)))
        assertFalse(isListed(helpRequest("r2", TestFamily.adult, listOf("adult2"), status = HelpStatus.Cancelled), TestFamily.adult, family, today))
    }

    @Test
    fun `the badge counts the open requests I can still take`() {
        val requests = listOf(
            helpRequest("r1", TestFamily.adult, listOf("adult2")),
            helpRequest("r2", TestFamily.adult, listOf("adult2"), status = HelpStatus.Taken, volunteer = TestFamily.admin),
            helpRequest("r3", TestFamily.adult, listOf("adult2"), date = today.minusDays(1)),
            helpRequest("r4", TestFamily.adult, listOf("adult2")).copy(declinedBy = "adult2"),
            helpRequest("r5", TestFamily.adult, listOf("admin1")),
        )
        assertEquals(1, badgeCount(requests, TestFamily.otherAdult, today))
    }
}
