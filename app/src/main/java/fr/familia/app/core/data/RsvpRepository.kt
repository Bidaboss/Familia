package fr.familia.app.core.data

import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** "Qui vient": the answers to an event. Screens never talk to Firestore directly. */
interface RsvpRepository {
    /** The answers to [eventId], updated live (and served from the offline cache). */
    fun observeRsvps(eventId: String): Flow<List<Rsvp>>

    /** Every answer of the family, by event id. */
    fun observeAllRsvps(): Flow<Map<String, List<Rsvp>>>

    /**
     * The answers to [eventIds] only, by event id, for the counters of the planning: the events to
     * come, rather than every answer of the family since the start. An event left out has no answer,
     * or its answers cannot be read (the planning then shows it without counters).
     */
    fun observeRsvpsOf(eventIds: List<String>): Flow<Map<String, List<Rsvp>>> {
        val wanted = eventIds.toSet()
        return observeAllRsvps().map { all -> all.filterKeys { it in wanted } }
    }

    /**
     * The answers of [memberId] alone to [eventIds], by event id, for « À faire » of the home
     * screen: one document per event instead of every answer of the family since the start.
     * An event left out has no answer. An event whose answer cannot be read comes with an empty
     * list: « À faire » then does not ask again, as if the answer was lost (note C de #317).
     */
    fun observeAnswersOf(memberId: String, eventIds: List<String>): Flow<Map<String, List<Rsvp>>> =
        observeAllRsvps().map { all ->
            eventIds.associateWith { id -> all[id].orEmpty().filter { it.memberId == memberId } }.filterValues { it.isNotEmpty() }
        }

    /** Answers for [memberId]; [answeredBy] is the member signed in, who may answer for a child. */
    suspend fun setRsvp(eventId: String, memberId: String, status: RsvpStatus, answeredBy: String)
}
