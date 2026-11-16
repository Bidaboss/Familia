package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import fr.familia.app.core.model.ClaimType
import fr.familia.app.core.model.GiftClaim
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftShare
import fr.familia.app.core.model.ReceivedGift
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/** Gift lists live in `giftLists/{listId}` and its subcollections. See docs/data-model.md. */
class FirestoreGiftRepository(private val db: FirebaseFirestore) : GiftRepository {

    override fun observeVisibleLists(memberId: String): Flow<List<GiftList>> {
        val lists = db.collection(LISTS)
        // Four queries the rules can prove, merged: Firestore has no OR across these fields. The ideas
        // of the family (récit 9 ter) are read for everyone but oneself (index kind + beneficiaryId).
        return combine(
            lists.whereArrayContains("viewerIds", memberId).observe { it.toGiftList() },
            lists.whereEqualTo("kind", GiftListKind.Wishes.firestoreValue).observe { it.toGiftList() },
            lists.whereEqualTo("beneficiaryId", memberId).whereEqualTo("revealed", true).observe { it.toGiftList() },
            lists.whereEqualTo("kind", GiftListKind.Ideas.firestoreValue).whereNotEqualTo("beneficiaryId", memberId).observe { it.toGiftList() },
        ) { seen, wishes, revealed, ideas -> (seen + wishes + revealed + ideas).distinctBy { it.id } }
    }

