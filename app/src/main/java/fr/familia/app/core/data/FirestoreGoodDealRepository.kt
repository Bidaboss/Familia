package fr.familia.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import fr.familia.app.core.model.GoodDeal
import fr.familia.app.core.model.GoodDealCategory
import fr.familia.app.core.model.GoodDealKind
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

/** Posts live in `goodDeals/{dealId}`. See docs/data-model.md for the fields and the rights. */
class FirestoreGoodDealRepository(
    private val db: FirebaseFirestore,
    private val journal: SendJournal? = null,
) : GoodDealRepository {

    override fun observeAll(): Flow<List<GoodDeal>> = callbackFlow {
        val registration = db.collection(GOOD_DEALS).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull { it.toGoodDeal() })
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observe(id: String): Flow<GoodDeal?> = callbackFlow {
        val registration = db.collection(GOOD_DEALS).document(id).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.takeIf { it.exists() }?.toGoodDeal())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun get(id: String): GoodDeal? =
        db.collection(GOOD_DEALS).document(id).get().await().takeIf { it.exists() }?.toGoodDeal()

    override fun newId(): String = db.collection(GOOD_DEALS).document().id

    override suspend fun save(deal: GoodDeal, isNew: Boolean) {
        val fields = mapOf(
            "kind" to deal.kind.firestoreValue,
            "title" to deal.title,
            "text" to deal.text,
            "link" to deal.link,
            "place" to deal.place,
            "phone" to deal.phone,
            "category" to deal.category?.firestoreValue,
            "endsAt" to deal.endsAt?.let { Timestamp(Date.from(it)) },
            "photo" to deal.photo,
            "authorId" to deal.authorId,
            "authorName" to deal.authorName,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val ref = db.collection(GOOD_DEALS).document(deal.id)
        if (isNew) {
            journal.track(SendEntry(ref.path, SendKind.GoodDeal, deal.title, deal.authorId)) {
                ref.set(
                    fields + mapOf(
                        "interestedIds" to emptyList<String>(),
                        "recommendedIds" to emptyList<String>(),
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
        } else {
            // An update leaves the names (interestedIds, recommendedIds) and the creation alone.
            ref.set(fields, SetOptions.merge()).await()
        }
    }

    override suspend fun delete(id: String) {
        journal?.settled(db.collection(GOOD_DEALS).document(id).path)
        db.collection(GOOD_DEALS).document(id).delete().await()
    }

    override suspend fun setInterested(id: String, memberId: String, interested: Boolean) =
        mark(id, "interestedIds", memberId, interested)

    override suspend fun setRecommended(id: String, memberId: String, recommended: Boolean) =
        mark(id, "recommendedIds", memberId, recommended)

    private suspend fun mark(id: String, field: String, memberId: String, on: Boolean) {
        val change = if (on) FieldValue.arrayUnion(memberId) else FieldValue.arrayRemove(memberId)
        db.collection(GOOD_DEALS).document(id).update(field, change).await()
    }

    private fun DocumentSnapshot.toGoodDeal(): GoodDeal? {
        val kind = GoodDealKind.fromFirestore(getString("kind")) ?: return null
        val title = getString("title") ?: return null
        return GoodDeal(
            id = id,
            kind = kind,
            title = title,
            text = getString("text").orEmpty(),
            link = getString("link"),
            place = getString("place"),
            phone = getString("phone"),
            category = GoodDealCategory.fromFirestore(getString("category")),
            endsAt = getTimestamp("endsAt")?.toDate()?.toInstant(),
            photo = getString("photo"),
            interestedIds = (get("interestedIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            recommendedIds = (get("recommendedIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            authorId = getString("authorId").orEmpty(),
            authorName = getString("authorName").orEmpty(),
            createdAt = getTimestamp("createdAt")?.toDate()?.toInstant(),
        )
    }

    private companion object {
        const val GOOD_DEALS = "goodDeals"
    }
}
