package fr.familia.app.core.album

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.PhotoRefusedException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant

/** Photo records in `{folder}/photos/{authorId}_{slot}` (docs/data-model.md). */
class FirestoreAlbumPhotoRepository(private val db: FirebaseFirestore) : AlbumPhotoRepository {

    override fun observePhotos(folder: String): Flow<List<AlbumPhoto>> = callbackFlow {
        val registration = photos(folder).addSnapshotListener { snapshot, error ->
            when {
                // No longer allowed to see the album (unticked from a trip): no photos.
                error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    trySend(emptyList())
                    close()
                }
                error != null -> close(error)
                snapshot != null -> trySend(snapshot.documents.mapNotNull { it.toPhoto(folder) }.sortedWith(albumPhotoOrder))
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun addPhoto(folder: String, slot: Int, me: Member) = addPhoto(folder, slot, me, null)

    override suspend fun addPhoto(folder: String, slot: Int, me: Member, takenAt: Instant?) = addPhoto(folder, slot, me, takenAt, null)

    override suspend fun addPhoto(folder: String, slot: Int, me: Member, takenAt: Instant?, stepId: String?) {
        val record = buildMap<String, Any> {
            put("authorId", me.id)
            put("authorName", me.firstName)
            put("slot", slot)
            put("createdAt", FieldValue.serverTimestamp())
            takenAt?.let { put("takenAt", Timestamp(it.epochSecond, 0)) }
            stepId?.let { put("stepId", it) }
        }
        try {
            photos(folder).document(AlbumPhoto.photoId(me.id, slot)).set(record).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) throw PhotoRefusedException(e)
            throw e
        }
    }

    override suspend fun deletePhoto(photo: AlbumPhoto) {
        photos(photo.folder).document(photo.id).delete().await()
    }

    private fun photos(folder: String) = db.document(folder).collection(PHOTOS)

    private fun DocumentSnapshot.toPhoto(folder: String): AlbumPhoto? {
        val authorId = getString("authorId") ?: return null
        val slot = getLong("slot")?.toInt() ?: return null
        return AlbumPhoto(
            folder = folder,
            authorId = authorId,
            authorName = getString("authorName").orEmpty(),
            slot = slot,
            createdAt = getTimestamp("createdAt")?.toDate()?.toInstant(),
            takenAt = getTimestamp("takenAt")?.toDate()?.toInstant(),
            stepId = getString("stepId"),
        )
    }

    private companion object {
        const val PHOTOS = "photos"
    }
}
