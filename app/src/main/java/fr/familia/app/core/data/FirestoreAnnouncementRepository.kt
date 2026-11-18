package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.familia.app.core.model.Announcement
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.announcementOrder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreAnnouncementRepository(private val db: FirebaseFirestore) : AnnouncementRepository {

    private val all = db.collection("announcements")

    override suspend fun send(title: String, text: String, urgent: Boolean, recipientIds: List<String>, me: Member) {
        // Exactly the keys of firestore.rules: the server adds `delivery`.
        all.document().set(
            mapOf(
                "title" to title.trim().take(Announcement.MAX_TITLE),
                "text" to text.trim().take(Announcement.MAX_TEXT),
                "urgent" to urgent,
                "recipientIds" to recipientIds.distinct(),
                "authorId" to me.id,
                "authorName" to me.firstName,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override fun observeSent(): Flow<List<Announcement>> = callbackFlow {
        val registration = all.orderBy("createdAt", Query.Direction.DESCENDING).limit(Announcement.HISTORY)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
                    trySend(announcementOrder(snapshot?.documents.orEmpty().mapNotNull(::toAnnouncement)))
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observe(id: String): Flow<Announcement?> = callbackFlow {
        val registration = all.document(id).addSnapshotListener { snapshot, error ->
            // Refused by the rules (not for me) reads as gone, not as a crash.
            trySend(if (error != null) null else snapshot?.let(::toAnnouncement))
        }
        awaitClose { registration.remove() }
    }

    private fun toAnnouncement(doc: DocumentSnapshot): Announcement? {
        if (!doc.exists()) return null
        @Suppress("UNCHECKED_CAST")
        return Announcement(
            id = doc.id,
            title = doc.getString("title").orEmpty(),
            text = doc.getString("text").orEmpty(),
            urgent = doc.getBoolean("urgent") == true,
            recipientIds = (doc.get("recipientIds") as? List<String>).orEmpty(),
            authorId = doc.getString("authorId").orEmpty(),
            authorName = doc.getString("authorName").orEmpty(),
            createdAt = doc.getTimestamp("createdAt")?.toInstant(),
            delivery = Announcement.Delivery.fromKey(doc.getString("delivery")),
        )
    }
}
