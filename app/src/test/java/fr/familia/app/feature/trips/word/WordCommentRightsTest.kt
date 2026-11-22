package fr.familia.app.feature.trips.word

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class WordCommentRightsTest {
    @Test
    fun `the author of the comment takes it off`() = assertTrue(canRemoveWordComment("a", "w", "a", false))

    @Test
    fun `the author of the word takes any comment off`() = assertTrue(canRemoveWordComment("a", "w", "w", false))

    @Test
    fun `the admin principal takes any comment off`() = assertTrue(canRemoveWordComment("a", "w", "k", true))

    @Test
    fun `another follower cannot`() = assertFalse(canRemoveWordComment("a", "w", "z", false))

    @Test
    fun `with no word known nobody else can`() = assertFalse(canRemoveWordComment("a", null, "z", false))
}
