package fr.familia.app.feature.members.photo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import fr.familia.app.R
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.PhotoProcessing
import fr.familia.app.core.photo.SquareCrop
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.FamiliaPreviews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Changing a profile photo (PRD, récit 3 bis): a sheet with the gallery, the camera and « Retirer »,
 * then a framing screen. Only pictures taken or chosen here are used, never the Google account
 * photo. [onSave] receives the finished JPEG; [onDismiss] closes everything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberPhotoEditor(
    member: Member,
    hasPhoto: Boolean,
    onSave: (ByteArray) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Saved across a rotation: the picture to frame once chosen, and whether the sheet is open.
    var picture by rememberSaveable { mutableStateOf<Uri?>(null) }
    var choosing by rememberSaveable { mutableStateOf(true) }
    val cameraFile = remember { cameraUri(context) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) picture = uri else onDismiss()
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) picture = cameraFile else onDismiss()
    }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }

    if (choosing && picture == null) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    text = stringResource(R.string.photo_of, member.firstName),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
                )
                SheetRow(Icons.Outlined.PhotoLibrary, stringResource(R.string.photo_pick_gallery)) {
                    choosing = false
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (hasCamera) {
                    SheetRow(Icons.Outlined.PhotoCamera, stringResource(R.string.photo_take)) {
                        choosing = false
                        try {
                            camera.launch(cameraFile)
                        } catch (e: ActivityNotFoundException) {
                            onDismiss()
                        }
                    }
                }
                if (hasPhoto) {
                    SheetRow(Icons.Outlined.Delete, stringResource(R.string.photo_remove)) {
                        onRemove()
                        onDismiss()
                    }
                }
            }
        }
    }

    picture?.let { uri ->
        PhotoCropDialog(
            uri = uri,
            onConfirm = { jpeg ->
                onSave(jpeg)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

/** The file the camera app writes to, in the cache: replaced by each new picture. */
private fun cameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.photos", File(dir, "camera.jpg"))
}

/** Reads the picture at [uri], then lets the member frame it. */
@Composable
private fun PhotoCropDialog(uri: Uri, onConfirm: (ByteArray) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var unreadable by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        val decoded = withContext(Dispatchers.IO) { PhotoProcessing.decode(context.contentResolver, uri) }
        if (decoded == null) unreadable = true else bitmap = decoded
    }
    if (unreadable) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.photo_unreadable_title)) },
            text = { Text(stringResource(R.string.photo_unreadable_body)) },
            confirmButton = {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
        return
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            val source = bitmap
            if (source == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val image = remember(source) { source.asImageBitmap() }
                val scope = rememberCoroutineScope()
                var saving by remember { mutableStateOf(false) }
                PhotoCropContent(
                    image = image,
                    saving = saving,
                    onConfirm = { crop ->
                        saving = true
                        scope.launch {
                            val jpeg = withContext(Dispatchers.Default) { PhotoProcessing.encode(source, crop) }
                            onConfirm(jpeg)
                        }
                    },
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

/** Framing screen: the picture under a round window, moved by dragging and pinching. Stateless but for the framing. */
@Composable
internal fun PhotoCropContent(
    image: ImageBitmap,
    saving: Boolean,
    onConfirm: (SquareCrop) -> Unit,
    onDismiss: () -> Unit,
) {
    var crop by remember(image) { mutableStateOf(SquareCrop(image.width, image.height)) }
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)
    val ring = MaterialTheme.colorScheme.surface
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.photo_crop_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.photo_crop_hint), style = MaterialTheme.typography.bodyLarge)
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clipToBounds()
                .pointerInput(image) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val side = size.width.toFloat()
                        crop = crop.transform(pan.x / side, pan.y / side, zoom)
                    }
                },
        ) {
            val side = size.width
            drawImage(
                image = image,
                dstOffset = IntOffset((crop.drawnLeft * side).roundToInt(), (crop.drawnTop * side).roundToInt()),
                dstSize = IntSize((crop.drawnWidth * side).roundToInt(), (crop.drawnHeight * side).roundToInt()),
            )
            // Darkens what stays outside the round avatar.
            val window = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addOval(Rect(Offset.Zero, size))
            }
            drawPath(window, scrim)
            drawCircle(ring, radius = side / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
        }
        // Buttons too, for those who cannot pinch (TalkBack, a hand busy with a child).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            IconButton(onClick = { crop = crop.transform(0f, 0f, 1f / 1.25f) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.ZoomOut, contentDescription = stringResource(R.string.photo_zoom_out))
            }
            IconButton(onClick = { crop = crop.transform(0f, 0f, 1.25f) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.ZoomIn, contentDescription = stringResource(R.string.photo_zoom_in))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(onClick = { onConfirm(crop) }, enabled = !saving, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.photo_crop_confirm))
            }
        }
    }
}

/** A plain gradient stands for a picture in previews and screenshots (no real photo). */
internal fun previewPicture(): ImageBitmap {
    val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()
    for (x in 0 until 300 step 20) {
        paint.color = android.graphics.Color.HSVToColor(floatArrayOf(x * 1.2f, 0.5f, 0.9f))
        canvas.drawRect(x.toFloat(), 0f, x + 20f, 200f, paint)
    }
    return bitmap.asImageBitmap()
}

@FamiliaPreviews
@Composable
private fun PhotoCropPreview() {
    FamiliaTheme { Surface { PhotoCropContent(previewPicture(), saving = false, onConfirm = {}, onDismiss = {}) } }
}
