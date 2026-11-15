package fr.familia.app.core.model

import fr.familia.app.feature.events.TestFamily
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudienceTest {

    private val family = TestFamily.family
    private val me = TestFamily.adult // adult1, household h1, parent of kid1

    @Test
    fun `an event for the whole family concerns everyone`() {
        assertTrue(concerns(EventTarget.ForFamily, me, family))
        assertTrue(concerns(EventTarget.ForFamily, TestFamily.otherAdult, family))
    }

    @Test
    fun `my household, its members and its children are in my scope`() {
        assertTrue(concerns(EventTarget.ForSelf("adult1"), me, family))
        assertTrue(concerns(EventTarget.ForSelf("admin1"), me, family))
        assertTrue(concerns(EventTarget.ForHousehold("h1"), me, family))
        assertTrue(concerns(EventTarget.ForChild("kid1"), me, family))
    }

    @Test
    fun `another household is not`() {
        assertFalse(concerns(EventTarget.ForHousehold("h2"), me, family))
        assertFalse(concerns(EventTarget.ForSelf("adult2"), me, family))
        assertFalse(concerns(EventTarget.ForChild("kid2"), me, family))
    }

    @Test
    fun `a child I am responsible for counts, even in another household`() {
        val stepParent = TestFamily.otherAdult
        val withStepParent = family.copy(
            members = family.members.map { if (it.id == "kid1") it.copy(parentIds = listOf("adult1", "adult2")) else it },
        )
        assertTrue(concerns(EventTarget.ForChild("kid1"), stepParent, withStepParent))
        assertFalse(concerns(EventTarget.ForHousehold("h1"), stepParent, withStepParent))
    }

    @Test
    fun `a member who left the family is in nobody's scope but their own`() {
        assertFalse(concerns(EventTarget.ForSelf("ghost"), me, family))
    }
}
