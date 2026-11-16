package fr.familia.app.core.data

import fr.familia.app.core.model.Feedback
import fr.familia.app.core.model.FeedbackContext
import fr.familia.app.core.model.Member
import kotlinx.coroutines.flow.Flow

/** feedback/{feedbackId} (PRD, récit 6 sexies). */
interface FeedbackRepository {
    /** [screenshot] is a JPEG of at most 150 KB, sent to Storage before the request names it. */
    suspend fun send(kind: Feedback.Kind, text: String, me: Member, context: FeedbackContext, screenshot: ByteArray? = null)

    /** Every one, for the admin principal (the rules refuse anyone else). */
    fun observeAll(): Flow<List<Feedback>>

    /** « Mes demandes »: those [memberId] sent. */
    fun observeMine(memberId: String): Flow<List<Feedback>>

    /** The admin principal's answer: a state and an optional word. */
    suspend fun setStatus(id: String, status: Feedback.Status, note: String?)

    /** Removes the request, and its [screenshot] first when it has one. */
    suspend fun delete(id: String, screenshot: String? = null)
}
