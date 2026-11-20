package fr.familia.app.core.data

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await

/**
 * Photos live in `memberPhotos/{memberId}` as `{ jpeg: bytes, updatedAt }` (docs/data-model.md),
 * those of the relatives of the tree in `kinPhotos/{kinId}`, the same way.
 * Firestore rather than Storage: 13 photos of 100 KB fit easily, they work offline like the rest of
 * the app, and the same security rules protect them.
 */
class FirestoreMemberPhotoRepository(private val db: FirebaseFirestore) : MemberPhotoRepository {

    /** Members' photos, and those of the relatives of the tree (récit 79): their ids never meet. */
    override fun observePhotos(): Flow<Map<String, MemberPhoto>> =
        combine(listen(MEMBER_PHOTOS, required = true), listen(KIN_PHOTOS, required = false)) { members, kin -> kin + members }

    /**
     * One collection, live. A [required] one closes the flow on an error; the other just goes
     * empty, so a refused read of the relatives' photos never takes the members' ones away.
     */
    private fun listen(collection: String, required: Boolean): Flow<Map<String, MemberPhoto>> = callbackFlow {
        val registration = db.collection(collection).addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (required) close(error) else trySend(emptyMap())
                return@addSnapshotListener
            }
            val photos = snapshot?.documents.orEmpty().mapNotNull { doc ->
                val jpeg = doc.getBlob("jpeg")?.toBytes() ?: return@mapNotNull null
                // The server time is still pending right after our own write: the length tells
                // the new photo apart until it arrives.
                val version = doc.getTimestamp("updatedAt")?.toDate()?.time ?: -jpeg.size.toLong()
                MemberPhoto(doc.id, jpeg, version)
            }
            trySend(photos.associateBy { it.memberId })
        }
        awaitClose { registration.remove() }
    }

    override suspend fun savePhoto(memberId: String, jpeg: ByteArray) {
        db.collection(MEMBER_PHOTOS).document(memberId)
            .set(mapOf("jpeg" to Blob.fromBytes(jpeg), "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    override suspend fun removePhoto(memberId: String) {
        db.collection(MEMBER_PHOTOS).document(memberId).delete().await()
    }

    companion object {
        const val MEMBER_PHOTOS = "memberPhotos"

        /** kinPhotos/{kinId}, written with the relative by [FirestoreGenealogyRepository]. */
        const val KIN_PHOTOS = "kinPhotos"
    }
}
