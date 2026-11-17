package fr.familia.app.core.album

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.familia.app.R
import fr.familia.app.core.photo.PrefetchStorageImage
import fr.familia.app.core.photo.StorageImage
import kotlinx.coroutines.delay

/** How long each photo stays on screen. */
private const val SLIDE_MILLIS = 4_000L

/** How long a photo that cannot load stays, its drawing shown, before the next one. */
private const val SKIP_MILLIS = 1_500L

/** Oldest first, as the days went by; the album itself shows the newest first. */
fun slideshowOrder(photos: List<AlbumPhoto>): List<AlbumPhoto> = photos.sortedWith(albumPhotoOrder).reversed()

/**
 * « Lancer le diaporama » (R1 bis, DESIGN-18): the diaporama is no longer a module of its own, each
 * album starts one. Shown from two photos on.
 */
@Composable
fun SlideshowButton(photos: List<AlbumPhoto>, modifier: Modifier = Modifier) {
    if (photos.size < 2) return
    var playing by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { playing = true }, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Icon(Icons.Outlined.Slideshow, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.album_slideshow_start))
    }
    if (playing) PhotoSlideshow(slideshowOrder(photos), onClose = { playing = false })
}

/**
 * The photos one after the other on black, every 4 seconds with a fade, then again from the first.
 * « Pause » stops on a photo; the screen stays on while it plays.
 */
@Composable
fun PhotoSlideshow(photos: List<AlbumPhoto>, onClose: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var paused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(photos.isEmpty()) { if (photos.isEmpty()) onClose() }
    if (photos.isEmpty()) return
    val shown = index.coerceIn(0, photos.lastIndex)
    // A photo that cannot load (weak network, never seen on this phone) is not waited for.
    var failedId by remember { mutableStateOf<String?>(null) }
    val skip = failedId == photos[shown].id
    LaunchedEffect(shown, paused, photos.size, skip) {
        if (paused) return@LaunchedEffect
        delay(if (skip) SKIP_MILLIS else SLIDE_MILLIS)
        // Forgotten for the next round: a photo that loads then keeps its 4 seconds.
        failedId = null
        index = (shown + 1) % photos.size
    }
    val view = LocalView.current
    DisposableEffect(paused) {
        val keep = !paused
        view.keepScreenOn = keep
        onDispose { view.keepScreenOn = false }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            val light = ButtonDefaults.textButtonColors(contentColor = Color.White)
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose, colors = light, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.album_photo_close))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { paused = !paused }, colors = light, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (paused) R.string.album_slideshow_resume else R.string.album_slideshow_pause))
                }
            }
            // The next photo is fetched while this one shows, for a weak network.
            if (photos.size > 1) PrefetchStorageImage(photos[(shown + 1) % photos.size].photoPath)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(targetState = photos[shown], animationSpec = tween(800), label = "slideshow") { photo ->
                    StorageImage(
                        path = photo.photoPath,
                        contentDescription = stringResource(R.string.album_photo_by, photo.authorName),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        showLoading = true,
                        onFailed = { failedId = photo.id },
                    )
                }
            }
            Text(
                stringResource(R.string.album_photo_position, shown + 1, photos.size),
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
            )
        }
    }
}
