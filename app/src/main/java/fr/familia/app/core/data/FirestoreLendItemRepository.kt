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
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.Date

/** Things live in `lendItems/{itemId}`. See docs/data-model.md for the fields and the rights. */
class FirestoreLendItemRepository(private val db: FirebaseFirestore) : LendItemRepository {

    override fun observeAll(): Flow<List<LendItem>> = callbackFlow {
        val registration = db.collection(LEND_ITEMS).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else if (snapshot != null) trySend(snapshot.documents.mapNotNull { it.toLendItem() })
        }
        awaitClose { registration.remove() }
    }

    override fun observe(id: String): Flow<LendItem?> = callbackFlow {
        val registration = db.collection(LEND_ITEMS).document(id).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.takeIf { it.exists() }?.toLendItem())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun get(id: String): LendItem? =
        db.collection(LEND_ITEMS).document(id).get().await().takeIf { it.exists() }?.toLendItem()

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
            ref.set(fields + free() + mapOf("ownerId" to item.ownerId, "ownerName" to item.ownerName, "createdAt" to FieldValue.serverTimestamp())).await()
        } else {
            // An edit leaves the loan, the owner and the creation alone.
            ref.set(fields, SetOptions.merge()).await()
        }
    }

    override suspend fun delete(id: String) {
        db.collection(LEND_ITEMS).document(id).delete().await()
    }

    override suspend fun ask(id: String, me: Member, from: Instant?, until: Instant?) {
        db.collection(LEND_ITEMS).document(id).update(
            mapOf(
                "status" to LendStatus.Asked.firestoreValue,
                "borrowerId" to me.id,
                "borrowerName" to me.firstName,
                "from" to from?.let { Timestamp(Date.from(it)) },
                "until" to until?.let { Timestamp(Date.from(it)) },
                "askedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun accept(id: String) {
        db.collection(LEND_ITEMS).document(id).update(
            mapOf(
                "status" to LendStatus.Lent.firestoreValue,
                "lentAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun release(id: String) {
        db.collection(LEND_ITEMS).document(id).update(free() + mapOf("updatedAt" to FieldValue.serverTimestamp())).await()
    }

    /** Every field of the loan, as on a free thing. */
    private fun free(): Map<String, Any?> = mapOf(
        "status" to LendStatus.Available.firestoreValue,
        "borrowerId" to null,
        "borrowerName" to null,
        "from" to null,
        "until" to null,
        "askedAt" to null,
        "lentAt" to null,
        "remindedAt" to null,
    )

    private fun DocumentSnapshot.toLendItem(): LendItem? {
        val name = getString("name") ?: return null
        return LendItem(
            id = id,
            name = name,
            note = getString("note").orEmpty(),
            category = LendCategory.fromFirestore(getString("category")) ?: LendCategory.Other,
            photo = getString("photo"),
            ownerId = getString("ownerId").orEmpty(),
            ownerName = getString("ownerName").orEmpty(),
            status = LendStatus.fromFirestore(getString("status")),
            borrowerId = getString("borrowerId"),
            borrowerName = getString("borrowerName"),
            from = getTimestamp("from")?.toDate()?.toInstant(),
            until = getTimestamp("until")?.toDate()?.toInstant(),
            lentAt = getTimestamp("lentAt")?.toDate()?.toInstant(),
            createdAt = getTimestamp("createdAt")?.toDate()?.toInstant(),
        )
    }

    private companion object {
        const val LEND_ITEMS = "lendItems"
    }
}
