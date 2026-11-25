package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.NotebookBudget
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookOccasion
import fr.familia.app.core.model.NotebookSource
import fr.familia.app.core.model.notebookBudgetId
import fr.familia.app.core.model.notebookTotalBudgetId
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
        // Written only when set on a new idea, so that the rules of an older server still take it;
        // an edit removes them when dropped.
        if (isNew) {
            entry.imageUrl?.let { fields["imageUrl"] = it }
            entry.whereToFind?.let { fields["whereToFind"] = it }
            entry.photo?.let { fields["photo"] = it }
        } else {
            fields["imageUrl"] = entry.imageUrl ?: FieldValue.delete()
            fields["whereToFind"] = entry.whereToFind ?: FieldValue.delete()
            fields["photo"] = entry.photo ?: FieldValue.delete()
        }
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

    override fun observeBudgets(memberId: String): Flow<List<NotebookBudget>> = callbackFlow {
        val registration = db.collection(NOTEBOOKS).document(memberId).collection(BUDGETS).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull { it.toBudget() })
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setBudget(memberId: String, budget: NotebookBudget, amount: Double?) {
        val id = if (budget.total) notebookTotalBudgetId(budget.occasion) else notebookBudgetId(budget.personId, budget.personName, budget.occasion)
        val ref = db.collection(NOTEBOOKS).document(memberId).collection(BUDGETS).document(id)
        if (amount == null) {
            ref.delete().await()
            return
        }
        ref.set(
            mapOf(
                "personId" to budget.personId.takeUnless { budget.total },
                "personName" to budget.personName.takeUnless { budget.total },
                "occasion" to budget.occasion.occasion?.firestoreValue,
                "year" to budget.occasion.year?.toLong(),
                "amount" to amount,
                "updatedAt" to FieldValue.serverTimestamp(),
            ) + (if (budget.total) mapOf("total" to true) else emptyMap()),
        ).await()
    }

    private fun DocumentSnapshot.toBudget(): NotebookBudget? {
        val amount = getDouble("amount")?.takeIf { it > 0 } ?: return null
        return NotebookBudget(
            personId = getString("personId"),
            personName = getString("personName").orEmpty(),
            occasion = NotebookOccasion(GiftOccasion.fromFirestore(getString("occasion")), getLong("year")?.toInt()),
            amount = amount,
            total = getBoolean("total") == true,
        )
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
            movedTo = getString("movedTo"),
            imageUrl = getString("imageUrl"),
            whereToFind = getString("whereToFind")?.takeIf { it.isNotBlank() },
            photo = getString("photo"),
        )
    }

    private companion object {
        const val NOTEBOOKS = "notebooks"
        const val ENTRIES = "entries"
        const val BUDGETS = "budgets"
    }
}
