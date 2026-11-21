package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.familia.app.core.model.Consumption
import java.time.LocalDate
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreConsumptionRepository(private val db: FirebaseFirestore) : ConsumptionRepository {

    override fun observeLatest(): Flow<Consumption?> = callbackFlow {
        val registration = db.collection("consumption")
            .orderBy("day", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
                    trySend(snapshot?.documents?.firstNotNullOfOrNull(::toConsumption))
                }
            }
        awaitClose { registration.remove() }
    }

    private fun toConsumption(doc: DocumentSnapshot): Consumption? {
        val day = doc.getString("day")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return Consumption(
            day = day,
            reads = doc.getLong("reads") ?: 0,
            writes = doc.getLong("writes") ?: 0,
            deletes = doc.getLong("deletes") ?: 0,
            storageBytes = doc.getLong("storageBytes"),
            photoBytes = doc.getDouble("photoBytes")?.toLong(),
            monthDownloadBytes = doc.getDouble("monthDownloadBytes")?.toLong(),
            monthCost = doc.getDouble("monthCost") ?: 0.0,
            monthCosts = monthCostsOf(doc.get("monthCosts")),
            failed = doc.getString("error") != null,
            errorDetail = doc.getString("errorDetail"),
        )
    }
}

/** consumption/{day}.monthCosts: { reads: 0.0, photos: 0.0, … }; a key unknown or not a number is left out. */
internal fun monthCostsOf(raw: Any?): Map<Consumption.Limit, Double> {
    val map = raw as? Map<*, *> ?: return emptyMap()
    return Consumption.Limit.entries.mapNotNull { limit -> (map[limit.name.lowercase()] as? Number)?.let { limit to it.toDouble() } }.toMap()
}
