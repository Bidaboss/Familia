package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.model.Ride
import fr.familia.app.core.model.RideNeed
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalTime

/** events/{eventId}/rides/{driverId} and rideNeeds/{memberId} (PRD, récit 52). See docs/data-model.md. */
interface CarpoolRepository {
    fun observeRides(eventId: String): Flow<List<Ride>>

    fun observeNeeds(eventId: String): Flow<List<RideNeed>>

    /** « J'ai X places », or a change of it: the passengers already in stay. */
    suspend fun offer(eventId: String, driverId: String, seats: Int, from: String?, leaveAt: LocalTime?, isNew: Boolean)

    suspend fun withdraw(eventId: String, driverId: String)

    /** [memberId] gets in the car of [driverId], leaving the car of [leaving] first when they were in one. */
    suspend fun getIn(eventId: String, driverId: String, memberId: String, leaving: String?)

    suspend fun getOut(eventId: String, driverId: String, memberId: String)

    suspend fun askSeat(eventId: String, memberId: String, askedBy: String)

    suspend fun cancelAsk(eventId: String, memberId: String)
}

class FirestoreCarpoolRepository(private val db: FirebaseFirestore) : CarpoolRepository {

    private fun event(eventId: String) = db.collection("events").document(eventId)
    private fun rides(eventId: String) = event(eventId).collection("rides")
    private fun needs(eventId: String) = event(eventId).collection("rideNeeds")

    override fun observeRides(eventId: String): Flow<List<Ride>> = callbackFlow {
        val registration = rides(eventId).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else if (snapshot != null) trySend(snapshot.documents.mapNotNull { it.toRide() })
        }
        awaitClose { registration.remove() }
    }

    override fun observeNeeds(eventId: String): Flow<List<RideNeed>> = callbackFlow {
        val registration = needs(eventId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull { d -> d.getString("askedBy")?.let { RideNeed(d.id, it) } })
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun offer(eventId: String, driverId: String, seats: Int, from: String?, leaveAt: LocalTime?, isNew: Boolean) {
        val fields = mapOf(
            "seats" to seats,
            "from" to from,
            "leaveAt" to leaveAt?.let { "%02d:%02d".format(it.hour, it.minute) },
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val ref = rides(eventId).document(driverId)
        if (isNew) {
            ref.set(fields + mapOf("passengers" to emptyList<String>(), "createdAt" to FieldValue.serverTimestamp())).await()
        } else {
            ref.update(fields).await()
        }
    }

    override suspend fun withdraw(eventId: String, driverId: String) {
        rides(eventId).document(driverId).delete().await()
    }

    override suspend fun getIn(eventId: String, driverId: String, memberId: String, leaving: String?) {
        // One change per write, as the rules ask: out of the old car, then in the new one, together.
        db.batch().apply {
            if (leaving != null && leaving != driverId) {
                update(rides(eventId).document(leaving), mapOf("passengers" to FieldValue.arrayRemove(memberId), "updatedAt" to FieldValue.serverTimestamp()))
            }
            update(rides(eventId).document(driverId), mapOf("passengers" to FieldValue.arrayUnion(memberId), "updatedAt" to FieldValue.serverTimestamp()))
        }.commit().await()
    }

    override suspend fun getOut(eventId: String, driverId: String, memberId: String) {
        rides(eventId).document(driverId)
            .update(mapOf("passengers" to FieldValue.arrayRemove(memberId), "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    override suspend fun askSeat(eventId: String, memberId: String, askedBy: String) {
        needs(eventId).document(memberId).set(mapOf("askedBy" to askedBy, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    override suspend fun cancelAsk(eventId: String, memberId: String) {
        needs(eventId).document(memberId).delete().await()
    }

    private fun DocumentSnapshot.toRide(): Ride? {
        val seats = getLong("seats")?.toInt() ?: return null
        @Suppress("UNCHECKED_CAST")
        val passengers = (get("passengers") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val leaveAt = getString("leaveAt")?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        return Ride(driverId = id, seats = seats, from = getString("from"), leaveAt = leaveAt, passengers = passengers)
    }
}
