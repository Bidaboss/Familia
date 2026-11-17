package fr.familia.app.feature.feedback

import android.content.ClipData
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import fr.familia.app.R
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.model.Feedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * « Partager pour Claude » (retours d'essai du 16/11): the text of [feedbackForClaude] and, when the
 * request has one, its screenshot, in one share of Android. Pasted into the Claude app, both arrive
 * together. The picture goes through the cache folder shares/ of the FileProvider, read-only and
 * only for the app chosen. Returns false when the screenshot cannot be fetched (offline, removed):
 * the text alone is then shared.
 */
suspend fun shareFeedbackForClaude(context: Context, storage: PhotoStorage?, item: Feedback): Boolean {
    val picture = item.screenshot?.let { path -> storage?.let { fetchScreenshot(context, it, path, item.id) } }
    val text = feedbackForClaude(item, imageAttached = picture != null)
    val send = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        if (picture != null) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", picture)
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            // The preview of the share sheet, and the read right, follow the clip.
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.feedback_share_chooser)))
    return item.screenshot == null || picture != null
}

/**
 * « Enregistrer la capture »: the share sheet of the Claude app always opens a new conversation,
 * never a project. So the admin saves the screenshot in the gallery (album Pictures/Familia) and
 * joins it by hand in the project. Android 10 and later only, which needs no permission; returns
 * false before that, or when the picture cannot be fetched (offline, removed).
 */
suspend fun saveFeedbackScreenshot(context: Context, storage: PhotoStorage?, item: Feedback): Boolean {
    val path = item.screenshot ?: return false
    if (storage == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    return withContext(Dispatchers.IO) {
        runCatching {
            val url = storage.downloadUrl(path)
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "familia-retour-${item.id}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Familia")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
            try {
                URL(url).openStream().use { input ->
                    (resolver.openOutputStream(uri) ?: error("no output")).use { input.copyTo(it) }
                }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }.getOrDefault(false)
    }
}

private suspend fun fetchScreenshot(context: Context, storage: PhotoStorage, path: String, id: String): File? =
    withContext(Dispatchers.IO) {
        runCatching {
            val url = storage.downloadUrl(path)
            val dir = File(context.cacheDir, "shares").apply { mkdirs() }
            val file = File(dir, "retour-$id.jpg")
            URL(url).openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
            file
        }.getOrNull()
    }
