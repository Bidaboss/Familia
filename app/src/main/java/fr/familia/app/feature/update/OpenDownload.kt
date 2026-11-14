package fr.familia.app.feature.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Opens the Drive folder in the browser or the Drive app. */
fun openDownload(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // No browser at all: nothing sensible to do, the offer stays on screen.
    }
}
