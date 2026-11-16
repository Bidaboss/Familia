package fr.familia.app.core.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import fr.familia.app.R
import java.io.File

/*
 * Sharing a film or a slideshow outside the app (carte EXPERT-6, PRD rév. 178). The videos keep
 * every photo, children included: before the share sheet of Android opens, one message reminds to
 * ask the parents of the children who appear. One component for the film of a trip (récit 16), the
 * album of an event (récit 41) and the slideshow of the month (récit 42).
 */

/** The reminder shown before sharing, with its single button « Continuer ». Back closes it. */
@Composable
fun ShareConsentReminder(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_consent_title)) },
        text = { Text(stringResource(R.string.share_consent_body)) },
        confirmButton = { TextButton(onClick = onContinue, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.share_consent_continue)) } },
    )
}

/** The authority of the FileProvider of the manifest (res/xml/photo_paths.xml lists the folders). */
private fun authority(context: Context) = "${context.packageName}.photos"

/** A video of the cache folder films/, for another app: read-only and only for that app. */
private fun videoIntent(context: Context, file: File, action: String): Intent {
    val uri = FileProvider.getUriForFile(context, authority(context), file)
    return Intent(action).apply {
        if (action == Intent.ACTION_SEND) {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
        } else {
            setDataAndType(uri, "video/mp4")
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

/** Opens the share sheet of Android for a plain text (a recipe): no photo, so no reminder needed. */
fun shareText(context: Context, text: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

/** Opens the share sheet of Android for [file]. Call it only after [ShareConsentReminder]. */
fun shareVideo(context: Context, file: File, chooserTitle: String) {
    context.startActivity(Intent.createChooser(videoIntent(context, file, Intent.ACTION_SEND), chooserTitle))
}

/** Plays [file] in the video app of the phone; false when the phone has none. */
fun playVideo(context: Context, file: File): Boolean = try {
    context.startActivity(videoIntent(context, file, Intent.ACTION_VIEW))
    true
} catch (e: ActivityNotFoundException) {
    false
}
