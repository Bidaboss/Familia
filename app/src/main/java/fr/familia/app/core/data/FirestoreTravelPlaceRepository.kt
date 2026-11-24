package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import fr.familia.app.core.model.TravelPlace
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/** `members/{memberId}/travelPlaces/{placeId}` (récit 107). The date is « yyyy-MM-dd », as for the trips. */
class FirestoreTravelPlaceRepository(private val db: FirebaseFirestore) : TravelPlaceRepository {

    private fun places(memberId: String) = db.collection("members").document(memberId).collection("travelPlaces")

    override fun observePlaces(memberId: String): Flow<List<TravelPlace>> = callbackFlow {
        val registration = places(memberId).addSnapshotListener { snapshot, error ->
            when {
                // Someone else's carnet (« Voir comme »): nothing to show, not a failure.
                error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    trySend(emptyList())
                    close()
                }
                error != null -> close(error)
                snapshot != null -> trySend(snapshot.documents.mapNotNull { it.toTravelPlace() }.sortedByDescending { it.date })
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun addPlace(memberId: String, place: TravelPlace) {
        val data = mapOf(
            "name" to place.name.trim().take(TravelPlace.MAX_NAME),
            "country" to place.country?.trim()?.take(TravelPlace.MAX_COUNTRY)?.ifEmpty { null },
            "countryCode" to place.countryCode?.uppercase(java.util.Locale.ROOT)?.takeIf { it.length == 2 },
            "lat" to place.lat,
            "lng" to place.lng,
            "date" to place.date.toString(),
            "days" to place.days.toLong(),
            "createdAt" to FieldValue.serverTimestamp(),
        )
        places(memberId).document().set(data).await()
    }

    override suspend fun deletePlace(memberId: String, placeId: String) {
        places(memberId).document(placeId).delete().await()
    }
}

/** Reads a hand-added destination; null for a document this version cannot read. */
internal fun DocumentSnapshot.toTravelPlace(): TravelPlace? {
    val name = getString("name")?.takeIf { it.isNotBlank() } ?: return null
    val lat = getDouble("lat") ?: return null
    val lng = getDouble("lng") ?: return null
    val date = getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    return TravelPlace(
        id = id,
        name = name,
        country = getString("country"),
        countryCode = getString("countryCode"),
        lat = lat,
        lng = lng,
        date = date,
        days = getLong("days")?.toInt() ?: 1,
    )
}
