package fr.familia.app.core.album

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.FamiliaPreviews
import java.time.Instant

/**
 * All the photos of an album, newest first, with « Ajouter des photos » on top. [actionsBar] holds
 * the help button of the module. Stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    title: String,
    state: AlbumPhotosUiState,
    actions: PhotoActions,
    onBack: () -> Unit,
    /** Opened on this photo once it is there (« Il y a N ans », récit 36). */
    startPhotoId: String? = null,
    actionsBar: @Composable () -> Unit = {},
    top: @Composable () -> Unit = {},
) {
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    var startDone by rememberSaveable { mutableStateOf(startPhotoId == null) }
    LaunchedEffect(state.photos, startPhotoId) {
        if (!startDone && state.photos.any { it.id == startPhotoId }) {
            openedId = startPhotoId
            startDone = true
        }
    }
    val shape = RoundedCornerShape(16.dp)
    val open = stringResource(R.string.album_photo_open)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { actionsBar() },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 112.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = ClaySpacing.screen, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "top", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    top()
                    SlideshowButton(state.photos)
                    PhotoStatus(state, actions.onDismissProblem)
                    if (state.canAdd) AddPhotosButton(state.placesLeft, actions.onAdd, max = state.maxPerMember)
                }
            }
            items(state.photos, key = { it.id }) { photo ->
                StorageImage(
                    path = photo.thumbnailPath,
                    contentDescription = stringResource(R.string.album_photo_by, photo.authorName),
                    modifier = Modifier.aspectRatio(1f).clip(shape).clickable(onClickLabel = open) { openedId = photo.id },
                )
            }
        }
    }
    openedId?.let { id -> PhotoViewer(state, id, actions.onRemove, onClose = { openedId = null }) }
}

/** Fictional photos for previews and screenshots: the grey boxes stand for the photos. */
fun previewAlbumPhotos(folder: String = "trips/1", count: Int = 8, sending: Int = 0): AlbumPhotosUiState = AlbumPhotosUiState(
    photos = (1..count).map { n ->
        val author = if (n % 2 == 0) "a" to "Camille" else "b" to "Inès"
        AlbumPhoto(folder, author.first, author.second, n, Instant.parse("2027-08-02T10:00:00Z").plusSeconds(n * 60L))
    },
    currentMemberId = "a",
    canAdd = true,
    sending = sending,
)

@FamiliaPreviews
@Composable
private fun AlbumScreenPreview() {
    FamiliaTheme { Surface { AlbumScreen("Photos", previewAlbumPhotos(), PhotoActions(), {}) } }
}
