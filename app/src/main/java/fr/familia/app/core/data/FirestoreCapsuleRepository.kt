package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.familia.app.core.model.Capsule
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalDate

/**
 * Capsules live in `capsules/{capsuleId}`. The rules let a member read only those they wrote and
 * those opened for them, so each list is its own query. See docs/data-model.md.
 */
class FirestoreCapsuleRepository(private val db: FirebaseFirestore) : CapsuleRepository {

    override fun observeWrittenBy(memberId: String): Flow<List<Capsule>> =
        observe(db.collection(CAPSULES).whereEqualTo("authorId", memberId))

    override fun observeOpenedFor(memberId: String): Flow<List<Capsule>> =
        observe(db.collection(CAPSULES).whereArrayContains("openedFor", memberId))

    private fun observe(query: Query): Flow<List<Capsule>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull { it.toCapsule() })
            }
        }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toCapsule(): Capsule? {
        val text = getString("text") ?: return null
        val openAt = getString("openAt")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return Capsule(
            id = id,
            text = text,
            photoCount = (getLong("photoCount") ?: 0L).toInt().coerceIn(0, Capsule.MAX_PHOTOS),
            recipientIds = ids("recipientIds"),
            openAt = openAt,
            openedFor = ids("openedFor"),
            openedAt = getTimestamp("openedAt")?.toDate()?.toInstant(),
            authorId = getString("authorId").orEmpty(),
            authorName = getString("authorName").orEmpty(),
            createdAt = getTimestamp("createdAt")?.toDate()?.toInstant(),
        )
    }

    private fun DocumentSnapshot.ids(field: String): List<String> = (get(field) as? List<*>)?.filterIsInstance<String>().orEmpty()

    private companion object {
        const val CAPSULES = "capsules"
    }
}
