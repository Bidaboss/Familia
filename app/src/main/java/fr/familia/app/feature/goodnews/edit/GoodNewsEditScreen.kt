package fr.familia.app.feature.goodnews.edit

import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import fr.familia.app.core.ui.FamiliaTopBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.familia.app.R
import fr.familia.app.core.photo.keepPhotoAccess
import fr.familia.app.core.mention.MentionTextField
import fr.familia.app.core.mention.mentionedIds
import fr.familia.app.core.ui.pickGroups
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormOutlineButton
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.scrollToFirstError
import fr.familia.app.core.ui.form.PeopleRow
import fr.familia.app.core.ui.form.PersonChoice
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.feature.goodnews.previewGoodNewsFamily

/** Publish a good news, or change one's own (récit 32). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoodNewsEditScreen(
    state: GoodNewsEditUiState,
    onText: (String) -> Unit,
    onAddPhotos: (List<String>) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onFor: (String) -> Unit,
    onMentions: (Set<String>) -> Unit,
    onSave: () -> Unit,
    onDismissFailure: () -> Unit,
    onBack: () -> Unit,
    /** Links the good news to the challenge of a foyer (récit 35 bis). */
    onForChallenge: (Boolean) -> Unit = {},
    /** « Les petits » (récit 43), offered when the module is on and the news is for a child. */
    littleOnesOn: Boolean = false,
    onLittleOnes: (Boolean) -> Unit = {},
    /** « Annuler l'envoi », while the photos are on their way. */
    onCancelSending: () -> Unit = {},
) {
    val editing = state as? GoodNewsEditUiState.Editing
    val hasContent = editing != null && (editing.form.text.isNotBlank() || editing.form.photos.isNotEmpty())
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = hasContent && editing?.isSaving == false) { confirmLeave = true }
    Scaffold(
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(if (editing?.isNew == false) R.string.goodnews_edit_title else R.string.goodnews_publish_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    BackButton(onClick = { if (hasContent && editing?.isSaving == false) confirmLeave = true else if (editing?.isSaving != true) onBack() })
                },
            )
        },
        // The black button at the bottom (plan de refonte du 21/11); the photos in flight are told above it.
        bottomBar = {
            editing?.let {
                StickyActionBar(
                    stringResource(if (it.isNew) R.string.goodnews_publish_action else R.string.goodnews_save_action),
                    onSave,
                    busy = it.isSaving,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                GoodNewsEditUiState.Loading, GoodNewsEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                GoodNewsEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.Celebration,
                    title = stringResource(R.string.goodnews_missing_title),
                    body = stringResource(R.string.goodnews_missing_body),
                )
                is GoodNewsEditUiState.Editing -> Form(
                    state, onText, onAddPhotos, onRemovePhoto, onFor, onMentions, onForChallenge,
                    littleOnes = if (littleOnesOn) onLittleOnes else null,
                    onCancelSending = onCancelSending,
                )
            }
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.goodnews_leave_title)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.member_leave_keep)) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text(stringResource(R.string.member_leave_discard)) } },
        )
    }
    editing?.failure?.let { failure ->
        AlertDialog(
            onDismissRequest = onDismissFailure,
            title = { Text(stringResource(R.string.goodnews_action_failed_title)) },
            text = {
                Text(
                    stringResource(
                        when (failure) {
                            PublishFailure.PhotoUnreadable -> R.string.goodnews_photo_unreadable
                            PublishFailure.Failed -> R.string.goodnews_publish_failed
                        },
                    ),
                )
            },
            confirmButton = { TextButton(onClick = onDismissFailure) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun Form(
    state: GoodNewsEditUiState.Editing,
    onText: (String) -> Unit,
    onAddPhotos: (List<String>) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onFor: (String) -> Unit,
    onMentions: (Set<String>) -> Unit,
    onForChallenge: (Boolean) -> Unit,
    littleOnes: ((Boolean) -> Unit)?,
    onCancelSending: () -> Unit,
) {
    val form = state.form
    val errors = state.errors
    val accent = MaterialTheme.colorScheme.primary
    val scroll = rememberScrollState()
    // The screen goes to the first tile with an error (plan de refonte du 21/11).
    val firstError = when {
        GoodNewsFormError.TextMissing in errors || GoodNewsFormError.TextTooLong in errors -> GoodNewsTile.Text
        GoodNewsFormError.NoPhoto in errors -> GoodNewsTile.Photos
        else -> null
    }
    val tileTop = scrollToFirstError(scroll, errors, firstError)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).imePadding().padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        FormTile(Icons.Outlined.EditNote, stringResource(R.string.goodnews_text_field), tileTop(GoodNewsTile.Text), accent = accent) {
            // « @Prénom » in the text names a member (l'admin, 19/11). Members named before, by the old
            // checkboxes, are kept as they were.
            val named = state.candidates.filter { it.id in form.mentionIds }
            val legacy = remember(state.existing?.id) { form.mentionIds - mentionedIds(form.text, named) }
            var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(form.text, TextRange(form.text.length))) }
            val value = if (field.text == form.text) field else TextFieldValue(form.text, TextRange(form.text.length))
            MentionTextField(
                value = value,
                onChange = { next, stillNamed ->
                    field = next
                    if (next.text != form.text) onText(next.text)
                    val ids = legacy + stillNamed.map { it.id }
                    if (ids != form.mentionIds) onMentions(ids)
                },
                groups = state.mentionGroups,
                chosen = named,
                label = null,
                placeholder = { Text(stringResource(R.string.goodnews_text_hint)) },
                isError = GoodNewsFormError.TextMissing in errors || GoodNewsFormError.TextTooLong in errors,
                supportingText = {
                    val error = when {
                        GoodNewsFormError.TextMissing in errors -> stringResource(R.string.goodnews_error_text_missing)
                        GoodNewsFormError.TextTooLong in errors -> stringResource(R.string.goodnews_error_text_too_long, GoodNews.TEXT_MAX)
                        else -> null
                    }
                    Text(error ?: stringResource(R.string.goodnews_text_count, form.text.length, GoodNews.TEXT_MAX))
                },
                minLines = 3,
                enabled = !state.isSaving,
            )
            if (state.candidates.isNotEmpty()) FormHint(stringResource(R.string.goodnews_mentions))
        }

        FormTile(Icons.Outlined.PhotoLibrary, stringResource(R.string.goodnews_photos), tileTop(GoodNewsTile.Photos), accent = accent) {
            if (state.isNew) {
                NewPhotos(state, onAddPhotos, onRemovePhoto)
                if (GoodNewsFormError.NoPhoto in errors) FormHint(stringResource(R.string.goodnews_error_no_photo), isError = true)
            } else {
                val existing = state.existing!!
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    existing.photoIds.forEachIndexed { index, id ->
                        StorageImage(existing.thumbnailPath(id), stringResource(R.string.goodnews_photo_number, index + 1), Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)))
                    }
                }
                FormHint(stringResource(R.string.goodnews_photos_fixed))
            }
        }

        if (state.isNew && state.targets.size > 1) {
            FormTile(Icons.Outlined.Face, stringResource(R.string.goodnews_for), accent = accent) {
                val me = state.targets.first().id
                PeopleRow(
                    choices = state.targets.map { member ->
                        PersonChoice(
                            key = member.id,
                            label = if (member.id == me) stringResource(R.string.goodnews_for_me) else member.firstName,
                            selected = member.id == form.forMemberId,
                            memberId = member.id,
                        )
                    },
                    multiple = false,
                    onToggle = { if (!state.isSaving) onFor(it) },
                )
            }
        }

        val showLittleOnes = littleOnes != null && state.isNew && form.forMemberId != state.targets.first().id
        val challenge = state.challenge?.takeIf { state.isNew }
        if (showLittleOnes || challenge != null) {
            FormTile(Icons.Outlined.Tune, stringResource(R.string.goodnews_tile_more), accent = accent) {
                if (showLittleOnes && littleOnes != null) {
                    SwitchRow(stringResource(R.string.goodnews_little_ones_switch), form.littleOnes, !state.isSaving, littleOnes)
                }
                if (challenge != null) {
                    SwitchRow(
                        stringResource(R.string.goodnews_for_challenge, challenge.emoji, challenge.title),
                        form.forChallenge, !state.isSaving, onForChallenge,
                    )
                }
            }
        }

        SendingProgress(state, onCancelSending)
    }
}

