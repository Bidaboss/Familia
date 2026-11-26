package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.core.model.TripPlace
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime

/** `trips/{tripId}/places/{placeId}` (récit 103). The hour is « HH:mm », as for the steps. */
class FirestoreTripPlaceRepository(private val db: FirebaseFirestore) : TripPlaceRepository {

    private fun places(tripId: String) = db.collection("trips").document(tripId).collection("places")

    override fun observePlaces(tripId: String): Flow<List<TripPlace>> = callbackFlow {
        val registration = places(tripId).addSnapshotListener { snapshot, error ->
            when {
                // Not a follower (any more): there is nothing to show, not a failure.
                error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    trySend(emptyList())
                    close()
                }
                error != null -> close(error)
                snapshot != null -> trySend(snapshot.documents.mapNotNull { it.toPlace() })
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun getPlace(tripId: String, placeId: String): TripPlace? =
        places(tripId).document(placeId).get().await().takeIf { it.exists() }?.toPlace()

    override fun newPlaceId(tripId: String): String = places(tripId).document().id

    override suspend fun savePlace(tripId: String, place: TripPlace, isNew: Boolean) {
        val data = mutableMapOf<String, Any?>(
            "stepId" to place.stepId,
            "kind" to place.kind.key,
            "name" to place.name,
            "address" to place.address,
            "lat" to place.lat,
            "lng" to place.lng,
            "arriveTime" to place.arriveTime?.let { String.format(java.util.Locale.ROOT, "%02d:%02d", it.hour, it.minute) },
            "note" to place.note,
            "cost" to place.cost,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        // Récit 122: only written when set, so a place without a day still saves before the rules know them.
        place.day?.let { data["day"] = it.toString() }
        place.checkIn?.let { data["checkIn"] = it.toString() }
        place.checkOut?.let { data["checkOut"] = it.toString() }
        // The same write either way: the rules check the whole shape, and the id is known before.
        places(tripId).document(place.id).set(data).await()
    }

    override suspend fun deletePlace(tripId: String, placeId: String) {
        places(tripId).document(placeId).delete().await()
    }
}

/** Reads a place; null for a document this version cannot read (unknown kind, no name). */
internal fun DocumentSnapshot.toPlace(): TripPlace? {
    val kind = TripPlaceKind.of(getString("kind")) ?: return null
    val name = getString("name")?.takeIf { it.isNotBlank() } ?: return null
    val stepId = getString("stepId") ?: return null
    return TripPlace(
        id = id,
        stepId = stepId,
        kind = kind,
        name = name,
        address = getString("address"),
        lat = getDouble("lat"),
        lng = getDouble("lng"),
        arriveTime = getString("arriveTime")?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
        note = getString("note"),
        cost = getDouble("cost"),
        day = date("day"),
        checkIn = date("checkIn"),
        checkOut = date("checkOut"),
    )
}

/** A day stored « yyyy-MM-dd »; null when absent or unreadable. */
private fun DocumentSnapshot.date(field: String): LocalDate? =
    getString(field)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
