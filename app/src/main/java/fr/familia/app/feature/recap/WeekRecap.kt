package fr.familia.app.feature.recap

import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MonthChallenge
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.upcomingBirthdays
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A photo of the week: the thumbnail of a good news, which opens it. */
data class WeekPhoto(val newsId: String, val thumbnailPath: String)

/**
 * « La semaine de la famille » (récit 36): up to three photos of the week (one per day, authors varied), the birthdays of the next
 * 14 days, the challenge of the month and the trips under way that the member follows.
 */
data class WeekRecap(
    val photos: List<WeekPhoto>,
    val birthdays: List<Birthday>,
    val challenge: MonthChallenge?,
    val trips: List<Trip>,
) {
    val isEmpty: Boolean get() = photos.isEmpty() && birthdays.isEmpty() && challenge == null && trips.isEmpty()

    companion object {
        const val PHOTOS = 3
        val PARIS: ZoneId = ZoneId.of("Europe/Paris")
        const val BIRTHDAY_DAYS = 14L
        val WEEK: Duration = Duration.ofDays(7)

        /**
         * The photos of the good news of the last 7 days that [memberId] did not hide, never chosen
         * by reactions (EXPERT-11, the same spirit as Notre année): one photo per day that has some,
         * the day counted in [zone]. Each day takes the photo of the author picked the least so far
         * (then the earliest post), newest day first. When more days than [PHOTOS] have photos,
         * the days of authors not seen yet go first, so three different people show when they can.
         * Shown newest first. No count, no name: the screen only shows them.
         */
        fun photos(news: List<GoodNews>, memberId: String, now: Instant, zone: ZoneId = PARIS): List<WeekPhoto> {
            val since = now.minus(WEEK)
            val week = news
                .filter { it.createdAt != null && !it.createdAt.isBefore(since) && !it.createdAt.isAfter(now) && it.photoIds.isNotEmpty() }
                .filterNot { it.isHiddenFor(memberId) }
            val days = week.groupBy { it.createdAt!!.atZone(zone).toLocalDate() }.toSortedMap(compareByDescending { it })
            val picked = mutableMapOf<String, Int>()
            val perDay = days.values.map { sameDay ->
                val n = sameDay.minWith(compareBy<GoodNews> { picked[it.authorId] ?: 0 }.thenBy { it.createdAt }.thenBy { it.id })
                picked[n.authorId] = (picked[n.authorId] ?: 0) + 1
                n
            }
            val seen = mutableSetOf<String>()
            val (fresh, again) = perDay.partition { seen.add(it.authorId) }
            val chosen = (fresh + again).take(PHOTOS).toSet()
            return perDay.filter { it in chosen }.map { WeekPhoto(it.id, it.thumbnailPath(it.photoIds.first())) }
        }

        fun birthdays(members: List<Member>, today: LocalDate): List<Birthday> =
            upcomingBirthdays(members, today).filter { !it.date.isAfter(today.plusDays(BIRTHDAY_DAYS)) }

        /** The trips that have begun and are not over; [trips] are already those the member follows. */
        fun tripsUnderWay(trips: List<Trip>, today: LocalDate): List<Trip> =
            trips.filter { !it.startDate.isAfter(today) && !it.endDate.isBefore(today) }.sortedBy { it.endDate }
    }
}
