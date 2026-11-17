package fr.familia.app.feature.yearinreview

import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.HelpKind
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.HelpStatus
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Reaction
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.WeeklyAnswer
import fr.familia.app.core.model.WeeklyQuestion
import fr.familia.app.feature.events.TestFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class YearInReviewTest {
    private val today = LocalDate.of(2026, 12, 27)
    private val now = Instant.parse("2026-12-27T10:00:00Z")
    private val me = TestFamily.adult
    private fun at(date: String) = Instant.parse("${date}T12:00:00Z")
    private val zoneYear = { i: Instant -> i.atZone(ZoneOffset.UTC).year }

    private fun news(id: String, date: String, likes: Int = 0, photos: List<String> = listOf("p1"), hiddenBy: List<String> = emptyList()) =
        GoodNews(
            id = id, text = "Nouvelle $id", photoIds = photos, authorId = "adult2", authorName = "Inès",
            forMemberId = "adult2", householdId = "h2",
            reactions = (1..likes).associate { "m$it" to Reaction.Heart },
            hiddenByIds = hiddenBy, createdAt = at(date),
        )

    private fun event(id: String, date: String) =
        Event(id, "Repas $id", LocalDate.parse(date), null, null, null, EventTarget.ForFamily, "adult1", "Camille")

    private fun help(id: String, date: String, status: HelpStatus) = HelpRequest(
        id, HelpKind.entries.first(), "Garde $id", LocalDate.parse(date), null, null, null, null,
        "adult1", "Camille", "adult1", listOf("adult2"), status = status,
    )

    private fun trip(id: String, start: String, end: String) =
        Trip(id, "Voyage $id", LocalDate.parse(start), LocalDate.parse(end), listOf("adult1"), emptyList(), "adult1", "Camille")

    private fun build(
        news: List<GoodNews> = emptyList(),
        events: List<Event> = emptyList(),
        helps: List<HelpRequest> = emptyList(),
        trips: List<Trip> = emptyList(),
        members: List<Member> = TestFamily.family.members,
        questions: List<WeeklyQuestion> = emptyList(),
        answers: Map<String, List<WeeklyAnswer>> = emptyMap(),
    ) = buildYearInReview(
        2026, me, today, now, news, events, helps, trips, TestFamily.family.copy(members = members),
        questions, answers, zoneYear,
    )

    @Test
    fun `the story of a year shows from 26 December, and the card until 10 January`() {
        assertNull(reviewYear(LocalDate.of(2026, 12, 25)))
        assertEquals(2026, reviewYear(LocalDate.of(2026, 12, 26)))
        assertEquals(2026, reviewYear(LocalDate.of(2027, 6, 1)))
        assertEquals(2027, reviewYear(LocalDate.of(2027, 12, 31)))
        assertFalse(reviewCardShows(LocalDate.of(2026, 12, 25)))
        assertTrue(reviewCardShows(LocalDate.of(2026, 12, 26)))
        assertTrue(reviewCardShows(LocalDate.of(2027, 1, 10)))
        assertFalse(reviewCardShows(LocalDate.of(2027, 1, 11)))
    }

    @Test
    fun `the admin principal tries this year with a simulated date`() {
        val november = LocalDate.of(2026, 11, 17)
        assertEquals(2026, reviewYear(november, simulate = true))
        assertNull(reviewYear(november, simulate = false))
    }

    @Test
    fun `only what happened during the year and by today counts`() {
        val review = build(
            news = listOf(news("a", "2026-03-01"), news("b", "2025-12-30"), news("c", "2026-12-28")),
            events = listOf(event("e1", "2026-05-01"), event("e2", "2026-12-31"), event("e3", "2025-06-01")),
            helps = listOf(help("h1", "2026-04-01", HelpStatus.Taken), help("h2", "2026-04-02", HelpStatus.Open), help("h3", "2026-04-03", HelpStatus.Cancelled)),
        )
        assertEquals(1, review.goodNewsCount)
        assertEquals(1, review.eventCount)
        assertEquals(1, review.helpCount)
    }

    @Test
    fun `the photos are the most liked, never those I hid, at most four`() {
        val review = build(
            news = listOf(
                news("a", "2026-01-01", likes = 1), news("b", "2026-02-01", likes = 5), news("c", "2026-03-01", likes = 3),
                news("d", "2026-04-01", likes = 9, hiddenBy = listOf(me.id)), news("e", "2026-05-01", likes = 2),
                news("f", "2026-06-01", likes = 8, photos = emptyList()), news("g", "2026-07-01"),
            ),
        )
        assertEquals(listOf("b", "c", "e", "a"), review.photos.map { it.newsId })
        assertEquals("goodNews/adult2/b/p1.jpg", review.photos.first().path)
    }

    @Test
    fun `the trip of the year is the longest one already over`() {
        val review = build(
            trips = listOf(trip("t1", "2026-04-01", "2026-04-03"), trip("t2", "2026-07-01", "2026-07-15"), trip("t3", "2026-12-26", "2027-01-05")),
        )
        assertEquals("t2", review.trip?.id)
    }

    @Test
    fun `the newcomers are the members born this year`() {
        val baby = Member("bb", "Lou", null, LocalDate.of(2026, 2, 1), "h1", MemberProfile.ChildWithoutApp, false, listOf("adult1"), null)
        val review = build(members = TestFamily.family.members + baby)
        assertEquals(listOf("bb"), review.newcomers.map { it.id })
    }

    @Test
    fun `the question of the year is the most answered, with a few adult answers and no skipped one`() {
        fun q(id: String, date: String, count: Int) = WeeklyQuestion(id, "Question $id", null, at(date), at(date).plusSeconds(172800), count)
        fun a(id: String, text: String?, skipped: Boolean = false) = WeeklyAnswer(id, id, text, skipped, at("2026-06-03"))
        val review = build(
            questions = listOf(q("q1", "2026-03-01", 2), q("q2", "2026-06-01", 5), q("q3", "2026-12-26", 9)),
            answers = mapOf("q2" to listOf(a("admin1", "La mer"), a("adult1", null, skipped = true), a("kid1", "Le gâteau"), a("adult2", "Le jardin"), a("adult1", "Les jeux"))),
        )
        assertEquals("q2", review.question?.id)
        assertEquals(listOf("La mer", "Le jardin", "Les jeux"), review.answers.map { it.text })
    }

    @Test
    fun `an empty year keeps only the first and the last page`() {
        val pages = reviewPages(build(members = emptyList()))
        assertEquals(listOf(ReviewPage.Intro(2026), ReviewPage.Thanks), pages)
    }

    @Test
    fun `a full year tells everything in order`() {
        val baby = Member("bb", "Lou", null, LocalDate.of(2026, 2, 1), "h1", MemberProfile.ChildWithoutApp, false, listOf("adult1"), null)
        val review = build(
            news = listOf(news("a", "2026-01-01", likes = 1)),
            trips = listOf(trip("t2", "2026-07-01", "2026-07-15")),
            members = TestFamily.family.members + baby,
            questions = listOf(WeeklyQuestion("q", "Question", null, at("2026-06-01"), at("2026-06-03"), 1)),
            answers = mapOf("q" to listOf(WeeklyAnswer("adult2", "Inès", "La mer", false, at("2026-06-02")))),
        )
        val kinds = reviewPages(review).map { it::class.simpleName }
        assertEquals(listOf("Intro", "Numbers", "Photos", "TripOfTheYear", "Newcomers", "Question", "Thanks"), kinds)
    }
}
