package fr.familia.app.feature.yearago

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.shotAt
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.concerns
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * « Il y a N ans » (PRD, récit 36, étendu par la rév. 258): a souvenir of the same day, one to ten
 * years earlier. No notification; it shows only to those who could see the original, and « Ne plus
 * me montrer ce souvenir » takes it away for me alone (avis EXPERT n° 12). A good news first, else
 * a photo of the album of a trip or an event, taken that day (EXIF) or else added that day. The
 * same souvenir stays all day.
 */
sealed interface YearAgoMemory {
    /** Stable, to remember that I asked not to see it again. */
    val key: String

    /** How many years ago, from 1 to [YearAgo.MAX_YEARS]. */
    val years: Int

    data class News(val news: GoodNews, override val years: Int = 1) : YearAgoMemory {
        override val key: String get() = "news-${news.id}"
    }

    data class Photo(val album: MemoryAlbum, val photo: AlbumPhoto, override val years: Int = 1) : YearAgoMemory {
        /** « trip-… » as before the rév. 258, so a souvenir set aside then stays aside. */
        override val key: String get() = "${album.kind.prefix}-${album.id}-${photo.id}"
    }
}

/** The album a photo comes from: a trip, or an event (récit 41). */
data class MemoryAlbum(val kind: Kind, val id: String, val title: String) {
    enum class Kind(val prefix: String) { Trip("trip"), Event("event") }
}

object YearAgo {
    val ZONE: ZoneId = ZoneId.of("Europe/Paris")

    /** The family only looks this far back. */
    const val MAX_YEARS = 10

    /** The day [years] before [today]; on 29 February, the 28th. */
    fun dayOf(today: LocalDate, years: Int = 1): LocalDate = today.minusYears(years.toLong())

    /** The same day, one to [MAX_YEARS] years before [today]. */
    fun days(today: LocalDate): List<LocalDate> = (1..MAX_YEARS).map { dayOf(today, it) }

    /** How many years before [today] [day] is, when it is one of [days]; else null. */
    fun yearsAgo(today: LocalDate, day: LocalDate): Int? =
        (today.year - day.year).takeIf { it in 1..MAX_YEARS && dayOf(today, it) == day }

    private fun Instant.day(): LocalDate = atZone(ZONE).toLocalDate()

    /** The bounds of each of those days in Paris, to read the good news published then. */
    fun bounds(today: LocalDate): List<Pair<Instant, Instant>> = days(today).map { day ->
        day.atStartOfDay(ZONE).toInstant() to day.plusDays(1).atStartOfDay(ZONE).toInstant()
    }

    /** The trips I follow that were under way on one of those days. */
    fun tripsOfThoseDays(followed: List<Trip>, today: LocalDate): List<Trip> {
        val days = days(today)
        return followed.filter { trip -> days.any { !trip.startDate.isAfter(it) && !trip.endDate.isBefore(it) } }
    }

    /** The events of one of those days that concern [memberId], as in « Albums »: their album may hold photos of that day. */
    fun eventsOfThoseDays(events: List<Event>, today: LocalDate, family: Family, memberId: String): List<Event> {
        val me = family.members.firstOrNull { it.id == memberId } ?: return emptyList()
        return events.filter { yearsAgo(today, it.date) != null && concerns(it.target, me, family) }
    }

    /**
     * The souvenir to show. [kept] is the one shown earlier today: it stays while it is still
     * there, so the card is the same all day. Else a good news (one with a photo first, then the
     * most loved), else a photo of an album, chosen by the day so that it changes the next day.
     * A good news I hid, a « Défi réussi » or a birthday of Familia, a souvenir I set aside and
     * one the admin principal took away for everyone ([hidden]) are left out.
     */
    fun pick(
        today: LocalDate,
        memberId: String,
        news: List<GoodNews>,
        photos: List<Pair<MemoryAlbum, AlbumPhoto>>,
        dismissed: Set<String>,
        hidden: Set<String> = emptySet(),
        kept: String? = null,
    ): YearAgoMemory? {
        val left = dismissed + hidden
        val fromNews = news
            .filter { it.challengeMonth == null && it.birthdayOf == null && !it.isHiddenFor(memberId) }
            .mapNotNull { n -> n.createdAt?.let { yearsAgo(today, it.day()) }?.let { YearAgoMemory.News(n, it) } }
            .filterNot { it.key in left }
            .sortedWith(
                compareByDescending<YearAgoMemory.News> { it.news.photoIds.isNotEmpty() }
                    .thenByDescending { it.news.reactions.size }
                    .thenBy { it.news.createdAt },
            )
        val fromPhotos = photos
            .mapNotNull { (album, photo) -> photo.shotAt?.let { yearsAgo(today, it.day()) }?.let { YearAgoMemory.Photo(album, photo, it) } }
            .filterNot { it.key in left }
            .distinctBy { it.key }
            .sortedBy { it.key }
        (fromNews + fromPhotos).firstOrNull { it.key == kept }?.let { return it }
        fromNews.firstOrNull()?.let { return it }
        if (fromPhotos.isEmpty()) return null
        return fromPhotos[Math.floorMod((today.toString() + memberId).hashCode(), fromPhotos.size)]
    }
}
