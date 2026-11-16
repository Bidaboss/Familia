package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.model.Moment
import fr.familia.app.core.model.MomentCheer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/** moments/{id} and momentCheers/{momentId}-{authorId}. See docs/data-model.md for the fields and the rights. */
class FirestoreMomentRepository(private val db: FirebaseFirestore) : MomentRepository {

    private val moments = db.collection("moments")
    private val cheers = db.collection("momentCheers")

    override fun observeFor(memberId: String, withPending: Boolean): Flow<List<Moment>> = callbackFlow {
        val query = moments.whereEqualTo("forMemberId", memberId)
        val registration = (if (withPending) query else query.whereEqualTo("status", "accepted")).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull { it.toMoment() }.sortedBy { it.day })
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeCheers(momentId: String): Flow<List<MomentCheer>> = callbackFlow {
        val registration = cheers.whereEqualTo("momentId", momentId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull { it.toCheer() }.sortedBy { it.createdAt })
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun add(moment: Moment) {
        moments.document().set(
            mapOf(
                "forMemberId" to moment.forMemberId,
                "authorId" to moment.authorId,
                "authorName" to moment.authorName,
                "title" to moment.title,
                "day" to moment.day.toString(),
                "status" to if (moment.pending) "pending" else "accepted",
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun accept(momentId: String) {
        moments.document(momentId).update("status", "accepted").await()
    }

    override suspend fun remove(momentId: String) {
        moments.document(momentId).delete().await()
    }

    override suspend fun cheer(cheer: MomentCheer) {
        cheers.document(cheer.id).set(
            mapOf(
                "momentId" to cheer.momentId,
                "forMemberId" to cheer.forMemberId,
                "authorId" to cheer.authorId,
                "authorName" to cheer.authorName,
                "text" to cheer.text,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun removeCheer(cheer: MomentCheer) {
        cheers.document(cheer.id).delete().await()
    }

    private fun DocumentSnapshot.toMoment(): Moment? {
        val day = getString("day")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return Moment(
            id = id,
            forMemberId = getString("forMemberId") ?: return null,
            authorId = getString("authorId") ?: return null,
            authorName = getString("authorName").orEmpty(),
            title = getString("title") ?: return null,
            day = day,
            pending = getString("status") == "pending",
            startedAt = getTimestamp("startedAt")?.toInstant(),
        )
    }

    private fun DocumentSnapshot.toCheer(): MomentCheer? = MomentCheer(
        momentId = getString("momentId") ?: return null,
        forMemberId = getString("forMemberId") ?: return null,
        authorId = getString("authorId") ?: return null,
        authorName = getString("authorName").orEmpty(),
        text = getString("text") ?: return null,
        createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toInstant(),
    )
}