/**
 * While the photos go: a bar that fills, a line when the network is gone (the send waits and
 * goes on by itself), and « Annuler l'envoi » to get the form back.
 */
@Composable
private fun SendingProgress(state: GoodNewsEditUiState.Editing, onCancel: () -> Unit) {
    val photo = state.sendingPhoto ?: return
    if (!state.isNew) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (photo == 0) stringResource(R.string.goodnews_saving) else stringResource(R.string.goodnews_sending_photo, photo, state.form.photos.size),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        val progress = state.progress
        if (progress == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        if (state.offline) {
            Text(
                stringResource(R.string.goodnews_waiting_network),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (photo > 0) {
            FormOutlineButton(stringResource(R.string.goodnews_cancel_sending), onCancel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewPhotos(state: GoodNewsEditUiState.Editing, onAddPhotos: (List<String>) -> Unit, onRemovePhoto: (String) -> Unit) {
    // The Android photo picker: no permission to ask, and it works on every phone of the family.
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(GoodNews.PHOTOS_MAX)) { uris ->
        keepPhotoAccess(context, uris)
        onAddPhotos(uris.map { it.toString() })
    }
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
    if (state.canAddPhotos && !state.isSaving) {
        FormOutlineButton(
            stringResource(if (state.form.photos.isEmpty()) R.string.goodnews_add_photos else R.string.goodnews_add_more_photos),
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            icon = Icons.Outlined.AddAPhoto,
        )
    }
}

/** Fictional data for previews and screenshots. */
internal fun previewGoodNewsEdit(errors: Set<GoodNewsFormError> = emptySet()): GoodNewsEditUiState.Editing {
    val family = previewGoodNewsFamily()
    val me = family.members.first()
    return GoodNewsEditUiState.Editing(
        form = GoodNewsForm(text = "Noé a fait ses premiers tours de vélo !", forMemberId = "noe", mentionIds = setOf("jules")),
        existing = null,
        targets = listOf(me, family.members.first { it.id == "noe" }),
        candidates = family.members.filter { it.id != me.id },
        mentionGroups = pickGroups(family.members.filter { it.id != me.id }, family, me.householdId),
        errors = errors,
    )
}

@FamiliaPreviews
@Composable
private fun GoodNewsEditScreenPreview() {
    FamiliaTheme { Surface { GoodNewsEditScreen(previewGoodNewsEdit(setOf(GoodNewsFormError.NoPhoto)), {}, {}, {}, {}, {}, {}, {}, {}) } }
}

/** A line of 56 dp that the whole width switches on and off. */
@Composable
private fun SwitchRow(text: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

private enum class GoodNewsTile { Text, Photos }
