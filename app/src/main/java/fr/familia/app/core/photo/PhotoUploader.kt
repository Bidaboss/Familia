package fr.familia.app.core.photo

import android.net.Uri
import java.time.Instant

/**
 * Encodes a photo chosen on the phone and sends it with its thumbnail to the two Storage paths
 * given by the module. [source] is the content URI as text, so ViewModels stay testable on the JVM.
 */
fun interface PhotoUploader {
    suspend fun upload(source: String, photoPath: String, thumbnailPath: String)

    /** The same, telling how much is sent, from 0 to 1, for a progress bar. */
    suspend fun upload(source: String, photoPath: String, thumbnailPath: String, onProgress: (Float) -> Unit) {
        upload(source, photoPath, thumbnailPath)
        onProgress(1f)
    }

    /** When the photo at [source] was taken, from its EXIF, or null (récit 36, « Il y a N ans »). */
    suspend fun takenAt(source: String): Instant? = null
}

class StoragePhotoUploader(private val encoder: PhotoEncoder, private val storage: PhotoStorage) : PhotoUploader {
    override suspend fun upload(source: String, photoPath: String, thumbnailPath: String) = upload(source, photoPath, thumbnailPath) { }

    override suspend fun upload(source: String, photoPath: String, thumbnailPath: String, onProgress: (Float) -> Unit) {
        val (photo, thumbnail) = encoder.encodeForUpload(Uri.parse(source))
        // The photo and its thumbnail count by their size, so the bar moves evenly.
        val total = (photo.size + thumbnail.size).coerceAtLeast(1).toFloat()
        storage.upload(photoPath, photo) { done -> onProgress(done * photo.size / total) }
        storage.upload(thumbnailPath, thumbnail) { done -> onProgress((photo.size + done * thumbnail.size) / total) }
    }

    override suspend fun takenAt(source: String): Instant? = encoder.takenAt(Uri.parse(source))
}
