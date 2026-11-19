package fr.familia.app.feature.goodnews

import fr.familia.app.core.model.Reaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** One heart (PRODUIT-139): a tap puts a heart or takes back the reaction given. */
class HeartReactionTest {
    @Test
    fun `a tap with no reaction puts a heart`() {
        assertEquals(Reaction.Heart, nextReaction(null, tappedReaction(null)))
    }

    @Test
    fun `a tap on my heart takes it back`() {
        assertNull(nextReaction(Reaction.Heart, tappedReaction(Reaction.Heart)))
    }

    @Test
    fun `a tap on another reaction of mine takes it back too`() {
        assertNull(nextReaction(Reaction.Clap, tappedReaction(Reaction.Clap)))
    }

    @Test
    fun `choosing in the bar changes my reaction`() {
        assertEquals(Reaction.Party, nextReaction(Reaction.Heart, Reaction.Party))
    }

    @Test
    fun `the summary shows the most given first, three at most`() {
        val reactions = mapOf(
            "a" to Reaction.Clap, "b" to Reaction.Clap, "c" to Reaction.Heart,
            "d" to Reaction.Wow, "e" to Reaction.Wow, "f" to Reaction.Wow, "g" to Reaction.Laugh,
        )
        assertEquals(listOf(Reaction.Wow, Reaction.Clap, Reaction.Heart), topReactions(reactions))
    }

    @Test
    fun `no reaction, no summary`() {
        assertEquals(emptyList<Reaction>(), topReactions(emptyMap()))
    }
}
