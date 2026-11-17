package fr.familia.app.core.album.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.album.previewAlbumPhotos
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.print.printFailedText
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import java.time.LocalDate
import fr.familia.app.core.ui.LoadingState

/** What the screen can ask, so the route stays short. */
data class PhotoBookActions(
    val onTitle: (String) -> Unit = {},
    val onFormat: (PhotoBookFormat) -> Unit = {},
    val onToggle: (String) -> Unit = {},
    val onMove: (id: String, by: Int) -> Unit = { _, _ -> },
    val onBig: (String) -> Unit = {},
    val onCover: (String) -> Unit = {},
    val onMake: () -> Unit = {},
)

/**
 * « Faire le livre photo » (récit 59): the title, the size of the pages, then every photo with its
 * box and, once chosen, « Plus tôt », « Plus tard », « En grand » and « En couverture ». The single
 * filled button is at the end. Stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoBookScreen(state: PhotoBookUiState, actions: PhotoBookActions, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.photo_book_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                PhotoBookUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                PhotoBookUiState.Empty -> EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = stringResource(R.string.photo_book_empty_title),
                    body = stringResource(R.string.photo_book_empty_body),
                )
                is PhotoBookUiState.Choosing -> Choosing(state, actions)
            }
        }
    }
}

@Composable
private fun Choosing(state: PhotoBookUiState.Choosing, actions: PhotoBookActions) {
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        item(key = "intro") {
            Text(stringResource(R.string.photo_book_intro), style = MaterialTheme.typography.bodyLarge)
        }
        item(key = "title") {
            OutlinedTextField(
                value = state.title,
                onValueChange = actions.onTitle,
                label = { Text(stringResource(R.string.photo_book_field_title)) },
                supportingText = { Text(state.dates, color = muted) },
                isError = state.title.isBlank(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "format") {
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                ClayCardTitle(stringResource(R.string.photo_book_format), style = MaterialTheme.typography.titleMedium)
                ChoiceRow(stringResource(R.string.photo_book_landscape), state.format == PhotoBookFormat.Landscape) { actions.onFormat(PhotoBookFormat.Landscape) }
                ChoiceRow(stringResource(R.string.photo_book_square), state.format == PhotoBookFormat.Square) { actions.onFormat(PhotoBookFormat.Square) }
            }
        }
        item(key = "count") {
            Text(
                pluralStringResource(R.plurals.photo_book_count, state.chosen.size, state.chosen.size, state.pageCount),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        itemsIndexed(state.photos, key = { _, it -> it.photo.id }) { index, photo ->
            PhotoRow(
                photo = photo,
                chosen = photo.photo.id in state.chosen,
                isCover = photo.photo.id == state.coverId,
                isFirst = index == 0,
                isLast = index == state.photos.lastIndex,
                actions = actions,
            )
        }
        item(key = "make") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.failed) {
                    Text(printFailedText(state.problem, R.string.photo_book_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = actions.onMake, enabled = state.canMake, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    if (state.isMaking) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(R.string.photo_book_make))
                    }
                }
                Text(stringResource(R.string.photo_book_hint), style = MaterialTheme.typography.bodyMedium, color = muted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoRow(photo: BookPhoto, chosen: Boolean, isCover: Boolean, isFirst: Boolean, isLast: Boolean, actions: PhotoBookActions) {
    val id = photo.photo.id
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .toggleable(value = chosen, role = Role.Checkbox, onValueChange = { actions.onToggle(id) }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(checked = chosen, onCheckedChange = null)
            StorageImage(
                path = photo.photo.thumbnailPath,
                contentDescription = stringResource(R.string.album_photo_by, photo.photo.authorName),
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(photo.caption, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val tags = listOfNotNull(
                    stringResource(R.string.photo_book_is_cover).takeIf { isCover },
                    stringResource(R.string.photo_book_is_big).takeIf { photo.big },
                )
                if (tags.isNotEmpty()) {
                    Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = FamiliaClay.colors.onTintVariant(ClayTint.Plain))
                }
            }
        }
        if (chosen) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallAction(stringResource(R.string.photo_book_earlier), enabled = !isFirst) { actions.onMove(id, -1) }
                SmallAction(stringResource(R.string.photo_book_later), enabled = !isLast) { actions.onMove(id, 1) }
                SmallAction(stringResource(if (photo.big) R.string.photo_book_small else R.string.photo_book_big)) { actions.onBig(id) }
                SmallAction(stringResource(R.string.photo_book_cover), enabled = !isCover) { actions.onCover(id) }
            }
        }
    }
}

/** « Faire le livre photo », in « Voir les photos » of a trip or an event, under the film. */
@Composable
fun PhotoBookButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Icon(Icons.Outlined.AutoStories, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.photo_book_open))
    }
}

@Composable
private fun SmallAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) { Text(label) }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@FamiliaPreviews
@Composable
private fun PhotoBookPreview() {
    val photos = previewAlbumPhotos(count = 3).photos.map { BookPhoto(it, bookCaption(it), big = it.slot == 2) }
    FamiliaTheme {
        Surface {
            PhotoBookScreen(
                PhotoBookUiState.Choosing(
                    photos = photos,
                    chosen = photos.map { it.photo.id }.toSet(),
                    coverId = photos.first().photo.id,
                    title = "Vacances à la mer",
                    dates = bookDates(LocalDate.of(2027, 8, 2), LocalDate.of(2027, 8, 9)),
                    steps = emptyList(),
                ),
                PhotoBookActions(),
                onBack = {},
            )
        }
    }
}
