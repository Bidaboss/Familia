package fr.familia.app.feature.members.photo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
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
 * The profile photo in full screen, on a dark background (15/11). It takes the whole screen
 * (16/11, too small before): as wide as the screen, pinch or double tap to zoom up to 4 times,
 * one finger to move it once zoomed. The back gesture or the « Fermer » button closes it.
 * [onChange] offers « Changer la photo » to those who may change it.
 */
@Composable
fun MemberPhotoViewer(member: Member, photo: ImageBitmap, onDismiss: () -> Unit, onChange: (() -> Unit)? = null) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var area by remember { mutableStateOf(IntSize.Zero) }
        // Never moved past the edges of the zoomed photo.
        fun bounded(value: Offset, zoom: Float): Offset {
            val maxX = area.width * (zoom - 1f) / 2f
            val maxY = area.height * (zoom - 1f) / 2f
            return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
        }
        val transform = rememberTransformableState { zoomChange, panChange, _ ->
            val zoom = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
            offset = bounded(offset + panChange, zoom)
            scale = zoom
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(
                bitmap = photo,
                contentDescription = stringResource(R.string.photo_of, member.firstName),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { area = it }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = DOUBLE_TAP_ZOOM
                                }
                            },
                        )
                    }
                    .transformable(transform, lockRotationOnZoomPan = true)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(4.dp).size(56.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.photo_viewer_close), tint = Color.White)
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(16.dp),
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

private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f
