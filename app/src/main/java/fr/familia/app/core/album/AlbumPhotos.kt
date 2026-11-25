package fr.familia.app.core.album

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.familia.app.R
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.photo.keepPhotoAccess

/** What the photo views can ask for. The defaults do nothing, for previews and screenshots. */
data class PhotoActions(
    val onAdd: (sources: List<String>) -> Unit = {},
    val onRemove: (AlbumPhoto) -> Unit = {},
    val onSeeAll: () -> Unit = {},
    val onDismissProblem: () -> Unit = {},
)

/** How many photos the view of the trip or event shows before « Voir les photos ». */
const val PHOTOS_SHOWN_IN_SECTION = 6

/**
 * The photos part of the view of a trip or an event: the newest photos, « Voir les photos » and
 * « Ajouter des photos » for a member who may add some. [emptyText] is said while there is none.
 */
@Composable
fun AlbumPhotosSection(state: AlbumPhotosUiState, actions: PhotoActions, emptyText: String, showSeeAll: Boolean = true) {
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.photos.isEmpty() && state.sending == 0) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PhotoGrid(state.photos.take(PHOTOS_SHOWN_IN_SECTION), onOpen = { openedId = it.id })
        PhotoStatus(state, actions.onDismissProblem)
        // The full grid is where the film is made, so it opens from the first photo.
        if (showSeeAll && state.photos.isNotEmpty()) {
            TextButton(onClick = actions.onSeeAll, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(pluralStringResource(R.plurals.album_photos_see_all, state.photos.size, state.photos.size))
            }
        }
        if (state.canAdd) AddPhotosButton(state.placesLeft, actions.onAdd, max = state.maxPerMember)
    }
    openedId?.let { id ->
        PhotoViewer(state, id, actions.onRemove, onClose = { openedId = null })
    }
}

/** Thumbnails three by three, the newest first. Touching one opens it full size. */
@Composable
fun PhotoGrid(photos: List<AlbumPhoto>, onOpen: (AlbumPhoto) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val open = stringResource(R.string.album_photo_open)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        photos.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { photo ->
                    StorageImage(
                        path = photo.thumbnailPath,
                        contentDescription = stringResource(R.string.album_photo_by, photo.authorName),
                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(shape)
                            .clickable(onClickLabel = open) { onOpen(photo) },
                    )
                }
                // A short last row keeps the size of the others.
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** « Encore 2 photos à envoyer » and how far the send is, then what went wrong, if anything. */
@Composable
fun PhotoStatus(state: AlbumPhotosUiState, onDismiss: () -> Unit) {
    if (state.sending > 0) {
        Text(
            pluralStringResource(R.plurals.album_photos_sending, state.sending, state.sending),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.sent == 0) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { state.sendProgress }, modifier = Modifier.fillMaxWidth())
        }
    }
    val problem = state.problem ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            when (problem) {
                PhotoProblem.SendFailed -> stringResource(R.string.album_photos_send_failed)
                PhotoProblem.Unreadable -> stringResource(R.string.album_photos_unreadable)
                PhotoProblem.Refused -> stringResource(R.string.album_photos_refused)
                PhotoProblem.TooMany -> stringResource(R.string.album_photos_too_many, state.maxPerMember)
                PhotoProblem.RemoveFailed -> stringResource(R.string.album_photos_remove_failed)
            },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(R.string.album_photos_ok))
        }
    }
}

/** Opens the photo picker of Android (no permission needed), up to the places left. */
@Composable
fun AddPhotosButton(placesLeft: Int, onAdd: (List<String>) -> Unit, max: Int = AlbumPhoto.MAX_PER_MEMBER) {
    // The picker needs a limit of 2 or more; the ViewModel keeps only what fits.
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxOf(2, placesLeft))) { uris ->
        keepPhotoAccess(context, uris)
        onAdd(uris.map { it.toString() })
    }
    FilledTonalButton(
        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        enabled = placesLeft > 0,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.album_photos_add))
    }
    Text(
        if (placesLeft > 0) {
            pluralStringResource(R.plurals.album_photos_places_left, placesLeft, placesLeft)
        } else {
            stringResource(R.string.album_photos_full, max)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The photos full size, one per page, starting at [startId]. Who may remove the photo shown
 * ([AlbumPhotosUiState.canRemove]) finds « Supprimer » in its menu « ⋯ », after a confirmation.
 */
@Composable
fun PhotoViewer(state: AlbumPhotosUiState, startId: String, onRemove: (AlbumPhoto) -> Unit, onClose: () -> Unit) {
    val photos = state.photos
    val start = photos.indexOfFirst { it.id == startId }
    // Removed meanwhile (here or on another phone): nothing left to show.
    LaunchedEffect(start) { if (start < 0) onClose() }
    if (start < 0) return
    val pager = rememberPagerState(initialPage = start) { photos.size }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                val current = photos.getOrNull(pager.currentPage)
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 56.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.album_photo_close))
                    }
                    Text(
                        current?.let { stringResource(R.string.album_photo_by, it.authorName) }.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    // Récit 121: in a menu « ⋯ », for its author, a responsible adult of the child
                    // who added it or the admin principal; never on the photo of another adult.
                    if (current != null && state.canRemove(current)) {
                        Box {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(56.dp)) {
                                Icon(Icons.Outlined.MoreHoriz, contentDescription = stringResource(R.string.album_photo_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.album_photo_remove)) },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        confirmRemove = true
                                    },
                                    modifier = Modifier.heightIn(min = 56.dp),
                                )
                            }
                        }
                    }
                }
                // The photos on either side load too, so swiping on a weak network shows them at once.
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { photos[it].id }, beyondViewportPageCount = 1) { page ->
                    val photo = photos[page]
                    StorageImage(
                        path = photo.photoPath,
                        contentDescription = stringResource(R.string.album_photo_by, photo.authorName),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        showLoading = true,
                    )
                }
                Text(
                    stringResource(R.string.album_photo_position, pager.currentPage + 1, photos.size),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
                )
                val toRemove = current
                if (confirmRemove && toRemove != null) {
                    AlertDialog(
                        onDismissRequest = { confirmRemove = false },
                        title = { Text(stringResource(R.string.album_photo_remove_title)) },
                        text = { Text(stringResource(R.string.album_photo_remove_body)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    confirmRemove = false
                                    onRemove(toRemove)
                                },
                                modifier = Modifier.heightIn(min = 56.dp),
                            ) { Text(stringResource(R.string.album_photo_remove)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmRemove = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                                Text(stringResource(R.string.album_photo_keep))
                            }
                        },
                    )
                }
            }
        }
    }
}
