package fr.familia.app.feature.gifts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import fr.familia.app.core.data.LinkPreviewRepository
import fr.familia.app.core.model.GiftItem
import java.util.concurrent.ConcurrentHashMap

/**
 * The picture of a wish's link, read on the phone when the wish kept none (a wish whose link was
 * typed by hand, or saved before « Coller » read its page). Each link is read once while the app
 * lives; a page without a picture is remembered as such, so it is never read again.
 */
class LinkImages(private val previews: LinkPreviewRepository) {
    private val known = ConcurrentHashMap<String, Result>()

    private data class Result(val imageUrl: String?)

    /** The picture of a link already read, null when its page has none or it is not read yet. */
    fun cached(url: String): String? = known[url]?.imageUrl

    fun isKnown(url: String): Boolean = known.containsKey(url)

    suspend fun imageOf(url: String): String? {
        known[url]?.let { return it.imageUrl }
        // A page that could not be read (offline, refused) is tried again later; only a page read
        // without a picture is remembered as such.
        val preview = previews.preview(url) ?: return null
        known[url] = Result(preview.imageUrl)
        return preview.imageUrl
    }
}

/** Null (tests, previews): no page is read, the stored picture or 🎁 only. */
val LocalLinkImages = staticCompositionLocalOf<LinkImages?> { null }

/**
 * The picture shown for a wish after its own photo: the one kept with it, else the one its link's
 * page announces, read once. Null while it comes, and when there is none.
 */
@Composable
fun rememberLinkImage(item: GiftItem): String? {
    item.imageUrl?.let { return it }
    val url = item.url ?: return null
    val images = LocalLinkImages.current ?: return null
    var image by remember(url) { mutableStateOf(if (images.isKnown(url)) images.cached(url) else null) }
    LaunchedEffect(url) {
        if (!images.isKnown(url)) image = images.imageOf(url)
    }
    return image
}

/**
 * A picture over its placeholder: [placeholder] (🎁) shows while the picture comes and stays when it
 * fails, so the vignette never jumps.
 */
@Composable
fun PictureOver(imageUrl: String?, modifier: Modifier = Modifier, placeholder: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        placeholder()
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
