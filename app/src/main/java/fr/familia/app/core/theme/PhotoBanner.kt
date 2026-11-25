package fr.familia.app.core.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** The height of the banner of the menus (A4: about 230 dp, down under the title). */
val PHOTO_BANNER_HEIGHT = 230.dp

/**
 * The photo of the theme at the top of a menu (règle 20, DESIGN-157 A, fiche A4): about 230 dp,
 * under the same light veil as the Accueil, melting into the background of the screen. It scrolls
 * with the content, one for one (no parallax here), following [scroll]. It draws nothing without a
 * photo theme (Argile, Contraste fort). Put it behind the screen, its title above it in white while
 * [onPhotoBanner] is true. Used by Famille and Tout Familia; Cadeaux will use it as it is.
 */
@Composable
fun PhotoBanner(scroll: PhotoScroll, modifier: Modifier = Modifier) {
    val theme = LocalPhotoTheme.current ?: return
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .height(PHOTO_BANNER_HEIGHT)
            .graphicsLayer { translationY = -scroll.offset }
            .clipToBounds(),
    ) {
        ThemePhotoImage(theme, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x59140C05),
                    0.4f to Color(0x1A140C05),
                    0.6f to background.copy(alpha = 0f),
                    1f to background,
                ),
            ),
        )
    }
}

/**
 * Whether the title of the screen lies on the photo of [PhotoBanner]: a photo theme, and the content
 * not yet scrolled past the top of the banner. The title is then white with a light shadow (A4).
 */
@Composable
fun onPhotoBanner(scroll: PhotoScroll): Boolean {
    if (LocalPhotoTheme.current == null) return false
    val limit = with(LocalDensity.current) { BANNER_TITLE_ROOM.toPx() }
    return scroll.offset < limit
}

/** How far the content may scroll while the title still lies on the dark top of the photo. */
private val BANNER_TITLE_ROOM = 48.dp
