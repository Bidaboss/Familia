package fr.familia.app.feature.trips.edit

import fr.familia.app.core.model.Trip
import java.time.LocalDate

/** What is being typed in the trip form. Participants always follow the trip (récit 12). */
data class TripForm(
    val title: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val participantIds: Set<String> = emptySet(),
    val readerIds: Set<String> = emptySet(),
) {
    /** Ticks or unticks a participant; a new participant also follows the trip. */
    fun withParticipant(id: String, taking: Boolean): TripForm =
        if (taking) copy(participantIds = participantIds + id, readerIds = readerIds + id) else copy(participantIds = participantIds - id)

    /** « Tout un foyer », « Toute la famille » (PRD rév. 190): ticks them all, each one also following. */
    fun withParticipants(ids: Collection<String>): TripForm =
        copy(participantIds = participantIds + ids, readerIds = readerIds + ids)

    /**
     * The participants picked by household (règle de Kévin, 17/11): a newcomer also follows the trip,
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
        )
    }
}

enum class TripFormError { TitleMissing, TitleTooLong, StartMissing, EndMissing, EndBeforeStart, TooManyParticipants }

const val MAX_TRIP_TITLE = 80

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
