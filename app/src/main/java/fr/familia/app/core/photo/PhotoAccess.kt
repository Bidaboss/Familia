package fr.familia.app.core.photo

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Keeps the right to read photos chosen in the Android picker. Without it, the right ends with the
 * app: when Android stops Familia while the camera or the gallery is open, the photos of a draft
 * could no longer be read once the member comes back. A picker that does not offer it (an old
 * phone) is simply left as it is.
 */
fun keepPhotoAccess(context: Context, uris: List<Uri>) {
    for (uri in uris) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}
