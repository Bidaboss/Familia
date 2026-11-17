package fr.familia.app.feature.yearinreview

import fr.familia.app.core.model.Event
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.HelpStatus
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.WeeklyAnswer
import fr.familia.app.core.model.WeeklyQuestion
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

/*
 * « Notre année en famille » (PRD, récit 55): from 26/12 to 10/01, a card on the Accueil opens a
 * full screen story of the family's year. Everything is counted for the family, never for one person:
 * no ranking, no « le plus actif ». Nothing about gifts, bookings or capsules. It is computed on the
 * phone from what this member can already read, with no new scheduled task.
 */

/** The first day the story of a year shows, and the last day of the card on the Accueil. */
val YEAR_IN_REVIEW_START: MonthDay = MonthDay.of(12, 26)
val YEAR_IN_REVIEW_CARD_END: MonthDay = MonthDay.of(1, 10)

/** The first year told: Familia started in November 2026. */
const val FIRST_REVIEW_YEAR = 2026

/** At most this many photos, the most liked first. */
const val REVIEW_MAX_PHOTOS = 4

/** At most this many answers to the question of the year. */
const val REVIEW_MAX_ANSWERS = 3

/** The year whose story can be seen on [today]: this year from 26/12, else the year before. Null before the first one. */
fun reviewYear(today: LocalDate): Int? {
    val year = if (MonthDay.from(today) >= YEAR_IN_REVIEW_START) today.year else today.year - 1
    return year.takeIf { it >= FIRST_REVIEW_YEAR }
}

/** The card shows on the Accueil from 26/12 to 10/01 included. */
fun reviewCardShows(today: LocalDate): Boolean {
    val day = MonthDay.from(today)
    return reviewYear(today) != null && (day >= YEAR_IN_REVIEW_START || day <= YEAR_IN_REVIEW_CARD_END)
}

/**
 * The year told, with a simulated date for the admin principal (PRODUIT, 17/11): before 26/12 they
 * see the story of this year as if it were 26/12, with what happened so far, to try it.
 */
fun reviewYear(today: LocalDate, simulate: Boolean): Int? = if (simulate) today.year else reviewYear(today)

/** One photo of a good news: the path of its first photo in Storage. */
data class ReviewPhoto(val newsId: String, val path: String, val authorName: String)

data class YearInReview(
    val year: Int,
    val goodNewsCount: Int,
    val eventCount: Int,
    val helpCount: Int,
    val photos: List<ReviewPhoto>,
    val trip: Trip?,
    val newcomers: List<Member>,
    val question: WeeklyQuestion?,
    val answers: List<WeeklyAnswer>,
)

/**
 * The story of [year] as seen by [me] on [today], from what they can read: the good news (those they
 * hid excluded), the events of the planning, the help requests they see, the trips they follow, the
 * family, and the questions of the week with their answers. Only what happened by [today] counts.
 */
fun buildYearInReview(
    year: Int,
    me: Member,
    today: LocalDate,
    now: Instant,
    news: List<GoodNews>,
    events: List<Event>,
    helps: List<HelpRequest>,
    trips: List<Trip>,
    family: Family,
    questions: List<WeeklyQuestion>,
    answers: Map<String, List<WeeklyAnswer>>,
    zoneYear: (Instant) -> Int,
): YearInReview {
    val inYear = { date: LocalDate -> date.year == year && date <= today }
    val myNews = news.filter { n -> n.createdAt?.let { zoneYear(it) == year && it <= now } == true && me.id !in n.hiddenByIds }
    val photos = myNews
        .filter { it.photoIds.isNotEmpty() }
        .sortedWith(compareByDescending<GoodNews> { it.reactions.size }.thenBy { it.createdAt })
        .take(REVIEW_MAX_PHOTOS)
        .map { ReviewPhoto(it.id, it.photoPath(it.photoIds.first()), it.authorName) }
    val trip = trips
        .filter { it.startDate.year == year && it.endDate <= today }
        .sortedWith(compareByDescending<Trip> { ChronoUnit.DAYS.between(it.startDate, it.endDate) }.thenBy { it.startDate })
        .firstOrNull()
    val adultIds = family.members.filter { it.profile == MemberProfile.Adult }.map { it.id }.toSet()
    val question = questions
        .filter { zoneYear(it.publishedAt) == year && it.revealAt <= now && it.answerCount > 0 }
        .sortedWith(compareByDescending<WeeklyQuestion> { it.answerCount }.thenBy { it.publishedAt })
        .firstOrNull()
    val shown = question?.let { q ->
        // Only answers everyone already reads (after the reveal), and never a child's (PRODUIT, 17/11).
        answers[q.id].orEmpty().filter { !it.skipped && !it.text.isNullOrBlank() && it.memberId in adultIds }
            .sortedBy { it.updatedAt }.take(REVIEW_MAX_ANSWERS)
    }.orEmpty()
    return YearInReview(
        year = year,
        goodNewsCount = myNews.size,
        eventCount = events.count { inYear(it.date) },
        helpCount = helps.count { it.status == HelpStatus.Taken && inYear(it.date) },
        photos = photos,
        trip = trip,
        newcomers = family.members.filter { it.birthDate?.let(inYear) == true }.sortedBy { it.birthDate },
        question = question.takeIf { shown.isNotEmpty() },
        answers = shown,
    )
}

/** The pages of the story, in order. A page with nothing to show is left out. */
sealed interface ReviewPage {
    data class Intro(val year: Int) : ReviewPage
    data class Numbers(val goodNews: Int, val events: Int, val helps: Int) : ReviewPage
    data class Photos(val photos: List<ReviewPhoto>) : ReviewPage
    data class TripOfTheYear(val trip: Trip) : ReviewPage
    data class Newcomers(val members: List<Member>) : ReviewPage
    data class Question(val question: WeeklyQuestion, val answers: List<WeeklyAnswer>) : ReviewPage
    data object Thanks : ReviewPage
}

fun reviewPages(review: YearInReview): List<ReviewPage> = buildList {
    add(ReviewPage.Intro(review.year))
    if (review.goodNewsCount + review.eventCount + review.helpCount > 0) {
        add(ReviewPage.Numbers(review.goodNewsCount, review.eventCount, review.helpCount))
    }
    if (review.photos.isNotEmpty()) add(ReviewPage.Photos(review.photos))
    review.trip?.let { add(ReviewPage.TripOfTheYear(it)) }
    if (review.newcomers.isNotEmpty()) add(ReviewPage.Newcomers(review.newcomers))
    review.question?.let { add(ReviewPage.Question(it, review.answers)) }
    add(ReviewPage.Thanks)
}
