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

/** A photo of the week: the thumbnail of a good news, which opens it. */
data class WeekPhoto(val newsId: String, val thumbnailPath: String)

/**
 * « La semaine de la famille » (récit 36): the three photos of the week, the birthdays of the next
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
        const val BIRTHDAY_DAYS = 14L
        val WEEK: Duration = Duration.ofDays(7)

        /**
         * The photos of the good news of the last 7 days that [memberId] did not hide, the most
         * loved first (then the newest), one per good news before a second one of the same.
         * No count, no name: the screen only shows them.
         */
        fun photos(news: List<GoodNews>, memberId: String, now: Instant): List<WeekPhoto> {
            val since = now.minus(WEEK)
            val week = news
                .filter { it.createdAt != null && !it.createdAt.isBefore(since) && it.photoIds.isNotEmpty() }
                .filterNot { it.isHiddenFor(memberId) }
                .sortedWith(compareByDescending<GoodNews> { it.reactions.size }.thenByDescending { it.createdAt })
            val rounds = (0 until GoodNews.PHOTOS_MAX).flatMap { i ->
                week.mapNotNull { n -> n.photoIds.getOrNull(i)?.let { WeekPhoto(n.id, n.thumbnailPath(it)) } }
            }
            return rounds.take(PHOTOS)
        }

        fun birthdays(members: List<Member>, today: LocalDate): List<Birthday> =
            upcomingBirthdays(members, today).filter { !it.date.isAfter(today.plusDays(BIRTHDAY_DAYS)) }

        /** The trips that have begun and are not over; [trips] are already those the member follows. */
        fun tripsUnderWay(trips: List<Trip>, today: LocalDate): List<Trip> =
            trips.filter { !it.startDate.isAfter(today) && !it.endDate.isBefore(today) }.sortedBy { it.endDate }
    }
}
