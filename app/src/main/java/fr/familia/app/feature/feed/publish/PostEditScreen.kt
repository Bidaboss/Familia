package fr.familia.app.feature.feed.publish

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.familia.app.R
import fr.familia.app.core.mention.MentionTextField
import fr.familia.app.core.model.GazettePost
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.photo.keepPhotoAccess
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormOutlineButton

/** The brown of « Publier » and of the texts of the sheet (prototype, fiche M1). */
internal val SheetBrown = Color(0xFF3A2A1A)

/** The cream of the sheet, and the light outline of its field and its pills. */
internal val SheetCream = Color(0xFFFFFAF2)
private val SheetLine = Color(0xFFE8DFD0)

/** « Publier », grey while there is nothing to publish. */
private val PublishOff = Color(0xFFA89F94)

/** The count of the characters shows from 900 of the 1 000 (fiche M1, tranché le 26/11). */
internal const val COUNT_SHOWN_FROM = 900

/** Whether leaving now would lose what was written. */
fun hasChanges(state: PostEditUiState): Boolean {
    val editing = state as? PostEditUiState.Editing ?: return false
    return if (editing.isNew) {
        editing.form.text.isNotBlank() || editing.form.photos.isNotEmpty() || editing.form.place != null
    } else {
        editing.form.text != editing.existing?.text
    }
}

/**
 * « Publier dans la gazette » (récit 117, fiche M1): the content of the sheet that rises over the
 * gazette (prototype, retour du 26/11: never a page of its own). Its title and ✕, a word, « 📷 Ajouter
 * une photo », « 📍 Dire où je suis », « ＠ Mentionner » (a « @ » typed in the text proposes the members
 * too), then « Publier » across the bottom, on as soon as there is a word or a photo. « Modifier le
 * texte » of one's own publication (fiche R4) is the same sheet, filled, its photos fixed. Stateless.
 */
@Composable
fun PostEditScreen(
    state: PostEditUiState,
    onText: (String) -> Unit,
    onAddPhotos: (List<String>) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onMentions: (Set<String>) -> Unit,
    onSave: () -> Unit,
    onDismissFailure: () -> Unit,
    onCancelSending: () -> Unit,
    /** The ✕: the sheet asks first when something would be lost. */
    onClose: () -> Unit,
    /** « 📍 Dire où je suis » (récit 117); null: no position on this phone. */
    onLocate: (() -> Unit)? = null,
    onRemovePlace: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val editing = state as? PostEditUiState.Editing
    Column(modifier.fillMaxWidth().imePadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = ClaySpacing.screen, end = 4.dp),
        ) {
            Text(
                stringResource(if (editing?.isNew == false) R.string.gazette_post_edit_title else R.string.gazette_post_title),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                color = SheetBrown,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = { if (editing?.isSaving != true) onClose() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.gazette_post_close), tint = SheetBrown)
            }
        }
        Box(Modifier.weight(1f, fill = false)) {
            when (state) {
                PostEditUiState.Loading, PostEditUiState.Done -> Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                PostEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.EditNote,
                    title = stringResource(R.string.gazette_post_missing_title),
                    body = stringResource(R.string.gazette_post_missing_body),
                )
                is PostEditUiState.Editing -> Form(state, onText, onAddPhotos, onRemovePhoto, onMentions, onCancelSending, onLocate, onRemovePlace)
            }
        }
        editing?.let {
            // « Publier » across the bottom of the sheet (56 dp, radius 28): grey while empty, then brown.
            Button(
                onClick = onSave,
                enabled = it.canPublish && !it.isSaving,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SheetBrown,
                    contentColor = Color.White,
                    disabledContainerColor = PublishOff,
                    disabledContentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ClaySpacing.screen, vertical = 12.dp)
                    .heightIn(min = 56.dp),
            ) {
                if (it.isSaving) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(if (it.isNew) R.string.gazette_post_publish else R.string.gazette_post_save),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
    editing?.failure?.let { failure ->
        AlertDialog(
            onDismissRequest = onDismissFailure,
            title = { Text(stringResource(R.string.gazette_post_failed_title)) },
            text = {
                Text(
                    stringResource(
                        when (failure) {
                            PostFailure.PhotoUnreadable -> R.string.goodnews_photo_unreadable
                            PostFailure.Failed -> R.string.gazette_post_failed
                        },
                    ),
                )
            },
            confirmButton = { TextButton(onClick = onDismissFailure) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

/** A pill of the sheet (prototype): light outline of 2 dp, brown bold text, 56 dp high. */
@Composable
private fun SheetPill(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(2.dp, SheetLine),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = SheetBrown),
        modifier = Modifier.heightIn(min = 56.dp),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(
    state: PostEditUiState.Editing,
    onText: (String) -> Unit,
    onAddPhotos: (List<String>) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onMentions: (Set<String>) -> Unit,
    onCancelSending: () -> Unit,
    onLocate: (() -> Unit)?,
    onRemovePlace: () -> Unit,
) {
    val form = state.form
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = ClaySpacing.screen, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        // « Alix · Toute la famille le verra » (fiche M1).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MemberAvatar(state.me, size = 40.dp)
            Column {
                Text(state.me.firstName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SheetBrown)
                Text(stringResource(R.string.gazette_post_audience), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val named = state.candidates.filter { it.id in form.mentionIds }
        var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(form.text, TextRange(form.text.length))) }
        val value = if (field.text == form.text) field else TextFieldValue(form.text, TextRange(form.text.length))
        MentionTextField(
            value = value,
            onChange = { next, stillNamed ->
                field = next
                if (next.text != form.text) onText(next.text)
                val ids = stillNamed.map { it.id }.toSet()
                if (ids != form.mentionIds) onMentions(ids)
            },
            groups = state.mentionGroups,
            chosen = named,
            label = null,
            placeholder = { Text(stringResource(R.string.gazette_post_hint)) },
            isError = PostFormError.TextTooLong in state.errors,
            supportingText = if (form.text.length >= COUNT_SHOWN_FROM) {
                { Text(stringResource(R.string.goodnews_text_count, form.text.length, GazettePost.TEXT_MAX)) }
            } else {
                null
            },
            minLines = 4,
            maxLength = GazettePost.TEXT_MAX,
            enabled = !state.isSaving,
            // Prototype: rounded (24 dp), white, a light outline of 2 dp.
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                disabledContainerColor = Color.White,
                unfocusedBorderColor = SheetLine,
                focusedBorderColor = SheetBrown,
            ),
        )
        if (PostFormError.Empty in state.errors) FormHint(stringResource(R.string.gazette_post_error_empty), isError = true)

        if (state.isNew) {
            NewPhotos(state, onRemovePhoto)
        } else {
            val existing = state.existing!!
            if (existing.photoIds.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    existing.photoIds.forEachIndexed { index, id ->
                        StorageImage(existing.thumbnailPath(id), stringResource(R.string.goodnews_photo_number, index + 1), Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)))
                    }
                }
                FormHint(stringResource(R.string.gazette_post_photos_fixed))
            }
        }

        PlaceRow(state, onRemovePlace)

        if (!state.isSaving) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.canAddPhotos) {
                    val context = LocalContext.current
                    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(GazettePost.PHOTOS_MAX)) { uris ->
                        keepPhotoAccess(context, uris)
                        onAddPhotos(uris.map { it.toString() })
                    }
                    SheetPill(
                        stringResource(R.string.gazette_post_add_photo),
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )
                }
                if (state.isNew && onLocate != null && form.place == null && !state.locating) {
                    SheetPill(stringResource(R.string.gazette_post_locate), onClick = onLocate)
                }
                if (state.candidates.isNotEmpty()) {
                    // « ＠ Mentionner »: a « @ » at the cursor, and the field proposes the members.
                    SheetPill(
                        stringResource(R.string.gazette_post_mention),
                        onClick = {
                            val at = value.selection.start.coerceIn(0, value.text.length)
                            val before = value.text.substring(0, at)
                            val insert = if (before.isEmpty() || before.last().isWhitespace()) "@" else " @"
                            val text = before + insert + value.text.substring(at)
                            if (text.length <= GazettePost.TEXT_MAX) {
                                field = TextFieldValue(text, TextRange(at + insert.length))
                                onText(text)
                            }
                        },
                    )
                }
            }
        }
        SendingProgress(state, onCancelSending)
    }
}

