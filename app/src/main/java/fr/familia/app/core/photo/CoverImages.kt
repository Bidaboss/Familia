package fr.familia.app.core.photo

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The cover of an element across the width of its sheet (PRD, récit 44), from its base in Storage
 * ([SinglePhoto]). Decorative: the title right below says what it is.
 */
@Composable
fun CoverBanner(base: String, modifier: Modifier = Modifier) {
    StorageImage(
        SinglePhoto.photoPath(base),
        contentDescription = null,
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)),
    )
}

/** The small square of a cover on a card of a list, read from the 400 px thumbnail. */
@Composable
fun CoverThumbnail(base: String, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    StorageImage(
        SinglePhoto.thumbPath(base),
        contentDescription = null,
        modifier = modifier.size(size).clip(RoundedCornerShape(12.dp)),
    )
}
