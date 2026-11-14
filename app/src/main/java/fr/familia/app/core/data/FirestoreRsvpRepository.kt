package fr.familia.app.core.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Answers live in `events/{eventId}/rsvps/{memberId}`. See docs/data-model.md for the rights. */
class FirestoreRsvpRepository(private val db: FirebaseFirestore) : RsvpRepository {

    override fun observeRsvps(eventId: String): Flow<List<Rsvp>> = callbackFlow {
        val registration = db.collection("events").document(eventId).collection("rsvps")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(
                        snapshot.documents.mapNotNull { doc ->
                            val status = RsvpStatus.fromFirestore(doc.getString("status")) ?: return@mapNotNull null
                            Rsvp(doc.id, status, doc.getString("answeredBy").orEmpty())
                        },
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    /**
     * One listener on the `rsvps` collection group instead of one per planning row. The family is
     * small (a few dozen answers a month), so reading them all costs less than many listeners.
     */
    override fun observeAllRsvps(): Flow<Map<String, List<Rsvp>>> = callbackFlow {
        val registration = db.collectionGroup("rsvps")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(
                        snapshot.documents.mapNotNull { doc ->
                            val eventId = doc.reference.parent.parent?.id ?: return@mapNotNull null
                            val status = RsvpStatus.fromFirestore(doc.getString("status")) ?: return@mapNotNull null
                            eventId to Rsvp(doc.id, status, doc.getString("answeredBy").orEmpty())
                        }.groupBy({ it.first }, { it.second }),
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun setRsvp(eventId: String, memberId: String, status: RsvpStatus, answeredBy: String) {
        db.collection("events").document(eventId).collection("rsvps").document(memberId)
            .set(
                mapOf(
                    "status" to status.firestoreValue,
                    "answeredBy" to answeredBy,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
    }
}
