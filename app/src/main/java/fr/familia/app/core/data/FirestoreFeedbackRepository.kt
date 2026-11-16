package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.familia.app.core.model.Feedback
import fr.familia.app.core.model.FeedbackContext
import fr.familia.app.core.model.FeedbackOrigin
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.feedbackOrder
import fr.familia.app.core.photo.PhotoStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreFeedbackRepository(private val db: FirebaseFirestore, private val photos: PhotoStorage?) : FeedbackRepository {

    private val all = db.collection("feedback")

    override suspend fun send(kind: Feedback.Kind, text: String, me: Member, context: FeedbackContext, screenshot: ByteArray?) {
        val doc = all.document()
        val shotPath = screenshot?.let { bytes ->
            val storage = photos ?: error("No Storage")
            Feedback.screenshotPath(me.id, doc.id).also { storage.upload(it, bytes) }
        }
        val fields = mutableMapOf<String, Any?>(
            "kind" to kind.key,
            "text" to text,
            "authorId" to me.id,
            "authorName" to me.firstName,
            "appVersion" to context.appVersion.take(40),
            "android" to context.android.take(20),
            "device" to context.device.take(80),
            "screen" to context.screen.key,
            "createdAt" to FieldValue.serverTimestamp(),
            "status" to Feedback.Status.New.key,
            "note" to null,
            "statusAt" to null,
        )
        if (shotPath != null) fields["screenshot"] = shotPath
        doc.set(fields).await()
    }

    override fun observeAll(): Flow<List<Feedback>> = observe(all)

    override fun observeMine(memberId: String): Flow<List<Feedback>> = observe(all.whereEqualTo("authorId", memberId))

    private fun observe(query: Query): Flow<List<Feedback>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(feedbackOrder(snapshot?.documents.orEmpty().mapNotNull(::toFeedback)))
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun setStatus(id: String, status: Feedback.Status, note: String?) {
        all.document(id).update(
            mapOf(
                "status" to status.key,
                "note" to note?.trim()?.takeIf { it.isNotEmpty() }?.take(Feedback.MAX_NOTE),
                "statusAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun delete(id: String, screenshot: String?) {
        if (screenshot != null) {
            try {
                photos?.delete(screenshot)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Already gone, or offline: the request still goes.
            }
        }
        all.document(id).delete().await()
    }

    private fun toFeedback(doc: DocumentSnapshot): Feedback? {
        val kind = Feedback.Kind.fromKey(doc.getString("kind")) ?: return null
        return Feedback(
            id = doc.id,
            kind = kind,
            text = doc.getString("text").orEmpty(),
            authorId = doc.getString("authorId").orEmpty(),
            authorName = doc.getString("authorName").orEmpty(),
            context = FeedbackContext(
                appVersion = doc.getString("appVersion").orEmpty(),
                android = doc.getString("android").orEmpty(),
                device = doc.getString("device").orEmpty(),
                screen = FeedbackOrigin.fromKey(doc.getString("screen")),
            ),
            createdAt = doc.getTimestamp("createdAt")?.toInstant(),
            status = Feedback.Status.fromKey(doc.getString("status")),
            note = doc.getString("note"),
            statusAt = doc.getTimestamp("statusAt")?.toInstant(),
            screenshot = doc.getString("screenshot"),
        )
    }
}
