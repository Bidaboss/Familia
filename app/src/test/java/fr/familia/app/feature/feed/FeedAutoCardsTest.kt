package fr.familia.app.feature.feed

import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GoodDeal
import fr.familia.app.core.model.GoodDealKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The automatic cards of la gazette (récit 116): envies, bons plans, events 3 days before. Fictional data only. */
class FeedAutoCardsTest {

    private val since = Instant.parse("2026-11-11T00:00:00Z")

    private fun list(id: String, who: String, kind: GiftListKind = GiftListKind.Wishes) =
        GiftList(id, kind, "Envies", null, null, who, emptyList(), who, who)

    private fun item(id: String, at: String?, shown: Boolean = true) =
        GiftItem(id, "Envie $id", null, null, null, "noe", "Noé", shown, addedAt = at?.let(Instant::parse))

    @Test
    fun `wishes are grouped by person and by day, without the ideas, the old ones nor my own`() {
        val days = wishesForFeed(
            listOf(
                list("w1", "noe") to listOf(
                    item("a", "2026-11-20T09:00:00Z"),
                    item("b", "2026-11-20T18:00:00Z"),
                    item("c", "2026-11-21T09:00:00Z"),
                    item("idea", "2026-11-21T10:00:00Z", shown = false),
                    item("old", "2026-11-01T10:00:00Z"),
                    item("unsaved", null),
                ),
                list("w2", "me") to listOf(item("mine", "2026-11-21T09:00:00Z")),
                list("i1", "lou", GiftListKind.Ideas) to listOf(item("x", "2026-11-21T09:00:00Z")),
            ),
            meId = "me",
            since = since,
            limit = 10,
        )
        assertEquals(listOf(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 20)), days.map { it.day })
        assertEquals(listOf("Envie c"), days[0].titles)
        assertEquals(listOf("Envie a", "Envie b"), days[1].titles)
        assertEquals("wishes:noe:2026-11-20", WishesFeedCard(days[1]).key)
    }

    @Test
    fun `the bons plans of the window, newest first`() {
        fun deal(id: String, at: String?) = GoodDeal(id, GoodDealKind.Deal, "Plan $id", authorId = "noe", authorName = "Noé", createdAt = at?.let(Instant::parse))
        val kept = dealsForFeed(listOf(deal("a", "2026-11-12T08:00:00Z"), deal("b", "2026-11-20T08:00:00Z"), deal("old", "2026-11-01T08:00:00Z"), deal("c", null)), since, 10)
        assertEquals(listOf("b", "a"), kept.map { it.id })
        assertEquals("deal:b", DealFeedCard(kept[0]).key)
    }

    @Test
    fun `an event comes back up at 8 h three days before, the same card`() {
        val planned = Instant.parse("2026-10-01T10:00:00Z")
        val event = Event("e1", "Repas", LocalDate.of(2026, 11, 28), null, null, null, EventTarget.ForFamily, "noe", "Noé", createdAt = planned)
        // 25/11 at 8 h in Paris (UTC+1).
        val back = Instant.parse("2026-11-25T07:00:00Z")
        assertEquals(planned, feedEventAt(event, Instant.parse("2026-11-25T06:59:00Z")))
        assertEquals(back, feedEventAt(event, Instant.parse("2026-11-25T07:00:00Z")))
        assertEquals(back, feedEventAt(event, Instant.parse("2026-11-27T12:00:00Z")))
        // Planned within the 3 days: it stays where it was planned.
        val late = event.copy(createdAt = Instant.parse("2026-11-26T09:00:00Z"))
        assertEquals(late.createdAt, feedEventAt(late, Instant.parse("2026-11-27T12:00:00Z")))
    }
}
