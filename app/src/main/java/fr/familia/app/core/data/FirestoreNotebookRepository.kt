package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookOccasion
import fr.familia.app.core.model.NotebookSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** The notebook lives in `notebooks/{memberId}/entries`. See docs/data-model.md. */
class FirestoreNotebookRepository(private val db: FirebaseFirestore) : NotebookRepository {

    private fun entries(memberId: String) = db.collection(NOTEBOOKS).document(memberId).collection(ENTRIES)

    override fun observeEntries(memberId: String): Flow<List<NotebookEntry>> = callbackFlow {
        val registration = entries(memberId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull { it.toEntry() })
            }
        }
        awaitClose { registration.remove() }
    }

    override fun newEntryId(memberId: String): String = entries(memberId).document().id

    override suspend fun saveIdea(memberId: String, entry: NotebookEntry, isNew: Boolean) {
        val fields = mutableMapOf<String, Any?>(
            "source" to NotebookSource.Idea.firestoreValue,
            "personId" to entry.personId,
            "personName" to entry.personName,
            "occasion" to entry.occasion.occasion?.firestoreValue,
            "year" to entry.occasion.year?.toLong(),
            "title" to entry.title,
            "url" to entry.url,
            "price" to entry.price,
            "note" to entry.note,
            "status" to entry.status.firestoreValue,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val ref = entries(memberId).document(entry.id)
        if (isNew) {
            fields["createdAt"] = FieldValue.serverTimestamp()
            ref.set(fields).await()
        } else {
            ref.update(fields).await()
        }
    }

    override suspend fun follow(memberId: String, entryId: String, status: GiftStatus, occasion: NotebookOccasion, note: String?) {
        entries(memberId).document(entryId).update(
            mapOf(
                "status" to status.firestoreValue,
                "occasion" to occasion.occasion?.firestoreValue,
                "year" to occasion.year?.toLong(),
                "note" to note,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun deleteIdea(memberId: String, entryId: String) {
        entries(memberId).document(entryId).delete().await()
    }

    private fun DocumentSnapshot.toEntry(): NotebookEntry? {
        val source = NotebookSource.fromFirestore(getString("source")) ?: return null
        val title = getString("title")?.takeIf { it.isNotBlank() } ?: return null
        return NotebookEntry(
            id = id,
            source = source,
            personId = getString("personId"),
            personName = getString("personName").orEmpty(),
            occasion = NotebookOccasion(GiftOccasion.fromFirestore(getString("occasion")), getLong("year")?.toInt()),
            title = title,
            url = getString("url"),
            price = getDouble("price"),
            note = getString("note"),
            status = GiftStatus.fromFirestore(getString("status")) ?: GiftStatus.Idea,
            listId = getString("listId"),
            itemId = getString("itemId"),
        )
    }

    private companion object {
        const val NOTEBOOKS = "notebooks"
        const val ENTRIES = "entries"
    }
}
