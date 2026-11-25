package fr.familia.app.feature.feed

import fr.familia.app.core.data.GazetteThread
import fr.familia.app.core.data.gazetteThreadId
import fr.familia.app.core.model.EventComment
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** J'aime and comments of the cards of the gazette (récit 115). */
class FeedSocialTest {
    @Test
    fun `the thread of a card is its key, the slashes of a folder turned into colons`() {
        assertEquals("album:trips:t1:2026-11-20", gazetteThreadId("album:trips/t1:2026-11-20"))
        assertEquals("help:r1", gazetteThreadId("help:r1"))
    }

    @Test
    fun `the J'aime shown first are the most given, then in the order of the four`() {
        val thread = GazetteThread(reactions = mapOf("a" to "🔥", "b" to "💛", "c" to "🔥", "d" to "🎉"))
        assertEquals(listOf("🔥", "💛", "🎉"), reactionSummary(thread))
        assertEquals(emptyList<String>(), reactionSummary(GazetteThread()))
    }

    @Test
    fun `my own J'aime is read from the map`() {
        val thread = GazetteThread(reactions = mapOf("a" to "👏"))
        assertEquals("👏", thread.reactionOf("a"))
        assertEquals(null, thread.reactionOf("b"))
    }

    private fun member(id: String, profile: MemberProfile = MemberProfile.Adult, parents: List<String> = emptyList(), admin: Boolean = false, household: String = "h1") =
        Member(id, id, null, null, household, profile, isAdmin = admin, parentIds = parents, uid = null)

    @Test
    fun `a comment of the gazette is removed by its author, who looks after its child author, or the admin principal`() {
        val owner = member("owner", household = "h2")
        val parent = member("parent")
        val other = member("other")
        val chef = member("chef", admin = true)
        val otherChef = member("otherChef", admin = true, household = "h2")
        val child = member("child", MemberProfile.ChildWithApp, parents = listOf("parent"))
        val family = Family(emptyList(), listOf(owner, parent, other, chef, otherChef, child), ownerId = "owner")
        val ofChild = EventComment("c1", "Trop bien", "child", "child", null)
        val ofOther = EventComment("c2", "Oui", "other", "other", null)
        assertTrue(canRemoveGazetteComment(family, ofChild, child))
        assertTrue(canRemoveGazetteComment(family, ofChild, parent))
        assertTrue(canRemoveGazetteComment(family, ofChild, owner))
        assertFalse(canRemoveGazetteComment(family, ofChild, other))
        // The chef de famille of the child's household too, as for the photos of an album (règle du 25/11).
        assertTrue(canRemoveGazetteComment(family, ofChild, chef))
        assertFalse(canRemoveGazetteComment(family, ofChild, otherChef))
        // Never what another adult wrote (récit 121).
        assertFalse(canRemoveGazetteComment(family, ofOther, parent))
    }
}