/** « 📍 Annecy · Haute-Savoie — Ta position, partagée 2 h avec la famille   Retirer » (fiche R1). */
@Composable
private fun PlaceRow(state: PostEditUiState.Editing, onRemove: () -> Unit) {
    val place = state.form.place ?: state.existing?.place
    when {
        state.locating -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(R.string.gazette_post_locating), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        place != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("📍", style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f)) {
                Text(place.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(if (state.isNew) R.string.gazette_post_place_hint else R.string.gazette_post_place_fixed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isNew && !state.isSaving) {
                TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.gazette_post_place_remove)) }
            }
        }
        state.placeProblem != null -> FormHint(
            stringResource(
                when (state.placeProblem) {
                    PlaceProblem.NoPermission -> R.string.gazette_post_place_no_permission
                    PlaceProblem.NoPosition -> R.string.gazette_post_place_none
                },
            ),
            isError = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewPhotos(state: PostEditUiState.Editing, onRemovePhoto: (String) -> Unit) {
    if (state.form.photos.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.form.photos.forEachIndexed { index, uri ->
            Box(Modifier.size(96.dp)) {
                AsyncImage(
                    model = uri,
                    contentDescription = stringResource(R.string.goodnews_photo_number, index + 1),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                if (!state.isSaving) {
                    FilledTonalIconButton(onClick = { onRemovePhoto(uri) }, modifier = Modifier.align(Alignment.TopEnd).size(56.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.goodnews_photo_remove, index + 1))
                    }
                }
            }
        }
    }
    Text(
        stringResource(R.string.gazette_post_photo_count, state.form.photos.size, GazettePost.PHOTOS_MAX),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** While the photos go: a bar that fills, a line without network, and « Annuler l'envoi ». */
@Composable
private fun SendingProgress(state: PostEditUiState.Editing, onCancel: () -> Unit) {
    val photo = state.sendingPhoto ?: return
    if (!state.isNew) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (photo == 0) stringResource(R.string.goodnews_saving) else stringResource(R.string.goodnews_sending_photo, photo, state.form.photos.size),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        val progress = state.progress
        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        if (state.offline) {
            Text(stringResource(R.string.goodnews_waiting_network), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (photo > 0) FormOutlineButton(stringResource(R.string.goodnews_cancel_sending), onCancel)
    }
}

@FamiliaPreviews
@Composable
private fun PostEditScreenPreview() {
    val family = fr.familia.app.feature.goodnews.previewGoodNewsFamily()
    val me = family.members.first()
    val others = family.members.drop(1)
    FamiliaTheme {
        Surface {
            PostEditScreen(
                PostEditUiState.Editing(PostForm(text = "Belle journée au parc"), null, me, others),
                {}, {}, {}, {}, {}, {}, {}, onClose = {},
            )
        }
    }
}
