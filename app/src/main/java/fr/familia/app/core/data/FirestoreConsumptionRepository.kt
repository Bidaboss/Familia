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
            monthCost = doc.getDouble("monthCost") ?: 0.0,
            failed = doc.getString("error") != null,
        )
    }
}
