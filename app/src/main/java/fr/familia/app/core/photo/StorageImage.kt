package fr.familia.app.core.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException

/** The Storage of the app, given once by FamiliaApp. Null in previews and screenshots. */
val LocalPhotoStorage = staticCompositionLocalOf<PhotoStorage?> { null }

/** Whether the phone has the network, given once by MainActivity: a photo that failed tries again when it comes back. */
val LocalOnline = compositionLocalOf { true }

/**
 * A photo kept in Storage at [path]. A photo already seen shows at once from the phone, even with a
 * weak or no network: its last link is kept ([PhotoStorage.knownUrl]) and the image cache does not
 * ask the server again (FamiliaApplication). A photo that cannot load shows a picture drawing on
 * the clay background, never an empty box. In previews, the clay background alone.
 */
@Composable
fun StorageImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val storage = LocalPhotoStorage.current
    val online = LocalOnline.current
    var url by remember(path, storage) { mutableStateOf(storage?.knownUrl(path)) }
    var asked by remember(path, storage) { mutableStateOf(false) }
    var failed by remember(path, storage) { mutableStateOf(false) }
    // Asked again when the network comes back: a photo never seen then shows without leaving the screen.
    LaunchedEffect(path, storage, online) {
        val photos = storage ?: return@LaunchedEffect
        if (online) failed = false
        val link = try {
            photos.downloadUrl(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline: the link known from before, if any. Removed: forgotten, so the drawing.
            photos.knownUrl(path)
        }
        // A new link gets its own try, even after the old one failed.
        if (link != url) failed = false
        url = link
        asked = true
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val shown = url
        if (shown != null && !failed) {
            AsyncImage(
                model = shown,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
                onError = { failed = true },
                onSuccess = { failed = false },
            )
        } else if (failed || (asked && shown == null)) {
            PhotoMissing()
        }
    }
}

/** What a photo that cannot load shows: a picture drawing in the soft ink of the theme. */
@Composable
private fun PhotoMissing() {
    Icon(
        Icons.Outlined.Image,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.size(40.dp),
    )
}

/**
 * Puts the photo at [path] in the image cache without showing it, for the next photo of a
 * diaporama: on a weak network it is there when its turn comes.
 */
@Composable
fun PrefetchStorageImage(path: String) {
    val storage = LocalPhotoStorage.current ?: return
    val context = LocalContext.current
    LaunchedEffect(path, storage) {
        val url = try {
            storage.downloadUrl(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@LaunchedEffect
        }
        Coil.imageLoader(context).enqueue(ImageRequest.Builder(context).data(url).build())
    }
}
