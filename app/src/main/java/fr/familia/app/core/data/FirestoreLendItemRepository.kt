package fr.familia.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import fr.familia.app.core.model.LendCategory
import fr.familia.app.core.model.LendItem
import fr.familia.app.core.model.LendStatus
import fr.familia.app.core.model.Member
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.Date

/**
 * Things live in `lendItems/{itemId}`, read by the whole family, with only `lent`; who asked or has
 * one, and the dates, live in `lendLoans/{itemId}`, read by the owner and the borrower (EXPERT-9).
 * The two always change together, in one batch. See docs/data-model.md for the fields and the rights.
 */
class FirestoreLendItemRepository(
    private val db: FirebaseFirestore,
    private val journal: SendJournal? = null,
) : LendItemRepository {

    private fun things(): Flow<List<DocumentSnapshot>> = callbackFlow {
        val registration = db.collection(LEND_ITEMS).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else if (snapshot != null) trySend(snapshot.documents)
        }
        awaitClose { registration.remove() }
    }

    private fun thing(id: String): Flow<DocumentSnapshot?> = callbackFlow {
        val registration = db.collection(LEND_ITEMS).document(id).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.takeIf { it.exists() })
        }
        awaitClose { registration.remove() }
    }

    /** The asks and loans [readerId] is part of, by thing: the only ones the rules let them read. */
    private fun loansOf(readerId: String): Flow<Map<String, DocumentSnapshot>> = callbackFlow {
        val registration = db.collection(LEND_LOANS).whereArrayContains("parties", readerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error) else if (snapshot != null) trySend(snapshot.documents.associateBy { it.id })
            }
        awaitClose { registration.remove() }
    }

    override fun observeAll(readerId: String): Flow<List<LendItem>> =
        combine(things(), loansOf(readerId)) { docs, loans -> docs.mapNotNull { it.toLendItem(loans[it.id]) } }

    override fun observe(id: String, readerId: String): Flow<LendItem?> =
        combine(thing(id), loansOf(readerId)) { doc, loans -> doc?.toLendItem(loans[id]) }

    override suspend fun get(id: String): LendItem? =
        db.collection(LEND_ITEMS).document(id).get().await().takeIf { it.exists() }?.toLendItem(null)

    override fun newId(): String = db.collection(LEND_ITEMS).document().id

    override suspend fun save(item: LendItem, isNew: Boolean) {
        val fields = mapOf(
            "name" to item.name,
            "note" to item.note,
            "category" to item.category.firestoreValue,
            "photo" to item.photo,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val ref = db.collection(LEND_ITEMS).document(item.id)
        if (isNew) {
            journal.track(SendEntry(ref.path, SendKind.LendItem, item.name, item.ownerId)) {
                ref.set(
                    fields + mapOf("lent" to false, "ownerId" to item.ownerId, "ownerName" to item.ownerName, "createdAt" to FieldValue.serverTimestamp()),
                )
            }
        } else {
            // An edit leaves the loan, the owner and the creation alone.
            ref.set(fields, SetOptions.merge()).await()
        }
    }

    /** The server removes its loan too (onLendItemDeleted). */
    override suspend fun delete(id: String) {
        journal?.settled(db.collection(LEND_ITEMS).document(id).path)
        db.collection(LEND_ITEMS).document(id).delete().await()
    }

    override suspend fun ask(id: String, ownerId: String, me: Member, from: Instant?, until: Instant?) {
        val batch = db.batch()
        batch.set(
            db.collection(LEND_LOANS).document(id),
            mapOf(
                "ownerId" to ownerId,
                "parties" to listOf(ownerId, me.id),
                "status" to LendStatus.Asked.firestoreValue,
                "borrowerId" to me.id,
                "borrowerName" to me.firstName,
                "from" to from?.let { Timestamp(Date.from(it)) },
                "until" to until?.let { Timestamp(Date.from(it)) },
                "askedAt" to FieldValue.serverTimestamp(),
                "lentAt" to null,
                "remindedAt" to null,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.update(db.collection(LEND_ITEMS).document(id), mapOf("lent" to true, "updatedAt" to FieldValue.serverTimestamp()))
        batch.commit().await()
    }

    override suspend fun accept(id: String) {
        db.collection(LEND_LOANS).document(id).update(
            mapOf(
                "status" to LendStatus.Lent.firestoreValue,
                "lentAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun release(id: String) {
        val batch = db.batch()
        batch.delete(db.collection(LEND_LOANS).document(id))
        batch.update(db.collection(LEND_ITEMS).document(id), mapOf("lent" to false, "updatedAt" to FieldValue.serverTimestamp()))
        batch.commit().await()
    }

    /** The thing, with [loan] when the reader may read it; otherwise only lent or free. */
    private fun DocumentSnapshot.toLendItem(loan: DocumentSnapshot?): LendItem? {
        val name = getString("name") ?: return null
        val item = LendItem(
            id = id,
            name = name,
            note = getString("note").orEmpty(),
            category = LendCategory.fromFirestore(getString("category")) ?: LendCategory.Other,
            photo = getString("photo"),
            ownerId = getString("ownerId").orEmpty(),
            ownerName = getString("ownerName").orEmpty(),
            status = if (getBoolean("lent") == true) LendStatus.Lent else LendStatus.Available,
            createdAt = getTimestamp("createdAt")?.toDate()?.toInstant(),
        )
        if (loan == null || getBoolean("lent") != true) return item
        return item.copy(
            status = LendStatus.fromFirestore(loan.getString("status")),
            borrowerId = loan.getString("borrowerId"),
            borrowerName = loan.getString("borrowerName"),
            from = loan.getTimestamp("from")?.toDate()?.toInstant(),
            until = loan.getTimestamp("until")?.toDate()?.toInstant(),
            lentAt = loan.getTimestamp("lentAt")?.toDate()?.toInstant(),
        )
    }

    private companion object {
        const val LEND_ITEMS = "lendItems"
        const val LEND_LOANS = "lendLoans"
    }
}
