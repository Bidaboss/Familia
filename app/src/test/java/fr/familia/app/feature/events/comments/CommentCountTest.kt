package fr.familia.app.feature.events.comments

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The counter under a comment field only shows near the limit (l'admin, 19/11). */
class CommentCountTest {
    @Test
    fun `no counter for an empty or short comment`() {
        assertFalse(showsCommentCount(0))
        assertFalse(showsCommentCount(120))
        assertFalse(showsCommentCount(449))
    }

    @Test
    fun `the counter shows from 450 characters`() {
        assertTrue(showsCommentCount(450))
        assertTrue(showsCommentCount(500))
    }
}
