package fr.familia.app.feature.feed

import androidx.compose.runtime.Composable
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Le fil d'actualité (récit 67). Fictional first names only. */
class FeedTest {
    private class Card(
        override val key: String,
        at: String,
        override val authorId: String? = "ines",
        override val pinRank: Int? = null,
    ) : FeedCard {
        override val at: Instant = Instant.parse(at)

        @Composable
        override fun Content(context: FeedContext) = Unit
    }

    @Test
    fun `newest first, a card given twice kept once`() {
        val merged = mergeFeed(
            listOf(
                listOf(Card("event:e1", "2026-11-18T08:00:00Z"), Card("event:e2", "2026-11-16T08:00:00Z")),
                listOf(Card("birthday:m1", "2026-11-17T23:00:00Z"), Card("event:e1", "2026-11-18T08:00:00Z")),
            ),
        )
        assertEquals(listOf("event:e1", "birthday:m1", "event:e2"), merged.map { it.key })
    }

    @Test
    fun `the birthdays of the day on top of it, the youngest first, then the day goes on`() {
        // Midnight in Paris for the birthdays, the news of the morning, and the day before.
        val merged = mergeFeed(
            listOf(
                listOf(Card("news:n1", "2026-11-18T08:00:00Z"), Card("news:n0", "2026-11-17T20:00:00Z")),
                listOf(
                    Card("birthday:lou", "2026-11-17T23:00:00Z", authorId = null, pinRank = 40),
                    Card("birthday:noe", "2026-11-17T23:00:00Z", authorId = null, pinRank = 8),
                ),
            ),
        )
        assertEquals(listOf("birthday:noe", "birthday:lou", "news:n1", "news:n0"), merged.map { it.key })
    }

    @Test
    fun `a birthday card is pinned by its age`() {
        val sam = Member("sam", "Sam", null, LocalDate.of(2016, 11, 18), "h1", MemberProfile.ChildWithoutApp, false, emptyList(), null)
        assertEquals(10, BirthdayFeedCard(birthdaysOn(listOf(sam), LocalDate.of(2026, 11, 18)).single(), wishes = true).pinRank)
    }

    @Test
    fun `cut by the day of Paris`() {
        // 23 h 30 UTC is already the next day in Paris.
        val days = feedDays(listOf(Card("a", "2026-11-17T23:30:00Z"), Card("b", "2026-11-17T22:30:00Z"), Card("c", "2026-11-16T10:00:00Z")))
        assertEquals(listOf(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 16)), days.map { it.first })
    }

    @Test
    fun `the day says today and yesterday with the date, then the weekday, and the year only when it is not this one`() {
        val today = LocalDate.of(2026, 11, 18)
        assertEquals("Aujourd'hui · mercredi 18 novembre", feedDayLabel(today, today, "Aujourd'hui", "Hier"))
        assertEquals("Hier · mardi 17 novembre", feedDayLabel(today.minusDays(1), today, "Aujourd'hui", "Hier"))
        assertEquals("Lundi 16 novembre", feedDayLabel(today.minusDays(2), today, "Aujourd'hui", "Hier"))
        assertEquals("Lundi 1er décembre 2025", feedDayLabel(LocalDate.of(2025, 12, 1), today, "Aujourd'hui", "Hier"))
        assertEquals(listOf("☀️", "🌙", "🗓️"), (0L..2L).map { feedDayEmoji(today.minusDays(it), today) })
    }

    @Test
    fun `the badge counts the cards of the others since I looked`() {
        val cards = listOf(
            Card("a", "2026-11-18T10:00:00Z"),
            Card("b", "2026-11-18T09:00:00Z", authorId = "camille"),
            Card("c", "2026-11-18T07:00:00Z"),
            Card("d", "2026-11-18T06:00:00Z", authorId = null),
        )
        assertEquals(1, unseenCount(cards, Instant.parse("2026-11-18T08:00:00Z"), "camille"))
        assertEquals(3, unseenCount(cards, null, "camille"))
    }

    @Test
    fun `each slice goes back 14 more days and takes 10 more cards`() {
        val now = Instant.parse("2026-11-18T12:00:00Z")
        assertEquals(Instant.parse("2026-11-04T12:00:00Z"), FeedWindow(1, now).since)
        assertEquals(10, FeedWindow(1, now).limit)
        assertEquals(Instant.parse("2026-10-21T12:00:00Z"), FeedWindow(2, now).since)
        assertEquals(20, FeedWindow(2, now).limit)
    }

    @Test
    fun `an event of a series, or not yet dated by the server, stays out of the fil`() {
        fun e(id: String, created: String?, series: String? = null) = Event(
            id, "Goûter", LocalDate.of(2026, 11, 21), null, null, null, EventTarget.ForFamily, "ines", "Inès",
            seriesId = series, createdAt = created?.let(Instant::parse),
        )
        val kept = newEvents(listOf(e("a", "2026-11-18T08:00:00Z"), e("b", null), e("c", "2026-11-18T08:00:00Z", series = "s1")))
        assertEquals(listOf("a"), kept.map { it.id })
    }

    @Test
    fun `only the birthdays of the day, with their age`() {
        fun m(id: String, born: LocalDate?) = Member(id, id, null, born, "h1", MemberProfile.Adult, false, emptyList(), null)
        val today = LocalDate.of(2026, 11, 18)
        val list = birthdaysOn(listOf(m("noe", LocalDate.of(2017, 11, 18)), m("lou", LocalDate.of(1990, 11, 19)), m("sam", null)), today)
        assertEquals(listOf("noe" to 9), list.map { it.member.id to it.age })
    }

    @Test
    fun `an event typed on a birth date shows its next return, a recent one keeps its own day`() {
        fun e(date: LocalDate) = Event("a", "Anniversaire de Nina", date, null, null, null, EventTarget.ForFamily, "ines", "Inès")
        val today = LocalDate.of(2026, 11, 20)
        assertEquals(LocalDate.of(2026, 12, 7), feedEventDay(e(LocalDate.of(1987, 12, 7)), today))
        assertEquals(LocalDate.of(2027, 3, 2), feedEventDay(e(LocalDate.of(1990, 3, 2)), today))
        // Last week, or in December of last year seen in January: its own day.
        assertEquals(LocalDate.of(2026, 11, 13), feedEventDay(e(LocalDate.of(2026, 11, 13)), today))
        assertEquals(LocalDate.of(2026, 12, 31), feedEventDay(e(LocalDate.of(2026, 12, 31)), LocalDate.of(2027, 1, 2)))
        assertEquals(LocalDate.of(2026, 12, 24), feedEventDay(e(LocalDate.of(2026, 12, 24)), today))
    }

    @Test
    fun `an old event typed on 29 February comes back on 28 February, then on 29 in a leap year`() {
        val old = Event("b", "Fête du 29", LocalDate.of(2024, 2, 29), null, null, null, EventTarget.ForFamily, "ines", "Inès")
        assertEquals(LocalDate.of(2027, 2, 28), feedEventDay(old, LocalDate.of(2026, 11, 20)))
        assertEquals(LocalDate.of(2028, 2, 29), feedEventDay(old, LocalDate.of(2027, 3, 1)))
    }
}
