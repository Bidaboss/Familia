package fr.familia.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * Reads the collections of docs/data-model.md from the server only: the offline cache may hold
 * part of the data, and a backup must be complete or not be made. Left out on purpose: memberEmails (rebuilt from the members), and each member's
 * phones and settings, which only they can read.
 */
class FirestoreExportRepository(private val db: FirebaseFirestore) : ExportRepository {

    override suspend fun loadFamilyData(): FamilyData = try {
        load()
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) throw ExportOfflineException(e) else throw e
    }

    private suspend fun load(): FamilyData = coroutineScope {
        val households = async { db.collection("households").get(Source.SERVER).await().documents.map(::toExport) }
        val members = async { db.collection("members").get(Source.SERVER).await().documents.map(::toExport) }
        val events = async { db.collection("events").get(Source.SERVER).await().documents.map(::toExport) }
        val answers = async {
            db.collectionGroup("rsvps").get(Source.SERVER).await().documents.mapNotNull { doc ->
                val eventId = doc.reference.parent.parent?.id ?: return@mapNotNull null
                ExportAnswer(eventId, doc.id, plain(doc.data.orEmpty()))
            }
        }
        FamilyData(households.await(), members.await(), events.await(), answers.await())
    }

    private fun toExport(doc: DocumentSnapshot) = ExportDocument(doc.id, plain(doc.data.orEmpty()))

    private fun plain(fields: Map<String, Any?>): Map<String, Any?> = fields.mapValues { (_, v) -> plainValue(v) }

    private fun plainValue(value: Any?): Any? = when (value) {
        is Timestamp -> value.toInstant()
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to plainValue(v) }
        is List<*> -> value.map(::plainValue)
        else -> value
    }
}
