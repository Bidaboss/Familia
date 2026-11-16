package fr.familia.app.core.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.model.ChallengeRotation
import fr.familia.app.core.model.FamilyTree
import fr.familia.app.core.model.HostedChallenge
import fr.familia.app.core.model.HostedIllustration
import fr.familia.app.core.model.HostedKind
import fr.familia.app.core.model.HostedStatus
import fr.familia.app.core.model.MonthChallenge
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.YearMonth

/**
 * familyTree/current, challenges/{AAAA-MM}, challengePicks/{AAAA-MM}, challengeRotation/current and
 * householdChallenges/{AAAA-MM}. See docs/data-model.md.
 */
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
                        hostHouseholdId = snapshot.getString("householdId")?.takeIf { snapshot.getString("measure") == "linked" },
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

    override fun observeRotation(): Flow<ChallengeRotation?> = callbackFlow {
        val registration = db.collection("challengeRotation").document("current").addSnapshotListener { snapshot, _ ->
            val ids = (snapshot?.get("householdIds") as? List<*>)?.filterIsInstance<String>()
            val start = snapshot?.getString("startMonth")?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
            trySend(if (ids.isNullOrEmpty() || start == null) null else ChallengeRotation(ids, start))
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setRotation(rotation: ChallengeRotation) {
        db.collection("challengeRotation").document("current")
            .set(
                mapOf(
                    "householdIds" to rotation.householdIds,
                    "startMonth" to rotation.startMonth.toString(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    override fun observeHosted(month: YearMonth): Flow<HostedChallenge?> = callbackFlow {
        val registration = db.collection("householdChallenges").document(month.toString()).addSnapshotListener { snapshot, _ ->
            val status = HostedStatus.of(snapshot?.getString("status"))
            val householdId = snapshot?.getString("householdId")
            trySend(
                if (status == null || householdId == null) {
                    null
                } else {
                    HostedChallenge(
                        month = month,
                        householdId = householdId,
                        status = status,
                        kind = HostedKind.of(snapshot.getString("kind")) ?: HostedKind.Photo,
                        title = snapshot.getString("title").orEmpty(),
                        emoji = snapshot.getString("emoji").orEmpty(),
                        illustration = HostedIllustration.of(snapshot.getString("illustration")) ?: HostedIllustration.Sun,
                        target = snapshot.getLong("target")?.toInt() ?: HostedChallenge.TARGET_MIN,
                    )
                },
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun saveHosted(challenge: HostedChallenge, memberId: String) {
        db.collection("householdChallenges").document(challenge.month.toString())
            .set(
                mapOf(
                    "householdId" to challenge.householdId,
                    "status" to challenge.status.key,
                    "kind" to challenge.kind.key,
                    "title" to challenge.title.trim(),
                    "emoji" to challenge.emoji,
                    "illustration" to challenge.illustration.key,
                    "target" to challenge.target,
                    "updatedBy" to memberId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }
}
