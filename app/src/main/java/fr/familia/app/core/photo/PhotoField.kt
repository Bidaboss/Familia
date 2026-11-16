package fr.familia.app.core.photo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.FamiliaPreviews
import java.io.File

/**
 * The photo of one element on its edit screen (PRD, récit 44): the photo, then « Ajouter une
 * photo », or « Changer la photo » and « Retirer la photo » (asked first). [stored] is the base of
 * the photo already saved ([SinglePhoto]), [edit] what the member did since; the screen saves it
 * with [SinglePhotoSaver]. The photo is shown wide, as the cover of a sheet.
 */
@Composable
fun PhotoField(
    stored: String?,
    edit: PhotoEdit,
    onEdit: (PhotoEdit) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val shown: PhotoShown? = when (edit) {
        is PhotoEdit.Picked -> PhotoShown.Local(edit.source)
        PhotoEdit.Removed -> null
        PhotoEdit.Unchanged -> stored?.let { PhotoShown.Stored(SinglePhoto.photoPath(it)) }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (shown != null) {
            PhotoPreview(shown, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            OutlinedButton(onClick = { choosing = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.photo_viewer_change))
            }
            TextButton(onClick = { confirmRemove = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.photo_remove))
            }
        } else {
            OutlinedButton(onClick = { choosing = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.photo_add))
            }
        }
    }
    if (choosing) {
        PhotoSourceSheet(onPicked = { onEdit(PhotoEdit.Picked(it)) }, onDismiss = { choosing = false })
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.photo_remove_confirm_title)) },
            text = { Text(stringResource(R.string.photo_remove_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onEdit(if (stored == null) PhotoEdit.Unchanged else PhotoEdit.Removed)
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.photo_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private sealed interface PhotoShown {
    data class Local(val uri: String) : PhotoShown
    data class Stored(val path: String) : PhotoShown
}

@Composable
private fun PhotoPreview(shown: PhotoShown, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val description = stringResource(R.string.photo_chosen)
    when (shown) {
        is PhotoShown.Stored -> StorageImage(shown.path, description, modifier.clip(shape))
        is PhotoShown.Local -> Surface(modifier, shape = shape, color = MaterialTheme.colorScheme.surfaceVariant) {
            AsyncImage(model = shown.uri, contentDescription = description, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/**
 * Where the photo comes from: the gallery, or the camera when the phone has one. [onPicked]
 * receives the content URI as text; the camera writes a file of its own each time, so a photo not
 * sent yet is never overwritten by the next one. [onDismiss] closes the sheet in every case.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSourceSheet(onPicked: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onPicked(it.toString()) }
        onDismiss()
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        cameraFile?.takeIf { taken }?.let(onPicked)
        onDismiss()
    }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.photo_add),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
            )
            SourceRow(Icons.Outlined.PhotoLibrary, stringResource(R.string.photo_pick_gallery)) {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            if (hasCamera) {
                SourceRow(Icons.Outlined.PhotoCamera, stringResource(R.string.photo_take)) {
                    val uri = newCameraUri(context)
                    cameraFile = uri.toString()
                    try {
                        camera.launch(uri)
                    } catch (e: ActivityNotFoundException) {
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(icon: ImageVector, label: String, onClick: () -> Unit) {
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

/** A new file in the cache folder photos/ of the FileProvider (res/xml/photo_paths.xml). */
private fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.photos", File(dir, "taken-${SinglePhoto.newId()}.jpg"))
}

@FamiliaPreviews
@Composable
private fun PhotoFieldEmptyPreview() {
    FamiliaTheme { Surface { PhotoField(stored = null, edit = PhotoEdit.Unchanged, onEdit = {}, modifier = Modifier.padding(16.dp)) } }
}

@FamiliaPreviews
@Composable
private fun PhotoFieldStoredPreview() {
    FamiliaTheme {
        Surface { PhotoField(stored = "events/e1/cover/m1/p1", edit = PhotoEdit.Unchanged, onEdit = {}, modifier = Modifier.padding(16.dp)) }
    }
}
