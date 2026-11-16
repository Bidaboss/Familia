package fr.familia.app.core.photo

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
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

    /** A link the image loader can read, valid as long as the file exists. */
    suspend fun downloadUrl(path: String): String

    suspend fun delete(path: String)
}

class FirebasePhotoStorage(private val storage: FirebaseStorage) : PhotoStorage {

    private val jpegMetadata = StorageMetadata.Builder().setContentType("image/jpeg").build()

    // A photo never changes once sent, so its link is asked once per launch of the app.
    private val urls = ConcurrentHashMap<String, String>()

    override suspend fun upload(path: String, jpeg: ByteArray) {
        try {
            storage.reference.child(path).putBytes(jpeg, jpegMetadata).await()
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
}
