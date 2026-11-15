package fr.familia.app.feature.events

import fr.familia.app.core.model.EventTarget
import fr.familia.app.feature.events.TestFamily.admin
import fr.familia.app.feature.events.TestFamily.adult
import fr.familia.app.feature.events.TestFamily.event
import fr.familia.app.feature.events.TestFamily.family
import fr.familia.app.feature.events.TestFamily.kid
import fr.familia.app.feature.events.TestFamily.otherAdult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same cases as firebase/tests/events.test.mjs, from the app's side. */
class EventRightsTest {

    @Test
    fun `an adult targets the family, their household, themselves and their children`() {
        assertEquals(
            listOf(EventTarget.ForFamily, EventTarget.ForHousehold("h1"), EventTarget.ForSelf("adult1"), EventTarget.ForChild("kid1")),
            allowedTargets(adult, family),
        )
    }

    @Test
    fun `a child with the app targets themselves only`() {
        assertEquals(listOf(EventTarget.ForSelf("kid1")), allowedTargets(kid, family))
    }

    @Test
    fun `the admin principal targets every household and every child`() {
        val targets = allowedTargets(admin, family)
        assertTrue(EventTarget.ForHousehold("h2") in targets)
        assertTrue(EventTarget.ForChild("kid2") in targets)
    }

    @Test
    fun `everyone manages their own events, the admin principal all of them`() {
        val byOther = event("e", otherAdult)
        assertTrue(canManage(byOther, otherAdult, family))
        assertTrue(canManage(byOther, admin, family))
        assertFalse(canManage(byOther, adult, family))
    }

    @Test
    fun `a chef de famille has an adult's targets`() {
        val chef = otherAdult.copy(isAdmin = true)
        val withChef = family.copy(members = family.members.map { if (it.id == chef.id) chef else it })
        assertEquals(
            listOf(EventTarget.ForFamily, EventTarget.ForHousehold("h2"), EventTarget.ForSelf("adult2"), EventTarget.ForChild("kid2")),
            allowedTargets(chef, withChef),
        )
    }

    @Test
    fun `a chef de famille manages the events written by their household only`() {
        val chef = otherAdult.copy(isAdmin = true)
        val withChef = family.copy(members = family.members.map { if (it.id == chef.id) chef else it })
        val byKidOfH2 = event("a", TestFamily.otherKid)
        assertTrue(canManage(byKidOfH2, chef, withChef))
        assertFalse(canManage(event("b", adult), chef, withChef))
        // Without the admin principal in config/family, a chef of h1 is not admin principal.
        val noOwner = family.copy(ownerId = null)
        assertFalse(canManage(event("c", otherAdult), admin, noOwner))
        assertTrue(canManage(event("d", adult), admin, noOwner))
    }

    @Test
    fun `a parent manages what their child wrote and what was written for them`() {
        assertTrue(canManage(event("a", kid, EventTarget.ForSelf("kid1")), adult, family))
        assertTrue(canManage(event("b", otherAdult, EventTarget.ForChild("kid1")), adult, family))
        assertFalse(canManage(event("c", otherAdult, EventTarget.ForChild("kid2")), adult, family))
    }

    @Test
    fun `a child never manages an adult's event`() {
        assertFalse(canManage(event("e", adult), kid, family))
    }

    @Test
    fun `a removed author is shown with the name saved on the event`() {
        val gone = event("e", adult).copy(authorId = "gone", authorName = "Ancien")
        assertEquals("Ancien", authorDisplayName(gone, family))
        assertEquals("Adult1", authorDisplayName(event("f", adult).copy(authorName = "Old name"), family))
    }

    @Test
    fun `an adult answers for themselves, then their children`() {
        assertEquals(listOf("adult1", "kid1"), answerableMembers(adult, family).map { it.id })
    }

    @Test
    fun `a child answers for themselves only, and an admin gets no extra answers`() {
        assertEquals(listOf("kid1"), answerableMembers(kid, family).map { it.id })
        assertEquals(listOf("admin1"), answerableMembers(admin, family).map { it.id })
    }
}
