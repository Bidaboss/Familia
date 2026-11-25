package fr.familia.app.core.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.model.MemberCard
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** The card of each member (récit 7 bis), in `memberCards/{memberId}`. */
interface MemberCardRepository {
    /** The card of [memberId], updated live; null while nobody has filled it in. */
    fun observeCard(memberId: String): Flow<MemberCard?>

    /** Saves [card] as written by [byId]. An empty card removes the document. */
    suspend fun saveCard(card: MemberCard, byId: String)
}

class FirestoreMemberCardRepository(private val db: FirebaseFirestore) : MemberCardRepository {

    override fun observeCard(memberId: String): Flow<MemberCard?> = callbackFlow {
        val registration = db.collection(MEMBER_CARDS).document(memberId).addSnapshotListener { doc, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(
                doc?.takeIf { it.exists() }?.let {
                    MemberCard(
                        memberId = it.id,
                        shoes = it.getString("shoes").orEmpty(),
                        tastes = it.getString("tastes").orEmpty(),
                        hobbies = it.getString("hobbies").orEmpty(),
                        top = it.getString("top").orEmpty(),
                        bottom = it.getString("bottom").orEmpty(),
                        ring = it.getString("ring").orEmpty(),
                        allergies = it.getString("allergies").orEmpty(),
                        diet = it.getString("diet").orEmpty(),
                        avoid = it.getString("avoid").orEmpty(),
                        now = it.getString("now").orEmpty(),
                    )
                },
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun saveCard(card: MemberCard, byId: String) {
        val doc = db.collection(MEMBER_CARDS).document(card.memberId)
        if (card.isEmpty) {
            doc.delete().await()
            return
        }
        doc.set(
            mapOf(
                // The whole document is replaced: every field is written, blank when unknown.
                "shoes" to card.shoes,
                "tastes" to card.tastes,
                "hobbies" to card.hobbies,
                "top" to card.top,
                "bottom" to card.bottom,
                "ring" to card.ring,
                "allergies" to card.allergies,
                "diet" to card.diet,
                "avoid" to card.avoid,
                "now" to card.now,
                "updatedBy" to byId,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    companion object {
        const val MEMBER_CARDS = "memberCards"
    }
}
