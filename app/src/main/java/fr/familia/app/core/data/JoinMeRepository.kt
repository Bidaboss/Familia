package fr.familia.app.core.data

import fr.familia.app.core.model.JoinMe
import fr.familia.app.core.place.Place
import kotlinx.coroutines.flow.Flow

/** joinMe/{shareId} (PRD, récit 33). See docs/data-model.md. */
interface JoinMeRepository {
    /** What was sent to [memberId], newest first, updated live (expired ones included: filter them). */
    fun observeReceived(memberId: String): Flow<List<JoinMe>>

    /** What [memberId] sent, newest first, updated live. */
    fun observeSent(memberId: String): Flow<List<JoinMe>>

    suspend fun send(authorId: String, authorName: String, recipientIds: List<String>, place: Place, note: String?)

    /** « Ne plus partager »: only its author may. */
    suspend fun delete(shareId: String)
}
