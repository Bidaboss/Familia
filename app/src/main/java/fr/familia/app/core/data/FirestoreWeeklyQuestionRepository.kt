package fr.familia.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.familia.app.core.model.WeeklyAnswer
import fr.familia.app.core.model.WeeklyQuestion
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** weeklyQuestions/{lundi} and its answers/{memberId}. See docs/data-model.md for the rights. */
class FirestoreWeeklyQuestionRepository(private val db: FirebaseFirestore) : WeeklyQuestionRepository {

    private fun answers(questionId: String) = db.collection(QUESTIONS).document(questionId).collection(ANSWERS)

    override fun observeLatest(count: Int): Flow<List<WeeklyQuestion>> = callbackFlow {
        val registration = db.collection(QUESTIONS)
            .orderBy("weekId", Query.Direction.DESCENDING)
            .limit(count.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error) else if (snapshot != null) trySend(snapshot.documents.mapNotNull { it.toQuestion() })
            }
        awaitClose { registration.remove() }
    }

    override fun observeQuestion(id: String): Flow<WeeklyQuestion?> = callbackFlow {
        val registration = db.collection(QUESTIONS).document(id).addSnapshotListener { snapshot, error ->
            if (error != null) trySend(null) else if (snapshot != null) trySend(snapshot.takeIf { it.exists() }?.toQuestion())
        }
        awaitClose { registration.remove() }
    }

    override fun observeMyAnswer(questionId: String, memberId: String): Flow<WeeklyAnswer?> = callbackFlow {
        val registration = answers(questionId).document(memberId).addSnapshotListener { snapshot, error ->
            if (error != null) trySend(null) else if (snapshot != null) trySend(snapshot.takeIf { it.exists() }?.toAnswer())
        }
        awaitClose { registration.remove() }
    }

    override fun observeAnswers(questionId: String): Flow<List<WeeklyAnswer>> = callbackFlow {
        val registration = answers(questionId).orderBy("updatedAt").addSnapshotListener { snapshot, error ->
            // Refused before one answers and before revealAt: show nothing rather than fail.
            if (error != null) trySend(emptyList()) else if (snapshot != null) trySend(snapshot.documents.mapNotNull { it.toAnswer() })
        }
        awaitClose { registration.remove() }
    }

    override suspend fun answer(questionId: String, memberId: String, memberName: String, text: String?, photoId: String?) {
        answers(questionId).document(memberId).set(
            mapOf(
                "memberId" to memberId,
                "memberName" to memberName,
                "text" to text,
                "skipped" to (text == null),
                "photoId" to photoId?.takeIf { text != null },
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override fun newPhotoId(): String = db.collection(QUESTIONS).document().id

    override suspend fun removeAnswer(questionId: String, memberId: String) {
        answers(questionId).document(memberId).delete().await()
    }

    private fun DocumentSnapshot.toQuestion(): WeeklyQuestion? {
        val text = getString("text") ?: return null
        val publishedAt = getTimestamp("publishedAt")?.toInstant() ?: return null
        val revealAt = getTimestamp("revealAt")?.toInstant() ?: return null
        return WeeklyQuestion(
            id = id,
            text = text,
            proposedByName = getString("proposedByName"),
            publishedAt = publishedAt,
            revealAt = revealAt,
            answerCount = getLong("answerCount")?.toInt() ?: 0,
        )
    }

    private fun DocumentSnapshot.toAnswer(): WeeklyAnswer? {
        val memberId = getString("memberId") ?: return null
        return WeeklyAnswer(
            memberId = memberId,
            memberName = getString("memberName").orEmpty(),
            text = getString("text"),
            skipped = getBoolean("skipped") == true,
            // Null while the server time of a fresh write is pending.
            updatedAt = (get("updatedAt") as? Timestamp)?.toInstant(),
            photoId = getString("photoId"),
        )
    }

    private companion object {
        const val QUESTIONS = "weeklyQuestions"
        const val ANSWERS = "answers"
    }
}
