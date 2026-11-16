package fr.familia.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.model.BringItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreBringRepository(private val db: FirebaseFirestore) : BringRepository {

    private fun lines(eventId: String) = db.collection("events").document(eventId).collection("bring")

    override fun observe(eventId: String): Flow<List<BringItem>> = callbackFlow {
        val registration = lines(eventId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                // A line just added offline has no server time yet: it goes last.
                val sorted = snapshot.documents.sortedWith(compareBy(nullsLast<Timestamp>()) { it.getTimestamp("createdAt") })
                trySend(sorted.mapNotNull { it.toItem() })
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun add(eventId: String, label: String, authorId: String, authorName: String, take: Boolean) {
        lines(eventId).document().set(
            mapOf(
                "label" to label,
                "authorId" to authorId,
                "takerId" to if (take) authorId else null,
                "takerName" to if (take) authorName else null,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun take(eventId: String, itemId: String, memberId: String, firstName: String) {
        lines(eventId).document(itemId)
            .update(mapOf("takerId" to memberId, "takerName" to firstName, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    override suspend fun release(eventId: String, itemId: String) {
        lines(eventId).document(itemId)
            .update(mapOf("takerId" to null, "takerName" to null, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    override suspend fun delete(eventId: String, itemId: String) {
        lines(eventId).document(itemId).delete().await()
    }

    private fun DocumentSnapshot.toItem(): BringItem? = BringItem(
        id = id,
        label = getString("label") ?: return null,
        authorId = getString("authorId") ?: return null,
        takerId = getString("takerId"),
        takerName = getString("takerName"),
    )
}
