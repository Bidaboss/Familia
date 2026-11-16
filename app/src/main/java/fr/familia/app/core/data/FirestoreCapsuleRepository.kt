package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import fr.familia.app.core.model.Capsule
import fr.familia.app.core.model.CapsuleLeftover
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
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

    override fun observeCapsule(id: String): Flow<Capsule?> = callbackFlow {
        val registration = db.collection(CAPSULES).document(id).addSnapshotListener { snapshot, error ->
            when {
                // Removed meanwhile, or still sealed for this member: nothing to show.
                error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> trySend(null)
                error != null -> close(error)
                else -> trySend(snapshot?.takeIf { it.exists() }?.toCapsule())
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun deleteCapsule(id: String) {
        db.collection(CAPSULES).document(id).delete().await()
    }

    override fun newCapsuleId(): String = db.collection(CAPSULES).document().id

    override suspend fun createCapsule(capsule: Capsule) {
        db.collection(CAPSULES).document(capsule.id).set(
            mapOf(
                "text" to capsule.text,
                "photoCount" to capsule.photoCount.toLong(),
                "recipientIds" to capsule.recipientIds,
                "openAt" to capsule.openAt.toString(),
                "openedFor" to emptyList<String>(),
                "authorId" to capsule.authorId,
                "authorName" to capsule.authorName,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override fun observeLeftovers(): Flow<List<CapsuleLeftover>> = callbackFlow {
        val registration = db.collection(LEFTOVERS).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(
                    snapshot.documents.mapNotNull { doc ->
                        val count = (doc.getLong("count") ?: 0L).toInt()
                        if (count <= 0) return@mapNotNull null
                        CapsuleLeftover(doc.id, doc.getString("memberName").orEmpty(), count, doc.getBoolean("deleteAsked") == true)
                    }.sortedBy { it.memberName.lowercase() },
                )
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun askDeleteLeftovers(memberId: String) {
        db.collection(LEFTOVERS).document(memberId).update("deleteAsked", true).await()
    }

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
        const val LEFTOVERS = "capsuleLeftovers"
    }
}
