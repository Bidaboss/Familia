package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.model.BirthdayCard
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** birthdayCards/{year}-{forMemberId}-{authorId}. See docs/data-model.md for the fields and the rights. */
class FirestoreBirthdayCardRepository(private val db: FirebaseFirestore) : BirthdayCardRepository {

    private val cards = db.collection("birthdayCards")

    override fun observeFor(memberId: String, year: Int): Flow<List<BirthdayCard>> = callbackFlow {
        val registration = cards.whereEqualTo("forMemberId", memberId).whereEqualTo("year", year)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toCard() }.sortedBy { it.createdAt })
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun save(card: BirthdayCard, isNew: Boolean) {
        val ref = cards.document(card.id)
        if (isNew) {
            ref.set(
                mapOf(
                    "forMemberId" to card.forMemberId,
                    "authorId" to card.authorId,
                    "authorName" to card.authorName,
                    "text" to card.text,
                    "year" to card.year,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } else {
            ref.update(mapOf("text" to card.text, "updatedAt" to FieldValue.serverTimestamp())).await()
        }
    }

    override suspend fun remove(card: BirthdayCard) {
        cards.document(card.id).delete().await()
    }

    private fun DocumentSnapshot.toCard(): BirthdayCard? {
        val forMemberId = getString("forMemberId") ?: return null
        val authorId = getString("authorId") ?: return null
        val text = getString("text") ?: return null
        val year = getLong("year")?.toInt() ?: return null
        return BirthdayCard(forMemberId, authorId, getString("authorName").orEmpty(), text, year, getTimestamp("createdAt")?.toInstant())
    }
}
