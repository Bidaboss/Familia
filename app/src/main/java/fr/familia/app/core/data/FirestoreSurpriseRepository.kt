package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import fr.familia.app.core.model.OpenSurprise
import fr.familia.app.core.model.Surprise
import fr.familia.app.core.model.SurpriseOccasion
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

class FirestoreSurpriseRepository(private val db: FirebaseFirestore) : SurpriseRepository {

    private val surprises get() = db.collection("surprises")
    private val open get() = db.collection("openSurprises")

    override fun observeMine(memberId: String): Flow<List<Surprise>> = callbackFlow {
        val registration = surprises.whereArrayContains("memberIds", memberId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toSurprise() }.sortedBy { it.date })
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeSurprise(id: String): Flow<Surprise?> = callbackFlow {
        val registration = surprises.document(id).addSnapshotListener { snapshot, error ->
            when {
                // Not a member any more: the rules refuse the read, the surprise is simply gone for them.
                error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> trySend(null)
                error != null -> close(error)
                else -> trySend(snapshot?.takeIf { it.exists() }?.toSurprise())
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeOpenFor(beneficiaryId: String): Flow<List<OpenSurprise>> = callbackFlow {
        val registration = open.whereEqualTo("beneficiaryId", beneficiaryId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(
                    snapshot?.documents.orEmpty().mapNotNull { doc ->
                        OpenSurprise(
                            id = doc.id,
                            beneficiaryId = doc.getString("beneficiaryId") ?: return@mapNotNull null,
                            title = doc.getString("title").orEmpty(),
                            creatorName = doc.getString("creatorName").orEmpty(),
                        )
                    },
                )
            }
        }
        awaitClose { registration.remove() }
    }

    override fun newSurpriseId(): String = surprises.document().id

    override suspend fun saveSurprise(surprise: Surprise, isNew: Boolean) {
        val fields = mutableMapOf<String, Any>(
            "beneficiaryId" to surprise.beneficiaryId,
            "title" to surprise.title,
            "occasion" to surprise.occasion.firestoreValue,
            "date" to surprise.date.toString(),
            "memberIds" to surprise.memberIds,
            "creatorId" to surprise.creatorId,
            "creatorName" to surprise.creatorName,
            "status" to "preparing",
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val batch = db.batch()
        if (isNew) {
            fields["createdAt"] = FieldValue.serverTimestamp()
            batch.set(surprises.document(surprise.id), fields)
        } else {
            batch.update(surprises.document(surprise.id), fields)
        }
        batch.set(
            open.document(surprise.id),
            mapOf(
                "beneficiaryId" to surprise.beneficiaryId,
                "title" to surprise.title,
                "creatorName" to surprise.creatorName,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.commit().await()
    }

    override suspend fun deleteSurprise(id: String) {
        db.batch().delete(surprises.document(id)).delete(open.document(id)).commit().await()
    }

    override suspend fun joinSurprise(id: String, memberId: String) {
        surprises.document(id).update(
            mapOf("memberIds" to FieldValue.arrayUnion(memberId), "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    private fun DocumentSnapshot.toSurprise(): Surprise? {
        if (getString("status") != "preparing") return null
        return Surprise(
            id = id,
            beneficiaryId = getString("beneficiaryId") ?: return null,
            title = getString("title").orEmpty(),
            occasion = SurpriseOccasion.fromFirestore(getString("occasion")),
            date = getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null,
            memberIds = (get("memberIds") as? List<*>).orEmpty().filterIsInstance<String>(),
            creatorId = getString("creatorId").orEmpty(),
            creatorName = getString("creatorName").orEmpty(),
        )
    }
}
