package fr.familia.app.feature.yearago

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Trip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * « Il y a un an » (PRD, récit 36): a souvenir of the same day, one year earlier. No notification;
 * it shows only to those who could see the original, and « Ne plus me montrer ce souvenir » takes
 * it away for me alone (avis EXPERT n° 12).
 */
sealed interface YearAgoMemory {
    /** Stable, to remember that I asked not to see it again. */
    val key: String

    data class News(val news: GoodNews) : YearAgoMemory {
        override val key: String get() = "news-${news.id}"
    }

    data class TripPhoto(val trip: Trip, val photo: AlbumPhoto) : YearAgoMemory {
        override val key: String get() = "trip-${trip.id}-${photo.id}"
    }
}

object YearAgo {
    val ZONE: ZoneId = ZoneId.of("Europe/Paris")

    /** The day one year before [today]; on 29 February, the 28th. */
    fun dayOf(today: LocalDate): LocalDate = today.minusYears(1)

    /** The bounds of that day in Paris, to read the good news published then. */
    fun bounds(today: LocalDate): Pair<Instant, Instant> {
        val day = dayOf(today)
        return day.atStartOfDay(ZONE).toInstant() to day.plusDays(1).atStartOfDay(ZONE).toInstant()
    }

    /** The trips I follow that were under way that day: only their photos can be of that day. */
    fun tripsOfThatDay(followed: List<Trip>, today: LocalDate): List<Trip> {
        val day = dayOf(today)
        return followed.filter { !it.startDate.isAfter(day) && !it.endDate.isBefore(day) }
    }

    /**
     * The souvenir to show: a good news of that day (one with a photo first, then the most loved),
     * else a photo of a trip taken that day. A good news one hid, a « Défi réussi » of Familia and a
     * souvenir one set aside are left out.
     */
    fun pick(
        today: LocalDate,
        memberId: String,
        news: List<GoodNews>,
        tripPhotos: List<Pair<Trip, AlbumPhoto>>,
        dismissed: Set<String>,
    ): YearAgoMemory? {
        val day = dayOf(today)
        fun Instant?.isThatDay() = this != null && atZone(ZONE).toLocalDate() == day
        val fromNews = news
            .filter { it.createdAt.isThatDay() && it.challengeMonth == null && it.birthdayOf == null && !it.isHiddenFor(memberId) }
            .map { YearAgoMemory.News(it) }
            .filterNot { it.key in dismissed }
            .sortedWith(
                compareByDescending<YearAgoMemory.News> { it.news.photoIds.isNotEmpty() }
                    .thenByDescending { it.news.reactions.size }
                    .thenBy { it.news.createdAt },
            )
            .firstOrNull()
        if (fromNews != null) return fromNews
        return tripPhotos
            .filter { (_, photo) -> photo.createdAt.isThatDay() }
            .map { (trip, photo) -> YearAgoMemory.TripPhoto(trip, photo) }
            .filterNot { it.key in dismissed }
            .minByOrNull { it.photo.createdAt!! }
    }
}
