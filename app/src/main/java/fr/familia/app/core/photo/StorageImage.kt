package fr.familia.app.core.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
    val url by produceState<String?>(initialValue = null, path, storage) {
        value = storage?.let { runCatching { it.downloadUrl(path) }.getOrNull() }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        url?.let {
            AsyncImage(model = it, contentDescription = contentDescription, modifier = Modifier.matchParentSize(), contentScale = contentScale)
        }
    }
}