    override fun observeList(listId: String): Flow<GiftList?> = callbackFlow {
        val registration = db.collection(LISTS).document(listId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                // A list that is not visible (or deleted with its rights) reads as missing.
                trySend(null)
            } else if (snapshot != null) {
                trySend(snapshot.takeIf { it.exists() }?.toGiftList())
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeItems(listId: String, hiddenFromMe: Boolean): Flow<List<GiftItem>> {
        val items: Query = db.collection(LISTS).document(listId).collection(ITEMS)
        val query = if (hiddenFromMe) items.whereEqualTo("visibleToBeneficiary", true) else items
        return query.observe { it.toGiftItem() }
    }

    override fun observeClaims(listId: String): Flow<List<GiftClaim>> =
        db.collection(LISTS).document(listId).collection(CLAIMS).observe { it.toGiftClaim() }

    override fun observeShares(
        listId: String,
        itemIds: List<String>,
        memberId: String,
        allOf: Set<String>,
    ): Flow<Map<String, List<GiftShare>>> {
        if (itemIds.isEmpty()) return flowOf(emptyMap())
        val claims = db.collection(LISTS).document(listId).collection(CLAIMS)
        val flows = itemIds.map { itemId ->
            val shares = claims.document(itemId).collection(SHARES)
            val parts = if (itemId in allOf) shares.observe { it.toGiftShare() } else shares.document(memberId).observeOne()
            parts.retryRefused()
        }
        return combine(flows) { parts -> itemIds.zip(parts.toList()).toMap() }
    }

    override fun newListId(): String = db.collection(LISTS).document().id

    override fun newItemId(listId: String): String =
        db.collection(LISTS).document(listId).collection(ITEMS).document().id

    override suspend fun saveList(list: GiftList, isNew: Boolean) {
        val ref = db.collection(LISTS).document(list.id)
        val fields = mapOf(
            "title" to list.title,
            "occasion" to list.occasion?.firestoreValue,
            "date" to list.date?.toString(),
            "viewerIds" to list.viewerIds,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isNew) {
            ref.set(
                fields + mapOf(
                    "kind" to list.kind.firestoreValue,
                    "beneficiaryId" to list.beneficiaryId,
                    "creatorId" to list.creatorId,
                    "creatorName" to list.creatorName,
                    "revealed" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } else {
            ref.update(fields).await()
        }
    }

    override suspend fun deleteList(listId: String) {
        db.collection(LISTS).document(listId).delete().await()
    }

    override suspend fun saveItem(listId: String, item: GiftItem, isNew: Boolean) {
        val ref = db.collection(LISTS).document(listId).collection(ITEMS).document(item.id)
        val fields = mapOf(
            "title" to item.title,
            "url" to item.url,
            "price" to item.price,
            "note" to item.note,
            "imageUrl" to item.imageUrl,
            "occasion" to item.occasion?.takeIf { it == GiftOccasion.Christmas || it == GiftOccasion.Birthday }?.firestoreValue,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isNew) {
            ref.set(
                fields + mapOf(
                    "authorId" to item.authorId,
                    "authorName" to item.authorName,
                    "visibleToBeneficiary" to item.visibleToBeneficiary,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } else {
            ref.update(fields).await()
        }
    }

    override suspend fun deleteItem(listId: String, itemId: String) {
        db.collection(LISTS).document(listId).collection(ITEMS).document(itemId).delete().await()
    }

    override suspend fun claim(listId: String, claim: GiftClaim) {
        db.collection(LISTS).document(listId).collection(CLAIMS).document(claim.itemId).set(
            mapOf(
                "type" to claim.type.firestoreValue,
                "holderId" to claim.holderId,
                "holderName" to claim.holderName,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun release(listId: String, itemId: String) {
        db.collection(LISTS).document(listId).collection(CLAIMS).document(itemId).delete().await()
    }

    override suspend fun setShare(listId: String, itemId: String, share: GiftShare, isNew: Boolean) {
        val ref = shareRef(listId, itemId, share.memberId)
        if (isNew) {
            ref.set(
                mapOf(
                    "amount" to share.amount,
                    "memberName" to share.memberName,
                    "received" to false,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } else {
            ref.update(mapOf("amount" to share.amount, "updatedAt" to FieldValue.serverTimestamp())).await()
        }
    }

    override suspend fun markShareReceived(listId: String, itemId: String, memberId: String, received: Boolean) {
        shareRef(listId, itemId, memberId)
            .update(mapOf("received" to received, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    override suspend fun leaveShare(listId: String, itemId: String, memberId: String) {
        shareRef(listId, itemId, memberId).delete().await()
    }

    override suspend fun setClaimOccasion(listId: String, itemId: String, occasion: GiftOccasion) {
        db.collection(LISTS).document(listId).collection(CLAIMS).document(itemId)
            .update("occasion", occasion.firestoreValue)
            .await()
    }

    override fun observeReceived(listId: String): Flow<List<ReceivedGift>> =
        db.collection(LISTS).document(listId).collection(RECEIVED).observe { it.toReceivedGift() }

    override suspend fun thank(listId: String, itemId: String) {
        db.collection(LISTS).document(listId).collection(RECEIVED).document(itemId)
            .update(mapOf("thanked" to true, "thankedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    private fun shareRef(listId: String, itemId: String, memberId: String) =
        db.collection(LISTS).document(listId).collection(CLAIMS).document(itemId).collection(SHARES).document(memberId)

    /**
     * The rules read the reservation to let the responsible read every part. Right after « Cadeau
     * commun », the listener can reach the server before the reservation does, and is refused. The
     * parts then read empty at once, so the whole list stays on screen, and the listener is tried
     * again, less and less often, for about two minutes. Any other error ends the parts, empty.
     */
    private fun Flow<List<GiftShare>>.retryRefused(): Flow<List<GiftShare>> =
        retryWhen { cause, attempt ->
            val refused = cause is FirebaseFirestoreException &&
                cause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            if (!refused || attempt >= SHARE_RETRIES) return@retryWhen false
            if (attempt == 0L) emit(emptyList())
            delay(minOf(SHARE_RETRY_DELAY_MILLIS * (attempt + 1), SHARE_RETRY_MAX_DELAY_MILLIS))
            true
        }.catch { e ->
            if (e is CancellationException) throw e
            emit(emptyList())
        }

    /** One live document as a list of zero or one item: a part that does not exist yet is empty. */
    private fun DocumentReference.observeOne(): Flow<List<GiftShare>> = callbackFlow {
        val registration = addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(listOfNotNull(snapshot.takeIf { it.exists() }?.toGiftShare()))
            }
        }
        awaitClose { registration.remove() }
    }

    /** Live documents of a query; malformed ones are skipped rather than crash the screen. */
    private fun <T> Query.observe(map: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
        val registration = addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull(map))
            }
        }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toGiftList(): GiftList? {
        val kind = GiftListKind.fromFirestore(getString("kind")) ?: return null
        @Suppress("UNCHECKED_CAST")
        val viewers = (get("viewerIds") as? List<Any?>)?.filterIsInstance<String>() ?: emptyList()
        return GiftList(
            id = id,
            kind = kind,
            title = getString("title").orEmpty(),
            occasion = GiftOccasion.fromFirestore(getString("occasion")),
            date = getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            beneficiaryId = getString("beneficiaryId") ?: return null,
            viewerIds = viewers,
            creatorId = getString("creatorId").orEmpty(),
            creatorName = getString("creatorName").orEmpty(),
            revealed = getBoolean("revealed") == true,
        )
    }

    private fun DocumentSnapshot.toGiftItem(): GiftItem? {
        return GiftItem(
            id = id,
            title = getString("title") ?: return null,
            url = getString("url"),
            price = getDouble("price"),
            note = getString("note"),
            authorId = getString("authorId").orEmpty(),
            authorName = getString("authorName").orEmpty(),
            visibleToBeneficiary = getBoolean("visibleToBeneficiary") == true,
            imageUrl = getString("imageUrl"),
            occasion = GiftOccasion.fromFirestore(getString("occasion")),
        )
    }

    private fun DocumentSnapshot.toGiftClaim(): GiftClaim? {
        return GiftClaim(
            itemId = id,
            type = ClaimType.fromFirestore(getString("type")) ?: return null,
            holderId = getString("holderId") ?: return null,
            holderName = getString("holderName").orEmpty(),
            sharesTotal = getDouble("sharesTotal") ?: 0.0,
            sharesCount = getLong("sharesCount")?.toInt() ?: 0,
            occasion = GiftOccasion.fromFirestore(getString("occasion")),
            createdOn = getTimestamp("createdAt")?.toDate()?.toInstant()?.atZone(java.time.ZoneId.of("Europe/Paris"))?.toLocalDate(),
        )
    }

    private fun DocumentSnapshot.toGiftShare(): GiftShare? {
        return GiftShare(
            memberId = id,
            memberName = getString("memberName").orEmpty(),
            amount = getDouble("amount") ?: return null,
            received = getBoolean("received") == true,
        )
    }

    private fun DocumentSnapshot.toReceivedGift(): ReceivedGift? {
        @Suppress("UNCHECKED_CAST")
        val givers = (get("givers") as? List<Any?>)?.filterIsInstance<String>() ?: emptyList()
        return ReceivedGift(
            id = id,
            title = getString("title") ?: return null,
            url = getString("url"),
            imageUrl = getString("imageUrl"),
            givers = givers,
            joint = getBoolean("joint") == true,
            occasion = GiftOccasion.fromFirestore(getString("occasion")),
            year = getLong("year")?.toInt(),
            thanked = getBoolean("thanked") == true,
        )
    }

    private companion object {
        const val LISTS = "giftLists"
        const val ITEMS = "items"
        const val CLAIMS = "claims"
        const val SHARES = "shares"
        const val RECEIVED = "received"
        const val SHARE_RETRIES = 20L
        const val SHARE_RETRY_DELAY_MILLIS = 750L
        const val SHARE_RETRY_MAX_DELAY_MILLIS = 10_000L
    }
}
