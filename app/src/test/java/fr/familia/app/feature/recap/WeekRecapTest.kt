package fr.familia.app.feature.recap

import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Reaction
import fr.familia.app.core.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WeekRecapTest {
    private val now = Instant.parse("2026-11-22T17:00:00Z")
    private val today = LocalDate.of(2026, 11, 22)

    private fun news(
        id: String,
        photos: Int,
        daysAgo: Long,
        reactions: Int = 0,
        hiddenBy: List<String> = emptyList(),
        author: String = "camille",
        at: Instant = now.minusSeconds(daysAgo * 24 * 3600),
    ) = GoodNews(
        id = id,
        text = "",
        photoIds = (1..photos).map { "p$it" },
        authorId = author,
        authorName = author,
        forMemberId = author,
        householdId = "h1",
        reactions = (1..reactions).associate { "m$it" to Reaction.Heart },
        hiddenByIds = hiddenBy,
        createdAt = at,
    )

    @Test
    fun `one photo per day of the week, never chosen by reactions`() {
        val list = listOf(
            news("a", photos = 3, daysAgo = 1),
            news("b", photos = 1, daysAgo = 2, reactions = 9, author = "ines"),
            news("b2", photos = 1, daysAgo = 2, author = "noe", at = now.minusSeconds(2 * 24 * 3600 + 60)),
            news("old", photos = 2, daysAgo = 8, reactions = 9),
            news("text", photos = 0, daysAgo = 1, reactions = 9),
        )
        val photos = WeekRecap.photos(list, "lou", now)
        // Day 2: Inès and Noé both posted once, the earliest post wins, whatever its hearts.
        assertEquals(listOf("a", "b2"), photos.map { it.newsId })
        assertEquals("goodNews/camille/a/p1-thumb.jpg", photos[0].thumbnailPath)
    }

    @Test
    fun `the authors vary from one day to the next`() {
        val list = listOf(
            news("d1cam", photos = 1, daysAgo = 1),
            news("d2cam", photos = 1, daysAgo = 2, at = now.minusSeconds(2 * 24 * 3600 + 60)),
            news("d2ines", photos = 1, daysAgo = 2, author = "ines"),
            news("d3cam", photos = 1, daysAgo = 3),
            news("d4cam", photos = 1, daysAgo = 4),
            news("d5noe", photos = 1, daysAgo = 5, author = "noe"),
        )
        // Camille on day 1, Inès on day 2 though Camille posted earlier, then Noé before a second Camille.
        assertEquals(listOf("d1cam", "d2ines", "d5noe"), WeekRecap.photos(list, "lou", now).map { it.newsId })
    }

    @Test
    fun `the day is counted in Paris, not in UTC`() {
        // 23 h 30 and 0 h 30 in Paris on a winter night: two days, though the same UTC day.
        val list = listOf(
            news("late", photos = 1, daysAgo = 0, at = Instant.parse("2026-11-20T22:30:00Z")),
            news("early", photos = 1, daysAgo = 0, at = Instant.parse("2026-11-20T23:30:00Z"), author = "ines"),
        )
        assertEquals(listOf("early", "late"), WeekRecap.photos(list, "lou", now).map { it.newsId })
    }

    @Test
    fun `a good news one hid stays out of the week`() {
        val list = listOf(news("a", photos = 1, daysAgo = 1, hiddenBy = listOf("ines")), news("b", photos = 1, daysAgo = 1))
        assertEquals(listOf("b"), WeekRecap.photos(list, "ines", now).map { it.newsId })
    }

    @Test
    fun `the birthdays of the next 14 days only`() {
        fun member(id: String, birth: LocalDate) = Member(id = id, firstName = id, email = null, birthDate = birth, householdId = "h1", profile = MemberProfile.Adult, isAdmin = false, parentIds = emptyList(), uid = null)
        val members = listOf(member("soon", LocalDate.of(1980, 12, 6)), member("later", LocalDate.of(1980, 12, 7)), member("today", LocalDate.of(1990, 11, 22)))
        assertEquals(listOf("today", "soon"), WeekRecap.birthdays(members, today).map { it.member.id })
    }

    @Test
    fun `the trips under way, not those to come or over`() {
        fun trip(id: String, start: LocalDate, end: LocalDate) = Trip(id, id, start, end, emptyList(), listOf("ines"), "camille", "Camille")
        val trips = listOf(
            trip("now", today.minusDays(2), today.plusDays(3)),
            trip("ends", today.minusDays(5), today),
            trip("next", today.plusDays(1), today.plusDays(4)),
            trip("over", today.minusDays(9), today.minusDays(1)),
        )
        assertEquals(listOf("ends", "now"), WeekRecap.tripsUnderWay(trips, today).map { it.id })
    }
}
