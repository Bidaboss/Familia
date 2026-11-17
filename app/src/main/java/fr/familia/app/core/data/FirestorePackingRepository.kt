package fr.familia.app.core.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.model.PackingBag
import fr.familia.app.core.model.PackingLine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestorePackingRepository(private val db: FirebaseFirestore) : PackingRepository {

    private fun bag(tripId: String, memberId: String) =
        db.collection("trips").document(tripId).collection("bags").document(memberId)

    private fun template(memberId: String) =
        db.collection("members").document(memberId).collection("settings").document("packing")

    override fun observeBag(tripId: String, memberId: String): Flow<PackingBag?> = callbackFlow {
        val registration = bag(tripId, memberId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(snapshot?.takeIf { it.exists() }?.let { PackingBag(linesFrom(it.get("items"))) })
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun saveBag(tripId: String, memberId: String, lines: List<PackingLine>, by: String) {
        bag(tripId, memberId).set(
            mapOf(
                "items" to lines.take(PackingLine.MAX_LINES).map { mapOf("label" to it.label, "done" to it.done) },
                "updatedAt" to FieldValue.serverTimestamp(),
                "updatedBy" to by,
            ),
        ).await()
    }

    override fun observeTemplate(memberId: String): Flow<List<String>?> = callbackFlow {
        val registration = template(memberId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(snapshot?.takeIf { it.exists() }?.let { (it.get("labels") as? List<*>).orEmpty().filterIsInstance<String>() })
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun saveTemplate(memberId: String, labels: List<String>) {
        template(memberId).set(mapOf("labels" to labels.take(PackingLine.MAX_LINES), "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    private fun linesFrom(raw: Any?): List<PackingLine> = (raw as? List<*>).orEmpty().mapNotNull { line ->
        val map = line as? Map<*, *> ?: return@mapNotNull null
        val label = (map["label"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        PackingLine(label.take(PackingLine.MAX_LABEL), map["done"] == true)
    }
}
