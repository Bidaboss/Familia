package fr.familia.app.core.data

import fr.familia.app.core.model.Member
import fr.familia.app.core.model.TripWord
import kotlinx.coroutines.flow.Flow

/**
 * The words of the steps of a trip (récit 88), in `trips/{tripId}/words/{stepId}`: one per step, at
 * most. The followers read them; a participant writes one, and only its author changes it or takes
 * it back. See docs/data-model.md.
 */
interface TripWordRepository {
    /** The words of the trip, by step, updated live. A member who may not read them gets none. */
    fun observeWords(tripId: String): Flow<Map<String, TripWord>>

    /** The word of one step, or null, updated live. */
    fun observeWord(tripId: String, stepId: String): Flow<TripWord?>

    /** Writes the word of [stepId], signed by [me] when it is new; later its author changes only the text and the photos. */
    suspend fun saveWord(tripId: String, stepId: String, text: String, photoIds: List<String>, me: Member, isNew: Boolean)

    /** Takes the word back (its hearts and comments go with it, on the server). */
    suspend fun deleteWord(tripId: String, stepId: String)

    /** Puts or takes back the heart of [memberId], and nothing else. */
    suspend fun setHeart(tripId: String, stepId: String, memberId: String, hearted: Boolean)

    /** The comments under the word of [stepId], with « @Prénom »: the same as under an event. */
    fun comments(tripId: String, stepId: String): EventCommentRepository
}
