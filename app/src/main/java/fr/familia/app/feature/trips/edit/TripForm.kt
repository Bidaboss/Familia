package fr.familia.app.feature.trips.edit

import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripLandscape
import java.time.LocalDate

/** What is being typed in the trip form. Participants always follow the trip (récit 12). */
data class TripForm(
    val title: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val participantIds: Set<String> = emptySet(),
    val readerIds: Set<String> = emptySet(),
    /** The decor of the path (récit 81): dunes until another one is chosen. */
    val landscape: TripLandscape = TripLandscape.Dunes,
) {
    /** Ticks or unticks a participant; a new participant also follows the trip. */
    fun withParticipant(id: String, taking: Boolean): TripForm =
        if (taking) copy(participantIds = participantIds + id, readerIds = readerIds + id) else copy(participantIds = participantIds - id)

    /** « Tout un foyer », « Toute la famille » (PRD rév. 190): ticks them all, each one also following. */
    fun withParticipants(ids: Collection<String>): TripForm =
        copy(participantIds = participantIds + ids, readerIds = readerIds + ids)

    /**
     * The participants picked by household (règle de l'admin, 17/11): a newcomer also follows the trip,
     * like [withParticipant]; someone unticked keeps following it.
     */
    fun withParticipantSet(ids: Set<String>): TripForm = copy(participantIds = ids, readerIds = readerIds + (ids - participantIds))

    /** The followers picked by household; a participant always stays one. */
    fun withReaderSet(ids: Set<String>): TripForm = copy(readerIds = ids + participantIds)

    /** Ticks or unticks a follower; a participant cannot be unticked. */
    fun withReader(id: String, following: Boolean): TripForm = when {
        following -> copy(readerIds = readerIds + id)
        id in participantIds -> this
        else -> copy(readerIds = readerIds - id)
    }

    companion object {
        fun from(trip: Trip) = TripForm(
            title = trip.title,
            startDate = trip.startDate,
            endDate = trip.endDate,
            participantIds = trip.participantIds.toSet(),
            readerIds = trip.readerIds.toSet(),
            landscape = trip.landscape,
        )
    }
}

enum class TripFormError { TitleMissing, TitleTooLong, StartMissing, EndMissing, EndBeforeStart, TooManyParticipants }

const val MAX_TRIP_TITLE = 80

/** The longest past trip given in days (règle 15). */
const val MAX_PAST_TRIP_DAYS = 365

/** A trip that started before [today]: its length can be given in days rather than by a return date (règle 15). */
fun startsInPast(form: TripForm, today: java.time.LocalDate): Boolean = form.startDate?.isBefore(today) == true

/** How many days the trip lasts, the first and the last one counted; null without both dates in order. */
fun formDays(form: TripForm): Int? {
    val start = form.startDate ?: return null
    val end = form.endDate?.takeIf { it >= start } ?: return null
    return java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
}

/** The return date that makes the trip last [days] days, within 1 and [MAX_PAST_TRIP_DAYS]; unchanged without a departure. */
fun withDays(form: TripForm, days: Int): TripForm {
    val start = form.startDate ?: return form
    return form.copy(endDate = start.plusDays(days.coerceIn(1, MAX_PAST_TRIP_DAYS) - 1L))
}

/** Same limits as the security rules. */
const val MAX_PARTICIPANTS = 13

fun validate(form: TripForm): Set<TripFormError> = buildSet {
    val title = form.title.trim()
    if (title.isEmpty()) add(TripFormError.TitleMissing)
    if (title.length > MAX_TRIP_TITLE) add(TripFormError.TitleTooLong)
    if (form.startDate == null) add(TripFormError.StartMissing)
    if (form.endDate == null) add(TripFormError.EndMissing)
    if (form.startDate != null && form.endDate != null && form.endDate < form.startDate) add(TripFormError.EndBeforeStart)
    if (form.participantIds.size > MAX_PARTICIPANTS) add(TripFormError.TooManyParticipants)
}
