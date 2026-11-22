package fr.familia.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.familia.app.core.model.JoinMe
import fr.familia.app.core.place.Place
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant

class FirestoreJoinMeRepository(
    private val db: FirebaseFirestore,
    private val clock: () -> Instant = Instant::now,
) : JoinMeRepository {

    private val shares get() = db.collection("joinMe")

    override fun observeReceived(memberId: String): Flow<List<JoinMe>> =
        observe(shares.whereArrayContains("recipientIds", memberId))

    override fun observeSent(memberId: String): Flow<List<JoinMe>> =
        observe(shares.whereEqualTo("authorId", memberId))

    // Sorted on the phone: an orderBy would need a composite index for each query.
    private fun observe(query: Query): Flow<List<JoinMe>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull { it.toJoinMe() }.sortedByDescending { it.expireAt })
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun send(authorId: String, authorName: String, recipientIds: List<String>, place: Place, note: String?) {
        // The rules accept « now + 2 h » from the phone's clock, within an hour.
        val expireAt = clock().plus(JoinMe.LIFETIME)
        shares.document().set(
            mapOf(
                "authorId" to authorId,
                "authorName" to authorName,
                "recipientIds" to recipientIds,
                "name" to place.name,
                "address" to place.address,
                "lat" to place.lat,
                "lng" to place.lng,
                "note" to note,
                "createdAt" to FieldValue.serverTimestamp(),
                "expireAt" to Timestamp(expireAt.epochSecond, expireAt.nano),
            ),
        ).await()
    }

    override suspend fun delete(shareId: String) {
        shares.document(shareId).delete().await()
    }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toJoinMe(): JoinMe? {
        val lat = getDouble("lat") ?: return null
        val lng = getDouble("lng") ?: return null
        val expireAt = getTimestamp("expireAt") ?: return null
        return JoinMe(
            id = id,
            authorId = getString("authorId") ?: return null,
            authorName = getString("authorName") ?: "",
            recipientIds = (get("recipientIds") as? List<String>).orEmpty(),
            place = Place(lat, lng, name = getString("name"), address = getString("address")),
            note = getString("note"),
            createdAt = getTimestamp("createdAt")?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) },
            expireAt = Instant.ofEpochSecond(expireAt.seconds, expireAt.nanoseconds.toLong()),
        )
    }
}
