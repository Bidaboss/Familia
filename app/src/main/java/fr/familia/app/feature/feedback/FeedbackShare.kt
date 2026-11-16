package fr.familia.app.feature.feedback

import android.content.ClipData
import android.content.Context
import android.content.Intent
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
