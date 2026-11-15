package fr.familia.app.core.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/** The Storage of the app, given once by FamiliaApp. Null in previews and screenshots. */
val LocalPhotoStorage = staticCompositionLocalOf<PhotoStorage?> { null }

/**
 * A photo kept in Storage at [path]. Shows a soft placeholder while the link is fetched, offline
 * before the photo was ever seen, and in previews. Coil keeps the photos it has shown on the phone.
 */
@Composable
fun StorageImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val storage = LocalPhotoStorage.current
    var url by remember(path, storage) { mutableStateOf<String?>(null) }
    LaunchedEffect(path, storage) {
        val photos = storage ?: return@LaunchedEffect
        url = try {
            photos.downloadUrl(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // Offline or removed: the grey box stays.
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        url?.let {
            AsyncImage(model = it, contentDescription = contentDescription, modifier = Modifier.matchParentSize(), contentScale = contentScale)
        }
    }
}
