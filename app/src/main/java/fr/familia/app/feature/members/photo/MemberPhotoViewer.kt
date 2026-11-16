package fr.familia.app.feature.members.photo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.familia.app.R
import fr.familia.app.core.model.Member

/**
 * The profile photo in full screen, on a dark background (15/11). The back gesture or the
 * « Fermer » button closes it. [onChange] offers « Changer la photo » to those who may change it.
 */
@Composable
fun MemberPhotoViewer(member: Member, photo: ImageBitmap, onDismiss: () -> Unit, onChange: (() -> Unit)? = null) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Image(
                bitmap = photo,
                contentDescription = stringResource(R.string.photo_of, member.firstName),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(56.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.photo_viewer_close), tint = Color.White)
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(member.firstName, style = MaterialTheme.typography.titleLarge, color = Color.White)
                if (onChange != null) {
                    Button(onClick = onChange, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.photo_viewer_change))
                    }
                }
            }
        }
    }
}
