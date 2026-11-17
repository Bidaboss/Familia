package fr.familia.app.core.photo

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

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

    /**
     * The last link known for [path], even from an earlier launch, without the network: with it, a
     * photo already seen comes out of the image cache when the network is weak or gone.
     */
    fun knownUrl(path: String): String? = null

    suspend fun delete(path: String)

    /** The file itself, for a share that carries the photo (récit 31); null when it is too big or unreadable. */
    suspend fun bytes(path: String, maxBytes: Long = SHARE_PHOTO_MAX_BYTES): ByteArray? = null
}

/** A photo of the app is about 1 Mo once compressed: 3 Mo leaves room without filling the memory. */
const val SHARE_PHOTO_MAX_BYTES = 3_000_000L

class FirebasePhotoStorage(
    private val storage: FirebaseStorage,
    private val links: PhotoLinks = PhotoLinks(),
) : PhotoStorage {

    private val jpegMetadata = StorageMetadata.Builder().setContentType("image/jpeg").build()

<<<<<<< HEAD
    override suspend fun upload(path: String, jpeg: ByteArray) {
=======
    // A photo never changes once sent, so its link is asked once per launch of the app.
    private val urls = ConcurrentHashMap<String, String>()

    override suspend fun upload(path: String, jpeg: ByteArray) = upload(path, jpeg) { }

    override suspend fun upload(path: String, jpeg: ByteArray, onProgress: (Float) -> Unit) {
        val task = storage.reference.child(path).putBytes(jpeg, jpegMetadata)
        task.addOnProgressListener { snapshot ->
            if (snapshot.totalByteCount > 0) onProgress(snapshot.bytesTransferred.toFloat() / snapshot.totalByteCount)
        }
>>>>>>> origin/main
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

    override suspend fun downloadUrl(path: String): String = links.fresh(path) {
        try {
            storage.reference.child(path).downloadUrl.await().toString()
        } catch (e: StorageException) {
            if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) links.forget(path)
            throw e
        }
    }

    override fun knownUrl(path: String): String? = links.known(path)

    override suspend fun delete(path: String) {
        storage.reference.child(path).delete().await()
        links.forget(path)
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

/**
 * The links of the photos. Each one is asked once per launch of the app ([fresh]), since a place of
 * an album can be emptied then filled again with another photo; the last one is also kept on the
 * phone ([saved]), so a photo already seen shows from the image cache without the network.
 */
class PhotoLinks(private val saved: SavedLinks = SavedLinks.None) {
    private val thisLaunch = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun fresh(path: String, ask: suspend () -> String): String =
        thisLaunch[path] ?: ask().also {
            thisLaunch[path] = it
            if (saved.get(path) != it) saved.put(path, it)
        }

    fun known(path: String): String? = thisLaunch[path] ?: saved.get(path)

    fun forget(path: String) {
        thisLaunch.remove(path)
        saved.put(path, null)
    }
}

/** Where the links are kept between two launches: the preferences of the app, or nowhere in tests. */
interface SavedLinks {
    fun get(path: String): String?
    fun put(path: String, url: String?)

    object None : SavedLinks {
        override fun get(path: String): String? = null
        override fun put(path: String, url: String?) = Unit
    }
}

class PreferencesLinks(private val prefs: android.content.SharedPreferences) : SavedLinks {
    override fun get(path: String): String? = prefs.getString(path, null)
    override fun put(path: String, url: String?) {
        prefs.edit().apply { if (url == null) remove(path) else putString(path, url) }.apply()
    }
}
