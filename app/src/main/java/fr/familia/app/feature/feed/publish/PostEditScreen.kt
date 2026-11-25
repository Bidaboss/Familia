package fr.familia.app.feature.feed.publish

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormOutlineButton
import fr.familia.app.core.ui.form.StickyActionBar

/**
 * « Publier dans la gazette » (récit 117, fiche M1): one screen, a word, « 📷 Ajouter une photo »,
 * « ＠ Mentionner » (a « @ » typed in the text proposes the members too). « Publier » is on as soon
 * as there is a word or a photo. « Modifier le texte » of one's own publication (fiche R4) is the
 * same screen, its photos fixed. Stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onBack: () -> Unit,
) {
    val editing = state as? PostEditUiState.Editing
    val changed = editing != null && (
        if (editing.isNew) editing.form.text.isNotBlank() || editing.form.photos.isNotEmpty() else editing.form.text != editing.existing?.text
        )
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = changed && editing?.isSaving == false) { confirmLeave = true }
    Scaffold(
        topBar = {
            FamiliaTopBar(
                title = {
                    Text(
                        stringResource(if (editing?.isNew == false) R.string.gazette_post_edit_title else R.string.gazette_post_title),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    BackButton(onClick = { if (changed && editing?.isSaving == false) confirmLeave = true else if (editing?.isSaving != true) onBack() })
                },
            )
        },
        bottomBar = {
            editing?.let {
                StickyActionBar(
                    stringResource(if (it.isNew) R.string.gazette_post_publish else R.string.gazette_post_save),
                    onSave,
                    enabled = it.canPublish,
                    busy = it.isSaving,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                PostEditUiState.Loading, PostEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                PostEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.EditNote,
                    title = stringResource(R.string.gazette_post_missing_title),
                    body = stringResource(R.string.gazette_post_missing_body),
                )
                is PostEditUiState.Editing -> Form(state, onText, onAddPhotos, onRemovePhoto, onMentions, onCancelSending)
            }
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.gazette_post_leave_title)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.member_leave_keep)) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text(stringResource(R.string.member_leave_discard)) } },
        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(
    state: PostEditUiState.Editing,
    onText: (String) -> Unit,
    onAddPhotos: (List<String>) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onMentions: (Set<String>) -> Unit,
    onCancelSending: () -> Unit,
) {
    val form = state.form
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        // « Alix · Toute la famille le verra » (fiche M1).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MemberAvatar(state.me, size = 48.dp)
            Column {
                Text(state.me.firstName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            placeholder = { Text(stringResource(R.string.gazette_post_hint, state.me.firstName)) },
            isError = PostFormError.TextTooLong in state.errors,
            supportingText = { Text(stringResource(R.string.goodnews_text_count, form.text.length, GazettePost.TEXT_MAX)) },
            minLines = 4,
            maxLength = GazettePost.TEXT_MAX,
            enabled = !state.isSaving,
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

        if (!state.isSaving) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.canAddPhotos) {
                    val context = LocalContext.current
                    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(GazettePost.PHOTOS_MAX)) { uris ->
                        keepPhotoAccess(context, uris)
                        onAddPhotos(uris.map { it.toString() })
                    }
                    FormOutlineButton(
                        stringResource(R.string.gazette_post_add_photo),
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )
                }
                if (state.candidates.isNotEmpty()) {
                    // « ＠ Mentionner »: a « @ » at the cursor, and the field proposes the members.
                    FormOutlineButton(
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
                {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
    }
}
