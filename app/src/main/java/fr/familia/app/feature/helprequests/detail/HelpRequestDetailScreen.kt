package fr.familia.app.feature.helprequests.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.familia.app.R
import fr.familia.app.core.mention.MentionText
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.HelpKind
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.HelpStatus
import fr.familia.app.core.model.VOLUNTEER_NOTE_MAX
import fr.familia.app.core.photo.SinglePhoto
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.place.DirectionsButton
import fr.familia.app.core.share.ShareButton
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.ui.PendingSign
import fr.familia.app.core.ui.form.FormDeleteButton
import fr.familia.app.core.ui.form.FormOutlineButton
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.feature.events.placeView
import fr.familia.app.feature.helprequests.entraideAccent
import fr.familia.app.feature.helprequests.kindLabel
import fr.familia.app.feature.helprequests.whenLabel
import java.time.LocalDate
import java.time.LocalTime

/** One « coup de main » and what I can do with it. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpRequestDetailScreen(
    state: HelpRequestDetailUiState,
    onTake: (note: String) -> Unit,
    onDecline: () -> Unit,
    onWithdraw: () -> Unit,
    onThank: () -> Unit,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    onSetNote: (note: String) -> Unit = {},
    /** « Partager » (récit 31): for whoever manages the request, while it is open. */
    onShare: (() -> Unit)? = null,
) {
    var taking by rememberSaveable { mutableStateOf(false) }
    val success = state as? HelpRequestDetailUiState.Success
    Scaffold(
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.helprequest_detail_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    onShare?.let { ShareButton(onClick = it) }
                    if ((state as? HelpRequestDetailUiState.Success)?.canManage == true) {
                        TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.event_detail_edit), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
            )
        },
        // The one thing to do here, in the black button (plan de refonte du 21/11).
        bottomBar = {
            when {
                success == null -> Unit
                success.canVolunteer -> StickyActionBar(stringResource(R.string.helprequest_take), onClick = { taking = true }, enabled = !success.isBusy)
                success.canThank -> StickyActionBar(
                    stringResource(R.string.helprequest_thank, success.request.volunteerName.orEmpty()),
                    onThank,
                    enabled = !success.isBusy,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                HelpRequestDetailUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                HelpRequestDetailUiState.Missing -> EmptyState(
                    icon = Icons.Outlined.VolunteerActivism,
                    title = stringResource(R.string.helprequest_missing_title),
                    body = stringResource(R.string.helprequest_missing_body),
                    action = {
                        Button(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.helprequest_back_to_list))
                        }
                    },
                )
                is HelpRequestDetailUiState.Success ->
                    Content(state, onDecline, onWithdraw, onCancel, onEdit, onDuplicate, onSetNote)
            }
        }
    }
    if (taking && success != null) {
        NoteDialog(
            title = stringResource(R.string.helprequest_take_title),
            label = stringResource(R.string.helprequest_note_label, success.request.authorName),
            initial = "",
            confirm = stringResource(R.string.helprequest_take),
            onConfirm = { taking = false; onTake(it) },
            onDismiss = { taking = false },
        )
    }
    val error = (state as? HelpRequestDetailUiState.Success)?.error
    if (error != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(R.string.helprequest_error_dialog_title)) },
            text = {
                Text(
                    when (error) {
                        is HelpActionError.AlreadyTaken ->
                            error.volunteerName?.let { stringResource(R.string.helprequest_error_already_taken, it) }
                                ?: stringResource(R.string.helprequest_error_not_open)
                        HelpActionError.NeedsNetwork -> stringResource(R.string.helprequest_error_needs_network)
                        HelpActionError.Failed -> stringResource(R.string.event_save_failed)
                    },
                )
            },
            confirmButton = { TextButton(onClick = onDismissError) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun Content(
    state: HelpRequestDetailUiState.Success,
    onDecline: () -> Unit,
    onWithdraw: () -> Unit,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onSetNote: (String) -> Unit,
) {
    val request = state.request
    val accent = entraideAccent()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        // The request in one tile (plan de refonte du 21/11): what, when, where, who.
        FormTile(Icons.Outlined.VolunteerActivism, request.title, accent = accent) {
            Text(kindLabel(request.kind), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(whenLabel(request, state.today), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            // Like an event (récit 74): the place, with the route as soon as there is an address.
            placeView(request.placeKind, request.place, request.placeHouseholdId, state.households, LocalFeatureFlags.current.isOn(Feature.Places))?.let { place ->
                Text(place.text, style = MaterialTheme.typography.bodyLarge)
                place.directions?.let { DirectionsButton(it) }
            }
            val author = if (state.isMine) stringResource(R.string.helprequest_by_me) else request.authorName
            Text(
                state.helpedName?.let { stringResource(R.string.helprequest_for_by, it, author) }
                    ?: stringResource(R.string.helprequest_by, author),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            request.note?.let { MentionText(it, request.mentionIds, style = MaterialTheme.typography.bodyLarge) }
            request.photo?.let { HelpPhoto(it) }
            Text(
                stringResource(R.string.helprequest_warned, state.warnedNames.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StatusTile(state, accent, onEdit) {
            if (state.isBusy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            val enabled = !state.isBusy
            if (state.canWithdraw) {
                var editing by rememberSaveable { mutableStateOf(false) }
                ActionLink(
                    stringResource(if (request.volunteerNote == null) R.string.helprequest_note_add else R.string.helprequest_note_edit),
                    enabled = enabled,
                    onClick = { editing = true },
                )
                if (editing) {
                    NoteDialog(
                        title = stringResource(R.string.helprequest_note_edit),
                        label = stringResource(R.string.helprequest_note_label, request.authorName),
                        initial = request.volunteerNote.orEmpty(),
                        confirm = stringResource(R.string.helprequest_note_save),
                        onConfirm = { editing = false; onSetNote(it) },
                        onDismiss = { editing = false },
                    )
                }
            }
            if (state.canDecline) {
                ActionLink(stringResource(R.string.helprequest_decline), enabled = enabled, onClick = onDecline)
            }
            if (state.canWithdraw) {
                ConfirmedButton(
                    label = stringResource(R.string.helprequest_withdraw),
                    title = stringResource(R.string.helprequest_withdraw_title),
                    body = stringResource(R.string.helprequest_withdraw_body),
                    confirm = stringResource(R.string.helprequest_withdraw_confirm),
                    enabled = enabled,
                    onConfirm = onWithdraw,
                )
            }
        }
        if (state.canManage) {
            val enabled = !state.isBusy
            FormOutlineButton(stringResource(R.string.helprequest_duplicate), onDuplicate, enabled = enabled, icon = Icons.Outlined.ContentCopy)
            if (request.status != HelpStatus.Cancelled && request.date >= state.today) {
                ConfirmedButton(
                    label = stringResource(R.string.helprequest_cancel),
                    title = stringResource(R.string.helprequest_cancel_title),
                    body = request.volunteerName?.let { stringResource(R.string.helprequest_cancel_body_taken, it) }
                        ?: stringResource(R.string.helprequest_cancel_body),
                    confirm = stringResource(R.string.helprequest_cancel_confirm),
                    enabled = enabled,
                    destructive = true,
                    onConfirm = onCancel,
                )
            }
        }
    }
}

/**
 * Where the request stands, in its tile, with the second actions of the volunteer ([actions]). The
 * author is offered to widen after a « Je ne peux pas ».
 */
@Composable
private fun StatusTile(state: HelpRequestDetailUiState.Success, accent: Color, onEdit: () -> Unit, actions: @Composable ColumnScope.() -> Unit) {
    val request = state.request
    FormTile(Icons.Outlined.Info, stringResource(R.string.helprequest_tile_status), accent = accent) {
        val text = when {
            request.status == HelpStatus.Taken && request.volunteerId != null && !state.canWithdraw && request.thankedAt != null ->
                stringResource(R.string.helprequest_status_thanked, request.volunteerName.orEmpty())
            state.canWithdraw -> stringResource(R.string.helprequest_status_mine_taken)
            request.status == HelpStatus.Taken -> stringResource(R.string.helprequest_status_taken_by, request.volunteerName.orEmpty())
            state.declinedName != null && state.isMine -> stringResource(R.string.helprequest_status_declined_by, state.declinedName)
            state.declinedName != null -> stringResource(R.string.helprequest_status_i_declined)
            state.isMine -> stringResource(R.string.helprequest_status_waiting)
            else -> stringResource(R.string.helprequest_status_open)
        }
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (request.pending) PendingSign()
        if (request.status == HelpStatus.Taken) {
            request.volunteerNote?.let {
                Text(
                    stringResource(R.string.helprequest_note_quote, it),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (state.declinedName != null && state.canManage && request.status == HelpStatus.Open) {
            Text(
                stringResource(R.string.helprequest_widen_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onEdit, modifier = Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.helprequest_widen))
            }
        }
        actions()
    }
}

/** The volunteer's word, optional (spec récit 16 rév. 8). Empty means no word. */
@Composable
private fun NoteDialog(
    title: String,
    label: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(VOLUNTEER_NOTE_MAX) },
                label = { Text(label) },
                supportingText = { Text(stringResource(R.string.helprequest_note_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ConfirmedButton(
    label: String,
    title: String,
    body: String,
    confirm: String,
    enabled: Boolean,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
) {
    var asking by rememberSaveable { mutableStateOf(false) }
    // « Annuler la demande » at the very bottom, with a pale red contour (plan de refonte du 21/11).
    if (destructive) {
        FormDeleteButton(label, onClick = { asking = true }, enabled = enabled)
    } else {
        ActionLink(label, enabled = enabled, onClick = { asking = true })
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = { asking = false; onConfirm() }) {
                    Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.action_keep)) } },
        )
    }
}

/** A secondary action as a text link of 56 dp, like ClayLink, that can be disabled while a write runs. */
@Composable
private fun ActionLink(text: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
        Text(" ›", style = MaterialTheme.typography.labelLarge, modifier = Modifier.clearAndSetSemantics { })
    }
}

/** Fictional data for previews and screenshots. */
internal fun previewHelpRequestDetail(isMine: Boolean = false): HelpRequestDetailUiState.Success {
    val today = LocalDate.of(2026, 11, 20)
    val request = HelpRequest(
        "r1", HelpKind.Childcare, "Garder Noé mercredi après-midi", today.plusDays(5), LocalTime.of(14, 0), LocalTime.of(18, 0),
        "Chez nous", "Le goûter est dans le placard.", "camille", "Camille", "noe", listOf("me", "ines"),
    )
    return HelpRequestDetailUiState.Success(
        request = request,
        today = today,
        helpedName = "Noé",
        isMine = isMine,
        myFirstName = "Jules",
        warnedNames = listOf("Inès", "Jules"),
        declinedName = null,
        canVolunteer = !isMine,
        canDecline = false,
        canWithdraw = false,
        canManage = isMine,
        canThank = false,
    )
}

@FamiliaPreviews
@Composable
private fun HelpRequestDetailScreenPreview() {
    FamiliaTheme { Surface { HelpRequestDetailScreen(previewHelpRequestDetail(), {}, {}, {}, {}, {}, {}, {}, {}, {}) } }
}

/**
 * The photo joined to a request, whole (a QR code must not be cropped), and full screen on a tap
 * so that it can be scanned at the counter. Stateless but for the full-screen switch.
 */
@Composable
private fun HelpPhoto(base: String) {
    var full by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.helprequest_photo_label)
    val open = stringResource(R.string.helprequest_photo_open)
    StorageImage(
        path = SinglePhoto.photoPath(base),
        contentDescription = label,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = open) { full = true },
    )
    if (full) {
        Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = Color.Black) {
                Column(Modifier.fillMaxSize()) {
                    TextButton(
                        onClick = { full = false },
                        modifier = Modifier.align(Alignment.End).padding(8.dp).heightIn(min = 56.dp),
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = null, tint = Color.White)
                        Text(stringResource(R.string.helprequest_photo_close), color = Color.White, modifier = Modifier.padding(start = 8.dp))
                    }
                    StorageImage(
                        path = SinglePhoto.photoPath(base),
                        contentDescription = label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}
