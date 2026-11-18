package fr.familia.app.feature.feed

import fr.familia.app.core.model.HelpStatus
import fr.familia.app.core.model.Reaction
import fr.familia.app.core.model.WeeklyQuestion
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.goodnews.goodNews
import fr.familia.app.feature.helprequests.helpRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** The cards of the fil coups de main (récit 67): bonnes nouvelles, coups de main, question. */
class FeedCardsTest {
    private val since: Instant = Instant.parse("2026-11-07T16:00:00Z")

    @Test
    fun `the good news of the window, without those I hid nor the birthdays of Familia`() {
        val recent = goodNews("n1", TestFamily.adult, daysAgo = 1)
        val old = goodNews("n2", TestFamily.adult, daysAgo = 20)
        val hidden = goodNews("n3", TestFamily.otherAdult, forMemberId = "kid1", daysAgo = 2).copy(hiddenByIds = listOf("kid1"))
        val birthday = goodNews("n4", TestFamily.admin, daysAgo = 0).copy(birthdayOf = "kid2")
        val shown = goodNewsForFeed(listOf(recent, old, hidden, birthday), "kid1", since)
        assertEquals(listOf("n1"), shown.map { it.id })
        assertEquals(listOf("n1", "n3"), goodNewsForFeed(listOf(recent, hidden), "adult1", since).map { it.id })
    }

    @Test
    fun `a compact card shows the reactions given, a heart when there is none`() {
        val news = goodNews("n1", TestFamily.adult).copy(
            reactions = mapOf("a" to Reaction.Clap, "b" to Reaction.Heart, "c" to Reaction.Clap),
        )
        assertEquals(listOf(Reaction.Heart to 1, Reaction.Clap to 2), feedReactions(news))
        assertEquals(listOf(Reaction.Heart to 0), feedReactions(goodNews("n2", TestFamily.adult)))
    }

    @Test
    fun `the coups de main of the window, newest first, never a cancelled one`() {
        val open = helpRequest("h1", TestFamily.adult, listOf("adult2")).copy(createdAt = Instant.parse("2026-11-18T08:00:00Z"))
        val taken = helpRequest("h2", TestFamily.adult, listOf("adult2"), status = HelpStatus.Taken, volunteer = TestFamily.otherAdult)
            .copy(createdAt = Instant.parse("2026-11-19T08:00:00Z"))
        val cancelled = open.copy(id = "h3", status = HelpStatus.Cancelled)
        val old = open.copy(id = "h4", createdAt = Instant.parse("2026-11-01T08:00:00Z"))
        val unsent = open.copy(id = "h5", createdAt = null)
        assertEquals(listOf("h2", "h1"), helpForFeed(listOf(open, taken, cancelled, old, unsent), since, 10).map { it.id })
        assertEquals(listOf("h2"), helpForFeed(listOf(open, taken), since, 1).map { it.id })
    }

    @Test
    fun `the questions published in the window`() {
        fun question(id: String, at: String) = WeeklyQuestion(id, "Ton plus beau souvenir ?", null, Instant.parse(at), Instant.parse(at).plusSeconds(172_800), 3)
        val shown = weeklyForFeed(listOf(question("2026-11-16", "2026-11-16T08:00:00Z"), question("2026-11-02", "2026-11-02T08:00:00Z")), since)
        assertEquals(listOf("2026-11-16"), shown.map { it.id })
    }
}
