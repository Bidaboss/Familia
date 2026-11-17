package fr.familia.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Turn
import fr.familia.app.core.model.TurnDone
import fr.familia.app.core.model.TurnSwap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.Date

/** Rotations live in `turns/{turnId}`. See docs/data-model.md for the fields and the rights. */
class FirestoreTurnRepository(private val db: FirebaseFirestore) : TurnRepository {

    override fun observeAll(): Flow<List<Turn>> = callbackFlow {
        val registration = db.collection(TURNS).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else if (snapshot != null) trySend(snapshot.documents.mapNotNull { it.toTurn() })
        }
        awaitClose { registration.remove() }
    }

    override fun observe(id: String): Flow<Turn?> = callbackFlow {
        val registration = db.collection(TURNS).document(id).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.takeIf { it.exists() }?.toTurn())
        }
        awaitClose { registration.remove() }
    }

    override fun newId(): String = db.collection(TURNS).document().id

    override suspend fun create(id: String, title: String, householdIds: List<String>, me: Member) {
        db.collection(TURNS).document(id).set(
            mapOf(
                "title" to title,
                "householdIds" to householdIds,
                "current" to 0,
                "history" to emptyList<Any>(),
                "swap" to null,
                "createdBy" to me.id,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun edit(id: String, title: String, householdIds: List<String>, current: Int) {
        db.collection(TURNS).document(id).update(
            mapOf(
                "title" to title,
                "householdIds" to householdIds,
                "current" to current,
                "swap" to null,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun done(turn: Turn, label: String) {
        val next = turn.done(label, Instant.now())
        val line = next.history.last()
        db.collection(TURNS).document(turn.id).update(
            mapOf(
                // No server time inside a list: the phone's clock, which the rules check within 10 minutes.
                "history" to FieldValue.arrayUnion(
                    mapOf("householdId" to line.householdId, "label" to line.label, "at" to Timestamp(Date.from(line.at))),
                ),
                "current" to next.current,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun skip(turn: Turn) {
        db.collection(TURNS).document(turn.id).update(
            mapOf("householdIds" to turn.skipped().householdIds, "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    override suspend fun askSwap(turn: Turn, me: Member, toHouseholdId: String) {
        val swap = turn.swapAsk(me, toHouseholdId) ?: error("No exchange possible")
        db.collection(TURNS).document(turn.id).update(
            mapOf(
                "swap" to mapOf(
                    "fromId" to swap.fromId, "toId" to swap.toId, "fromIndex" to swap.fromIndex,
                    "toIndex" to swap.toIndex, "byId" to swap.byId, "byName" to swap.byName,
                ),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun acceptSwap(turn: Turn) {
        db.collection(TURNS).document(turn.id).update(
            mapOf("householdIds" to turn.swapAccepted().householdIds, "swap" to null, "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    override suspend fun dropSwap(id: String) {
        db.collection(TURNS).document(id).update(mapOf("swap" to null, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    override suspend fun delete(id: String) {
        db.collection(TURNS).document(id).delete().await()
    }

    private companion object {
        const val TURNS = "turns"
    }
}

/** A rotation as stored; null when a field the app needs is missing. */
internal fun DocumentSnapshot.toTurn(): Turn? {
    val title = getString("title") ?: return null
    val households = (get("householdIds") as? List<*>)?.filterIsInstance<String>() ?: return null
    val history = (get("history") as? List<*>).orEmpty().mapNotNull { line ->
        val m = line as? Map<*, *> ?: return@mapNotNull null
        val household = m["householdId"] as? String ?: return@mapNotNull null
        val at = (m["at"] as? Timestamp)?.toDate()?.toInstant() ?: Instant.EPOCH
        TurnDone(household, m["label"] as? String ?: "", at)
    }
    val swap = (get("swap") as? Map<*, *>)?.let { s ->
        TurnSwap(
            fromId = s["fromId"] as? String ?: return@let null,
            toId = s["toId"] as? String ?: return@let null,
            fromIndex = (s["fromIndex"] as? Number)?.toInt() ?: return@let null,
            toIndex = (s["toIndex"] as? Number)?.toInt() ?: return@let null,
            byId = s["byId"] as? String ?: "",
            byName = s["byName"] as? String ?: "",
        )
    }
    return Turn(
        id = id,
        title = title,
        householdIds = households,
        current = getLong("current")?.toInt()?.coerceIn(0, (households.size - 1).coerceAtLeast(0)) ?: 0,
        history = history,
        swap = swap,
        createdBy = getString("createdBy") ?: "",
    )
}
