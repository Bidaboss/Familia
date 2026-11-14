package fr.familia.app.core.data

import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import kotlinx.coroutines.flow.Flow

/** "Qui vient": the answers to an event. Screens never talk to Firestore directly. */
interface RsvpRepository {
    /** The answers to [eventId], updated live (and served from the offline cache). */
    fun observeRsvps(eventId: String): Flow<List<Rsvp>>

    /** Answers for [memberId]; [answeredBy] is the member signed in, who may answer for a child. */
    suspend fun setRsvp(eventId: String, memberId: String, status: RsvpStatus, answeredBy: String)
}
