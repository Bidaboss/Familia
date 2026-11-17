package fr.familia.app.core.data

import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** "Qui vient": the answers to an event. Screens never talk to Firestore directly. */
interface RsvpRepository {
    /** The answers to [eventId], updated live (and served from the offline cache). */
    fun observeRsvps(eventId: String): Flow<List<Rsvp>>

    /** Every answer of the family, by event id, for the counters of the planning. */
    fun observeAllRsvps(): Flow<Map<String, List<Rsvp>>>

    /**
     * The answers of [memberId] alone to [eventIds], by event id, for « À faire » of the home
     * screen: one document per event instead of every answer of the family since the start.
     */
    fun observeAnswersOf(memberId: String, eventIds: List<String>): Flow<Map<String, List<Rsvp>>> =
        observeAllRsvps().map { all ->
            eventIds.associateWith { id -> all[id].orEmpty().filter { it.memberId == memberId } }.filterValues { it.isNotEmpty() }
        }

    /** Answers for [memberId]; [answeredBy] is the member signed in, who may answer for a child. */
    suspend fun setRsvp(eventId: String, memberId: String, status: RsvpStatus, answeredBy: String)
}
