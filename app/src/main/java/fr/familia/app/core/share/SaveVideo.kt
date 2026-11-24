package fr.familia.app.core.share

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.URL

/** Where the films go in the gallery (récit 108, règle 32): Films › Familia. */
const val GALLERY_FILMS_FOLDER = "Movies/Familia"

/**
 * The gallery takes a film without any permission from Android 10 on. Before (Android 8 and 9,
 * minSdk 26), it would need one: « Télécharger » is not offered there, « Partager » stays
 * (docs/etude-film-etape.md, part 6; as the screenshots of the feedback, note C of #288).
 */
fun canSaveVideoToGallery(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

/** Copies the film read from [open] into the gallery as [displayName]; false when it could not be done. */
suspend fun saveVideoToGallery(context: Context, displayName: String, open: () -> InputStream): Boolean {
    if (!canSaveVideoToGallery()) return false
    return withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, GALLERY_FILMS_FOLDER)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
            try {
                open().use { input -> (resolver.openOutputStream(uri) ?: error("no output")).use { input.copyTo(it) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }.getOrDefault(false)
    }
}

/** A film made on this phone. */
suspend fun saveVideoToGallery(context: Context, file: File, displayName: String): Boolean =
    saveVideoToGallery(context, displayName) { file.inputStream() }

/** A film published in Storage, read from its address. */
suspend fun saveVideoUrlToGallery(context: Context, url: String, displayName: String): Boolean =
    saveVideoToGallery(context, displayName) { URL(url).openStream() }

/** « familia-etape-ajaccio.mp4 »: letters and digits only, the gallery shows the rest as it likes. */
fun galleryFilmName(vararg parts: String): String =
    (listOf("familia") + parts).joinToString("-") { part ->
        java.text.Normalizer.normalize(part, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
    }.replace(Regex("-+"), "-") + ".mp4"
