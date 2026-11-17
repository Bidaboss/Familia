package fr.familia.app.core.photo

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * Photos in Cloud Storage. Each module passes its own path, for example
 * goodNews/{authorId}/{postId}/{photoId}.jpg or trips/{tripId}/{memberId}/{photoId}.jpg; what
 * each path allows is in firebase/storage.rules.
 */
interface PhotoStorage {
    /** Sends a JPEG. Storage refuses to replace a file that already exists. */
    suspend fun upload(path: String, jpeg: ByteArray)

    /** The same, telling how much is sent, from 0 to 1. */
    suspend fun upload(path: String, jpeg: ByteArray, onProgress: (Float) -> Unit) {
        upload(path, jpeg)
        onProgress(1f)
    }

    /** A link the image loader can read, valid as long as the file exists. */
    suspend fun downloadUrl(path: String): String

    suspend fun delete(path: String)

    /** The file itself, for a share that carries the photo (récit 31); null when it is too big or unreadable. */
    suspend fun bytes(path: String, maxBytes: Long = SHARE_PHOTO_MAX_BYTES): ByteArray? = null
}

/** A photo of the app is about 1 Mo once compressed: 3 Mo leaves room without filling the memory. */
const val SHARE_PHOTO_MAX_BYTES = 3_000_000L

class FirebasePhotoStorage(private val storage: FirebaseStorage) : PhotoStorage {

    private val jpegMetadata = StorageMetadata.Builder().setContentType("image/jpeg").build()

    // A photo never changes once sent, so its link is asked once per launch of the app.
    private val urls = ConcurrentHashMap<String, String>()

    override suspend fun upload(path: String, jpeg: ByteArray) = upload(path, jpeg) { }

    override suspend fun upload(path: String, jpeg: ByteArray, onProgress: (Float) -> Unit) {
        val task = storage.reference.child(path).putBytes(jpeg, jpegMetadata)
        task.addOnProgressListener { snapshot ->
            if (snapshot.totalByteCount > 0) onProgress(snapshot.bytesTransferred.toFloat() / snapshot.totalByteCount)
        }
        try {
            task.await()
        } catch (e: CancellationException) {
            // « Annuler l'envoi »: stop the transfer too, so no half photo arrives later.
            task.cancel()
            throw e
        } catch (e: StorageException) {
            if (e.errorCode == StorageException.ERROR_NOT_AUTHORIZED) throw PhotoRefusedException(e)
            throw e
        }
    }

    override suspend fun downloadUrl(path: String): String =
        urls[path] ?: storage.reference.child(path).downloadUrl.await().toString().also { urls[path] = it }

    override suspend fun delete(path: String) {
        storage.reference.child(path).delete().await()
        urls.remove(path)
    }

    override suspend fun bytes(path: String, maxBytes: Long): ByteArray? = try {
        storage.reference.child(path).getBytes(maxBytes).await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Too big, deleted meanwhile, no network: the share goes without its photo.
        null
    }
}
