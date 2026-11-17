package fr.familia.app.core.model

import fr.familia.app.feature.events.TestFamily
import org.junit.Assert.assertEquals
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

    // --- Cercles, étape 1 (PRD rév. 238): one audience, no change. « Famille A » and « Famille B ».

    @Test
    fun `naming no circle is the circle « principal »`() {
        assertEquals(listOf(MAIN_CIRCLE), circlesOf(null))
        assertEquals(listOf(MAIN_CIRCLE), circlesOf(emptyList()))
        assertEquals(listOf(MAIN_CIRCLE), circlesOf(listOf("")))
        assertEquals(listOf("familleA", "familleB"), circlesOf(listOf("familleA", "familleB")))
    }

    @Test
    fun `with the circles off everyone reaches everyone, with them on only a shared circle`() {
        assertTrue(inAudience(listOf("familleA"), listOf("familleB"), circlesOn = false))
        assertFalse(inAudience(listOf("familleA"), listOf("familleB"), circlesOn = true))
        assertTrue(inAudience(listOf("familleA", "familleB"), listOf("familleB"), circlesOn = true))
        assertTrue(inAudience(null, null, circlesOn = true))
        assertFalse(inAudience(listOf("familleA"), null, circlesOn = true))
    }

    @Test
    fun `« toute la famille » is today every member`() {
        assertEquals(family.members, familyAudience(family))
    }

    @Test
    fun `a gift list is read as the rule canSeeList says`() {
        fun list(kind: GiftListKind, beneficiary: String, viewers: List<String> = emptyList(), revealed: Boolean = false) =
            GiftList("l", kind, "Liste", null, null, beneficiary, viewers, beneficiary, beneficiary, revealed = revealed)
        assertTrue(canSeeList(list(GiftListKind.Wishes, "adult1"), "adult1"))
        assertTrue(canSeeList(list(GiftListKind.Ideas, "adult1"), "adult2"))
        assertFalse(canSeeList(list(GiftListKind.Ideas, "adult1"), "adult1"))
        assertFalse(canSeeList(list(GiftListKind.Surprise, "adult1", listOf("adult2")), "adult1"))
        assertTrue(canSeeList(list(GiftListKind.Surprise, "adult1", listOf("adult2")), "adult2"))
        assertTrue(canSeeList(list(GiftListKind.Surprise, "adult1", listOf("adult2"), revealed = true), "adult1"))
    }
}
