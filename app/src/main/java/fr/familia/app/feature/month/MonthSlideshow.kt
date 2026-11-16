package fr.familia.app.feature.month

import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.concerns
import fr.familia.app.feature.events.album.eventAlbumFolder
import fr.familia.app.feature.trips.photos.tripAlbumFolder
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/*
 * The slideshow of the month (PRD, récit 42): the photos of the month, the birthdays, and a video
 * made with the film of the trips (PRODUIT-33). It shows on the Accueil from the last Sunday of the
 * month. Each member only sees what they already saw: the trips they follow, and the events that
 * concern them (the shared function concerns). Hidden behind Feature.MonthSlideshow.
 */

/** The last Sunday of [month], when the slideshow comes to the Accueil. */
fun lastSundayOf(month: YearMonth): LocalDate =
    month.atEndOfMonth().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

/** True from the last Sunday of the month to its last day. */
fun isSlideshowTime(today: LocalDate): Boolean = !today.isBefore(lastSundayOf(YearMonth.from(today)))

/**
 * The month whose slideshow a recap notice opens: the one of [recapWeek] (AAAA-MM-JJ) when it is
 * the last Sunday of its month, as the server tells it; null for an ordinary week.
 */
fun slideshowMonthOf(recapWeek: String): YearMonth? {
    val sunday = runCatching { LocalDate.parse(recapWeek) }.getOrNull() ?: return null
    val month = YearMonth.from(sunday)
    return month.takeIf { lastSundayOf(it) == sunday }
}

/** The members born in [month], by day. */
fun birthdaysOf(family: Family, month: YearMonth): List<Member> = family.members
    .filter { it.birthDate?.month == month.month }
    .sortedBy { it.birthDate?.dayOfMonth }

/** The albums [me] can see this month: the events of the month that concern them, then the trips they follow. */
fun monthAlbums(month: YearMonth, me: Member, family: Family, events: List<Event>, followedTrips: List<Trip>): MonthAlbums = MonthAlbums(
    eventFolders = events
        .filter { YearMonth.from(it.date) == month && concerns(it.target, me, family) }
        .map { eventAlbumFolder(it.id) },
    tripFolders = followedTrips
        .filter { !it.startDate.isAfter(month.atEndOfMonth()) }
        .map { tripAlbumFolder(it.id) },
)

/** All the photos of the events of the month; from the trips, only those added during the month. */
data class MonthAlbums(val eventFolders: List<String>, val tripFolders: List<String>) {
    val all: List<String> get() = eventFolders + tripFolders

    fun keep(photo: AlbumPhoto, month: YearMonth, zone: ZoneId): Boolean =
        photo.folder in eventFolders || photo.createdAt?.let { YearMonth.from(it.atZone(zone)) == month } == true
}

/** The slideshow of one month. */
@Serializable
data class MonthSlideshowDestination(val year: Int, val month: Int)
