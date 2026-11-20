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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.heading
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
                    state, onText, onAddPhotos, onRemovePhoto, onFor, onMentions, onSave, onForChallenge,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(
    state: GoodNewsEditUiState.Editing,
    onText: (String) -> Unit,
    onAddPhotos: (List<String>) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onFor: (String) -> Unit,
    onMentions: (Set<String>) -> Unit,
    onSave: () -> Unit,
    onForChallenge: (Boolean) -> Unit,
    littleOnes: ((Boolean) -> Unit)?,
    onCancelSending: () -> Unit,
) {
    val form = state.form
    val errors = state.errors
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // « @Prénom » in the text names a member (Kévin, 19/11). Members named before, by the old
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
            label = { Text(stringResource(R.string.goodnews_text_field)) },
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
        if (state.candidates.isNotEmpty()) {
            Text(stringResource(R.string.goodnews_mentions), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionTitle(stringResource(R.string.goodnews_photos))
        if (state.isNew) {
            NewPhotos(state, onAddPhotos, onRemovePhoto)
            if (GoodNewsFormError.NoPhoto in errors) {
                Text(stringResource(R.string.goodnews_error_no_photo), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            val existing = state.existing!!
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                existing.photoIds.forEachIndexed { index, id ->
                    StorageImage(existing.thumbnailPath(id), stringResource(R.string.goodnews_photo_number, index + 1), Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)))
                }
            }
            Text(stringResource(R.string.goodnews_photos_fixed), style = MaterialTheme.typography.bodyMedium)
        }

        if (state.isNew && state.targets.size > 1) {
            SectionTitle(stringResource(R.string.goodnews_for))
            MemberChips(state.targets, setOf(form.forMemberId), enabled = !state.isSaving, onClick = onFor) { member ->
                if (member.id == state.targets.first().id) stringResource(R.string.goodnews_for_me) else member.firstName
            }
        }

        if (littleOnes != null && state.isNew && form.forMemberId != state.targets.first().id) {
            SwitchRow(stringResource(R.string.goodnews_little_ones_switch), form.littleOnes, !state.isSaving, littleOnes)
        }

        val challenge = state.challenge
        if (state.isNew && challenge != null) {
            SwitchRow(
                stringResource(R.string.goodnews_for_challenge, challenge.emoji, challenge.title),
                form.forChallenge, !state.isSaving, onForChallenge,
            )
        }

        SendingProgress(state, onCancelSending)

        Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            if (state.isSaving) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    when (val photo = state.sendingPhoto) {
                        null, 0 -> stringResource(R.string.goodnews_saving)
                        else -> stringResource(R.string.goodnews_sending_photo, photo, form.photos.size)
                    },
                )
            } else {
                Text(stringResource(if (state.isNew) R.string.goodnews_publish_action else R.string.goodnews_save_action))
            }
        }
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
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.goodnews_cancel_sending))
            }
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
        OutlinedButton(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.heightIn(min = 56.dp),
        ) {
            Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (state.form.photos.isEmpty()) R.string.goodnews_add_photos else R.string.goodnews_add_more_photos))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberChips(
    members: List<Member>,
    selectedIds: Set<String>,
    enabled: Boolean,
    onClick: (String) -> Unit,
    label: @Composable (Member) -> String,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        members.forEach { member ->
            val selected = member.id in selectedIds
            FilterChip(
                selected = selected,
                onClick = { onClick(member.id) },
                enabled = enabled,
                label = { Text(label(member), style = MaterialTheme.typography.labelLarge) },
                leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                modifier = Modifier.heightIn(min = 56.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
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
