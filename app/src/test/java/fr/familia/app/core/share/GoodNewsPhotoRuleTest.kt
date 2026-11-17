package fr.familia.app.core.share

import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.goodnews.goodNews
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Carte EXPERT-2 on a good news: its photo leaves Familia only with a responsible adult of every child it shows. */
class GoodNewsPhotoRuleTest {
    private val family = TestFamily.family

    @Test
    fun `an adult's own news goes with its photo`() {
        assertTrue(goodNewsPhotoAllowed(goodNews("n1", TestFamily.adult), "adult2", family))
    }

    @Test
    fun `a child's news goes with its photo only from their parent`() {
        val news = goodNews("n1", TestFamily.adult, forMemberId = "kid1")
        assertTrue(goodNewsPhotoAllowed(news, "adult1", family))
        assertFalse(goodNewsPhotoAllowed(news, "adult2", family))
        assertFalse(goodNewsPhotoAllowed(news, "kid1", family))
    }

    @Test
    fun `a child named from another household keeps the photo in Familia`() {
        val news = goodNews("n1", TestFamily.adult, forMemberId = "kid1").copy(mentionIds = listOf("kid2"))
        assertFalse(goodNewsPhotoAllowed(news, "adult1", family))
        assertFalse(goodNewsPhotoAllowed(news, "adult2", family))
    }

    @Test
    fun `a member who left the family can no longer be checked`() {
        val news = goodNews("n1", TestFamily.adult).copy(mentionIds = listOf("gone"))
        assertFalse(goodNewsPhotoAllowed(news, "adult1", family))
    }

    @Test
    fun `no photo, or the good news of Familia, has nothing to send`() {
        assertFalse(goodNewsPhotoAllowed(goodNews("n1", TestFamily.adult, photos = 0), "adult1", family))
        assertFalse(goodNewsPhotoAllowed(goodNews("n2", TestFamily.adult).copy(birthdayOf = "adult1"), "adult1", family))
        assertFalse(goodNewsPhotoAllowed(goodNews("n3", TestFamily.adult).copy(challengeMonth = "2026-11"), "adult1", family))
    }
}
