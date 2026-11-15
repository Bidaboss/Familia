package fr.familia.app.core.photo

import android.net.Uri

/**
 * Encodes a photo chosen on the phone and sends it with its thumbnail to the two Storage paths
 * given by the module. [source] is the content URI as text, so ViewModels stay testable on the JVM.
 */
fun interface PhotoUploader {
    suspend fun upload(source: String, photoPath: String, thumbnailPath: String)
}

class StoragePhotoUploader(private val encoder: PhotoEncoder, private val storage: PhotoStorage) : PhotoUploader {
    override suspend fun upload(source: String, photoPath: String, thumbnailPath: String) {
        val (photo, thumbnail) = encoder.encodeForUpload(Uri.parse(source))
        storage.upload(photoPath, photo)
        storage.upload(thumbnailPath, thumbnail)
    }
}
