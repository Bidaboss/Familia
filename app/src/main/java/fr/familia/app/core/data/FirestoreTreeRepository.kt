package fr.familia.app.core.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.model.FamilyTree
import fr.familia.app.core.model.MonthChallenge
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.YearMonth

/** familyTree/current, challenges/{AAAA-MM} and challengePicks/{AAAA-MM}. See docs/data-model.md. */
class FirestoreTreeRepository(private val db: FirebaseFirestore) : TreeRepository {

    override fun observeTree(): Flow<FamilyTree> = callbackFlow {
        val registration = db.collection("familyTree").document("current").addSnapshotListener { snapshot, _ ->
            trySend(
                FamilyTree(
                    leaves = snapshot?.getLong("leaves")?.toInt() ?: 0,
                    bloomedMonths = (snapshot?.get("bloomedMonths") as? List<*>)?.filterIsInstance<String>().orEmpty(),
                ),
            )
        }
        awaitClose { registration.remove() }
    }

    override fun observeChallenge(month: YearMonth): Flow<MonthChallenge?> = callbackFlow {
        val registration = db.collection("challenges").document(month.toString()).addSnapshotListener { snapshot, _ ->
            val title = snapshot?.getString("title")
            val target = snapshot?.getLong("target")?.toInt()
            trySend(
                if (title == null || target == null) {
                    null
                } else {
                    MonthChallenge(
                        month = month,
                        title = title,
                        emoji = snapshot.getString("emoji").orEmpty(),
                        target = target,
                        progress = snapshot.getLong("progress")?.toInt() ?: 0,
                        achieved = snapshot.get("achievedAt") != null,
                        choiceId = snapshot.getString("choiceId"),
                    )
                },
            )
        }
        awaitClose { registration.remove() }
    }

    override fun observePick(month: YearMonth): Flow<String?> = callbackFlow {
        val registration = db.collection("challengePicks").document(month.toString()).addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.getString("choiceId"))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun pick(month: YearMonth, choiceId: String) {
        db.collection("challengePicks").document(month.toString())
            .set(mapOf("choiceId" to choiceId, "pickedAt" to FieldValue.serverTimestamp()))
            .await()
    }
}
