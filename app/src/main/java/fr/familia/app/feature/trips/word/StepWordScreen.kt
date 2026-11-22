package fr.familia.app.feature.trips.word

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.TripWord
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.album.previewAlbumPhotos
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.trips.detail.previewTripDetail
import java.time.Instant
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.form.FormDeleteButton
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.formatTime
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.core.ui.frenchDateTime
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.trips.TripFormScaffold
import fr.familia.app.feature.trips.detail.TripPathColors
import fr.familia.app.feature.trips.joinNames
import fr.familia.app.feature.trips.stepTypeIcon
import fr.familia.app.feature.trips.stepTypeLabel

/** What the page of a word can ask for. The defaults do nothing, for previews and tests. */
data class StepWordActions(
    val onText: (String) -> Unit = {},
    val onTogglePhoto: (String) -> Unit = {},
    val onPublish: () -> Unit = {},
    val onRemove: () -> Unit = {},
    val onHeart: () -> Unit = {},
    val onEditStep: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * « Le mot de l'étape » (récit 88, variante A): one step with its word. A participant writes it, picks
 * up to 4 photos of the album and publishes; a follower reads it and puts a heart. Stateless.
 */
@Composable
fun StepWordScreen(state: StepWordUiState, actions: StepWordActions, comments: @Composable () -> Unit = {}) {
    val ready = state as? StepWordUiState.Ready
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val hasChanges = ready?.canWrite == true && ready.hasChanges
    BackHandler(enabled = hasChanges) { confirmLeave = true }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.trip_leave_title)) },
            text = { Text(stringResource(R.string.trip_leave_body)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.trip_leave_keep)) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false; actions.onBack() }) { Text(stringResource(R.string.member_leave_discard)) } },
        )
    }
    TripFormScaffold(
        title = stringResource(R.string.word_title),
        help = ScreenHelp.TripDetail,
        onBack = { if (hasChanges) confirmLeave = true else actions.onBack() },
        action = ready?.takeIf { it.canWrite }?.let { stringResource(if (it.word == null) R.string.word_publish else R.string.member_save) },
        busy = ready?.busy == true,
        onAction = { if (ready?.canPublish == true) actions.onPublish() },
    ) {
        when (state) {
            StepWordUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
            StepWordUiState.Missing -> EmptyState(Icons.Outlined.CardTravel, stringResource(R.string.trip_missing_title), stringResource(R.string.trip_missing_body))
            StepWordUiState.Error -> EmptyState(
                icon = Icons.Outlined.CardTravel,
                title = stringResource(R.string.trips_error_title),
                body = stringResource(R.string.trips_error_body),
                action = { Button(onClick = actions.onRetry, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_retry)) } },
            )
            is StepWordUiState.Ready -> WordContent(state, actions, comments)
        }
    }
}

