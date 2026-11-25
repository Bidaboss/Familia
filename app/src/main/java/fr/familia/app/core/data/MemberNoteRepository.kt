package fr.familia.app.core.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * « Mes notes » (récit 114): members/{authorId}/notes/{aboutId}, a private notepad of the author
 * about another member. Only its author reads it (firestore.rules), never the person it is about
 * nor the admin principal, so « Voir comme » has none. Saved on its own, apart from « Enregistrer ».
 */
interface MemberNoteRepository {
    /** The note of [authorId] about [aboutId]; empty when there is none. */
    fun observe(authorId: String, aboutId: String): Flow<String>

    /** An empty text removes the note. */
    suspend fun save(authorId: String, aboutId: String, text: String)

    companion object {
        const val MAX_TEXT = 2_000
    }
}

class FirestoreMemberNoteRepository(private val db: FirebaseFirestore) : MemberNoteRepository {

    private fun note(authorId: String, aboutId: String) =
        db.collection("members").document(authorId).collection("notes").document(aboutId)

    override fun observe(authorId: String, aboutId: String): Flow<String> = callbackFlow {
        val registration = note(authorId, aboutId).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.getString("text").orEmpty())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun save(authorId: String, aboutId: String, text: String) {
        val ref = note(authorId, aboutId)
        val kept = text.trim().take(MemberNoteRepository.MAX_TEXT)
        if (kept.isEmpty()) {
            ref.delete().await()
        } else {
            ref.set(mapOf("text" to kept, "updatedAt" to FieldValue.serverTimestamp())).await()
        }
    }
}
