package fr.familia.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A relative of the tree without an account (récit 79): their photo when someone added one, else
 * their initial. The name is said by the row around it.
 */
@Composable
fun KinAvatar(kinId: String, firstName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    KinAvatar(LocalMemberPhotos.current[kinId], firstName, modifier, size)
}

/** The same with the [photo] given, such as one just framed and not saved yet. */
@Composable
fun KinAvatar(photo: ImageBitmap?, firstName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    if (photo != null) {
        Image(
            bitmap = photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape).clearAndSetSemantics {},
        )
        return
    }
    Box(
        modifier.size(size).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            firstName.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