@Composable
private fun WordContent(state: StepWordUiState.Ready, actions: StepWordActions, comments: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StepRecap(state, actions.onEditStep)
        if (state.sendFailed) {
            Text(stringResource(R.string.word_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (state.canWrite) WriteWord(state, actions) else ReadWord(state, actions)
        // The comments come under a published word only (the rules check the same).
        if (state.word != null && !state.busy) comments()
    }
}

/** The step the word is about: its type, place, rank and day, « Bien arrivé ». */
@Composable
private fun StepRecap(state: StepWordUiState.Ready, onEditStep: () -> Unit) {
    val step = state.step
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(40.dp).background(TripPathColors.Button, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(stepTypeIcon(step.type), contentDescription = null, tint = TripPathColors.InkSoft)
                }
                Column(Modifier.weight(1f)) {
                    Text(step.place, style = MaterialTheme.typography.titleLarge, color = TripPathColors.Ink)
                    val time = step.departTime?.let { " · " + formatTime(it) }.orEmpty()
                    Text(
                        stringResource(R.string.trip_map_rank, state.rank, state.total) + " · " + frenchDate(step.departDate, "EEE d MMM") + time,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TripPathColors.InkSoft,
                    )
                }
            }
            Text(stepTypeLabel(step.type), style = MaterialTheme.typography.bodyLarge, color = TripPathColors.Ink)
            step.arrival?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.trip_arrived_by, it.name), style = MaterialTheme.typography.bodyMedium, color = TripPathColors.InkSoft)
                }
            }
            if (state.isParticipant) {
                TextButton(
                    onClick = onEditStep,
                    colors = ButtonDefaults.textButtonColors(contentColor = TripPathColors.Ink),
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.word_edit_step), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun WriteWord(state: StepWordUiState.Ready, actions: StepWordActions) {
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.word_remove_title)) },
            text = { Text(stringResource(R.string.word_remove_body)) },
            confirmButton = { TextButton(onClick = { confirmRemove = false; actions.onRemove() }) { Text(stringResource(R.string.word_remove_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.word_remove_keep)) } },
        )
    }
    FormTile(Icons.Outlined.Edit, stringResource(R.string.word_your_word)) {
        FormHint(stringResource(R.string.word_your_word_hint))
        OutlinedTextField(
            value = state.draft.text,
            onValueChange = actions.onText,
            label = { Text(stringResource(R.string.word_field_label)) },
            minLines = 4,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.word_counter, state.draft.text.length, TripWord.MAX_TEXT),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.End),
        )
    }
    FormTile(Icons.Outlined.PhotoLibrary, stringResource(R.string.word_photos_title)) {
        if (state.album.isEmpty()) {
            FormHint(stringResource(R.string.word_photos_empty))
        } else {
            FormHint(stringResource(R.string.word_photos_hint, TripWord.MAX_PHOTOS))
            state.album.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { photo ->
                        val rank = state.draft.photoIds.indexOf(photo.id) + 1
                        PickPhoto(photo, rank, Modifier.weight(1f)) { actions.onTogglePhoto(photo.id) }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text(
                pluralStringResource(R.plurals.word_photos_chosen, state.draft.photoIds.size, state.draft.photoIds.size, TripWord.MAX_PHOTOS),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    FormTile(Icons.Outlined.Groups, stringResource(R.string.word_who_title)) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
            Text(
                pluralStringResource(R.plurals.word_who_line, state.followers, state.followers),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(12.dp),
            )
        }
        FormHint(stringResource(R.string.word_who_hint))
    }
    if (state.word != null) FormDeleteButton(stringResource(R.string.word_remove), onClick = { confirmRemove = true })
}

/** One photo of the album to pick: its number in the order chosen, in a black dot, once chosen. */
@Composable
private fun PickPhoto(photo: AlbumPhoto, rank: Int, modifier: Modifier, onClick: () -> Unit) {
    val chosen = rank > 0
    val label = if (chosen) stringResource(R.string.word_photo_chosen, rank) else stringResource(R.string.word_photo_pick)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .then(if (chosen) Modifier.border(BorderStroke(3.dp, TripPathColors.Ink), RoundedCornerShape(12.dp)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; selected = chosen },
    ) {
        StorageImage(photo.thumbnailPath, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (chosen) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).background(TripPathColors.Ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(rank.toString(), color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadWord(state: StepWordUiState.Ready, actions: StepWordActions) {
    val word = state.word
    if (word == null) {
        Text(stringResource(R.string.word_empty_follower), style = MaterialTheme.typography.bodyLarge, color = TripPathColors.Ink)
        return
    }
    FormTile(Icons.Outlined.FavoriteBorder, stringResource(R.string.word_title)) {
        Text(word.authorName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        word.createdAt?.let { Text(frenchDateTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        // A long press is a shortcut for the heart; the button under it stays, for whoever does not know it.
        Text(
            word.text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = {},
                onLongClick = actions.onHeart,
                onLongClickLabel = stringResource(if (state.hearted) R.string.word_heart_take_short else R.string.word_heart_put_short),
            ),
        )
        WordPhotos(word.photosIn(state.album))
        HeartRow(state, actions.onHeart)
        FormHint(stringResource(R.string.word_heart_hint))
    }
}

/** One photo large, or two side by side, or one large and two small, or four in a square. */
@Composable
private fun WordPhotos(photos: List<AlbumPhoto>) {
    if (photos.isEmpty()) return
    val shape = RoundedCornerShape(12.dp)
    @Composable
    fun Photo(photo: AlbumPhoto, modifier: Modifier) =
        StorageImage(photo.thumbnailPath, contentDescription = null, modifier = modifier.clip(shape), contentScale = ContentScale.Crop)
    when (photos.size) {
        1 -> Photo(photos[0], Modifier.fillMaxWidth().aspectRatio(4f / 3f))
        2 -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { photos.forEach { Photo(it, Modifier.weight(1f).aspectRatio(1f)) } }
        3 -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Photo(photos[0], Modifier.weight(2f).aspectRatio(1f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Photo(photos[1], Modifier.fillMaxWidth().aspectRatio(1f))
                Photo(photos[2], Modifier.fillMaxWidth().aspectRatio(1f))
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            photos.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { Photo(it, Modifier.weight(1f).aspectRatio(1f)) } }
            }
        }
    }
}

/** The heart button (112 × 56 dp, with the number), then the faces and the names of those who put one. */
@Composable
private fun HeartRow(state: StepWordUiState.Ready, onHeart: () -> Unit) {
    val count = state.word?.heartIds?.size ?: 0
    val label = stringResource(if (state.hearted) R.string.word_heart_take else R.string.word_heart_put, count)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier
                .size(112.dp, 56.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                .clickable(role = Role.Button, onClick = onHeart)
                .clearAndSetSemantics { contentDescription = label; role = Role.Button },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (state.hearted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(8.dp))
            Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (state.hearts.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                state.hearts.take(4).forEach { MemberAvatar(it, size = 32.dp) }
            }
            Text(heartNames(state.hearts), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun heartNames(hearts: List<Member>): String {
    val names = hearts.map { it.firstName }
    return if (names.size <= 3) joinNames(names) else pluralStringResource(R.plurals.word_hearts_more, names.size - 2, names[0], names[1], names.size - 2)
}

/** Fictional word for previews and screenshots: [canWrite] for a participant, [withWord] with 3 photos and 5 hearts. */
internal fun previewStepWord(canWrite: Boolean, withWord: Boolean, isParticipant: Boolean = canWrite): StepWordUiState.Ready {
    val trip = previewTripDetail()
    val album = previewAlbumPhotos().photos
    val family = listOf("Camille", "Inès", "Noé", "Lou", "Jules").mapIndexed { i, name ->
        Member("m$i", name, null, null, "h1", MemberProfile.Adult, false, emptyList(), null)
    }
    val word = TripWord(
        stepId = trip.steps[0].id,
        text = "Arrivés à Ajaccio ! Le vol était à l'heure et Noé a dormi tout le trajet. Premier café sur le port.",
        photoIds = album.take(3).map { it.id },
        authorId = "a",
        authorName = "Camille",
        heartIds = family.map { it.id }.toSet(),
        createdAt = Instant.parse("2027-08-02T07:52:00Z"),
    )
    val saved = if (withWord) word else null
    return StepWordUiState.Ready(
        step = trip.steps[0],
        rank = 1,
        total = trip.steps.size,
        word = saved,
        album = album,
        followers = 11,
        currentMemberId = "m1",
        canWrite = canWrite,
        isParticipant = isParticipant,
        draft = WordDraft(saved?.text.orEmpty(), saved?.photoIds.orEmpty()),
        hasChanges = false,
        hearts = if (withWord) family else emptyList(),
    )
}

@FamiliaPreviews
@Composable
private fun StepWordWritePreview() {
    FamiliaTheme { Surface { StepWordScreen(previewStepWord(canWrite = true, withWord = true), StepWordActions()) } }
}

@FamiliaPreviews
@Composable
private fun StepWordReadPreview() {
    FamiliaTheme { Surface { StepWordScreen(previewStepWord(canWrite = false, withWord = true), StepWordActions()) } }
}
