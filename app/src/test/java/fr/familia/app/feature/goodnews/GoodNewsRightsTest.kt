package fr.familia.app.feature.goodnews

import fr.familia.app.core.model.EventComment
import fr.familia.app.core.model.Reaction
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.goodnews.edit.GoodNewsForm
import fr.familia.app.feature.goodnews.edit.GoodNewsFormError
import fr.familia.app.feature.goodnews.edit.validate
import fr.familia.app.feature.goodnews.edit.withPhotos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TestFamily: admin1 (admin principal, h1), adult1 (h1), adult2 (h2), kid1 (h1, parent adult1), kid2 (h2, parent adult2). */
class GoodNewsRightsTest {

    private val family = TestFamily.family

    @Test
    fun `an adult publishes for themselves and the children of their household, a child for themselves`() {
        assertEquals(listOf("adult1", "kid1"), goodNewsTargets(TestFamily.adult, family).map { it.id })
        assertEquals(listOf("adult2", "kid2"), goodNewsTargets(TestFamily.otherAdult, family).map { it.id })
        assertEquals(listOf("kid1"), goodNewsTargets(TestFamily.kid, family).map { it.id })
    }

    @Test
    fun `everyone else can be named`() {
        assertEquals(listOf("admin1", "adult2", "kid1", "kid2"), mentionCandidates(TestFamily.adult, family).map { it.id })
    }

    @Test
    fun `the author, the parents, a chef there and the admin principal remove it (AVIS-12)`() {
        val byKid = goodNews("n1", TestFamily.kid)
        assertTrue(canRemoveGoodNews(byKid, TestFamily.kid, family))
        assertTrue(canRemoveGoodNews(byKid, TestFamily.adult, family))
        assertTrue(canRemoveGoodNews(byKid, TestFamily.admin, family))
        assertFalse(canRemoveGoodNews(byKid, TestFamily.otherAdult, family))
        val forKid = goodNews("n2", TestFamily.otherAdult, forMemberId = "kid2")
        assertFalse(canRemoveGoodNews(forKid, TestFamily.adult, family))
        assertTrue(canEditGoodNews(forKid, TestFamily.otherAdult))
        assertFalse(canEditGoodNews(forKid, TestFamily.admin))
    }

    @Test
    fun `a comment is removed by its author, the author of the news, a parent and the admin principal (récit 67)`() {
        val news = goodNews("n1", TestFamily.otherAdult)
        val byKid = EventComment("c1", "Bravo", "kid1", "Kid", null)
        assertTrue(canRemoveGoodNewsComment(news, byKid, TestFamily.kid, family))
        assertTrue(canRemoveGoodNewsComment(news, byKid, TestFamily.otherAdult, family))
        assertTrue(canRemoveGoodNewsComment(news, byKid, TestFamily.adult, family))
        assertTrue(canRemoveGoodNewsComment(news, byKid, TestFamily.admin, family))
        val byAdult = EventComment("c2", "Bravo", "adult1", "Adult", null)
        assertFalse(canRemoveGoodNewsComment(news, byAdult, TestFamily.kid, family))
    }

    @Test
    fun `the « Défi réussi » of Familia, only the admin principal removes it, nobody edits it (récit 35)`() {
        val defi = goodNews("challenge-2026-12", TestFamily.admin).copy(
            authorId = "familia", authorName = "Familia", forMemberId = "familia", householdId = "", challengeMonth = "2026-12",
        )
        assertTrue(canRemoveGoodNews(defi, TestFamily.admin, family))
        assertFalse(canRemoveGoodNews(defi, TestFamily.adult, family))
        assertFalse(canEditGoodNews(defi, TestFamily.admin))
        val item = goodNewsItem(defi, TestFamily.adult, family)
        assertNull(item.author)
        assertFalse(item.canHide)
    }

    @Test
    fun `the card counts the six reactions in order and knows mine`() {
        val news = goodNews("n1", TestFamily.adult, forMemberId = "kid1")
            .copy(reactions = mapOf("adult2" to Reaction.Heart, "admin1" to Reaction.Heart, "kid1" to Reaction.Party), mentionIds = listOf("kid2", "gone"))
        val item = goodNewsItem(news, TestFamily.otherAdult, family)
        assertEquals(Reaction.entries, item.reactionCounts.map { it.reaction })
        assertEquals(listOf(2, 0, 1, 0, 0, 0), item.reactionCounts.map { it.count })
        assertEquals(Reaction.Heart, item.myReaction)
        assertEquals("Kid1", item.forName)
        assertEquals(listOf("Kid2"), item.mentionNames)
        assertFalse(item.canRemove)
    }

    @Test
    fun `touching one's own reaction again takes it back`() {
        assertNull(nextReaction(Reaction.Wow, Reaction.Wow))
        assertEquals(Reaction.Clap, nextReaction(Reaction.Wow, Reaction.Clap))
        assertEquals(Reaction.Clap, nextReaction(null, Reaction.Clap))
    }

    @Test
    fun `a text and at least one photo, three at most`() {
        val form = GoodNewsForm(forMemberId = "adult1")
        assertEquals(setOf(GoodNewsFormError.TextMissing, GoodNewsFormError.NoPhoto), form.validate(isNew = true))
        assertEquals(setOf(GoodNewsFormError.TextMissing), form.validate(isNew = false))
        assertEquals(setOf(GoodNewsFormError.TextTooLong), form.copy(text = "a".repeat(501), photos = listOf("u1")).validate(true))
        assertEquals(listOf("u1", "u2", "u3"), form.withPhotos(listOf("u1", "u2")).withPhotos(listOf("u2", "u3", "u4")).photos)
    }
}
