package fr.familia.app.feature.helprequests.detail

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.HelpKind
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.HelpStatus
import fr.familia.app.core.model.VOLUNTEER_NOTE_MAX
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
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
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.helprequest_detail_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    if ((state as? HelpRequestDetailUiState.Success)?.canManage == true) {
                        TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.event_detail_edit), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                HelpRequestDetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
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
                    Content(state, onTake, onDecline, onWithdraw, onThank, onCancel, onEdit, onDuplicate, onSetNote)
            }
        }
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
    onTake: (String) -> Unit,
    onDecline: () -> Unit,
    onWithdraw: () -> Unit,
    onThank: () -> Unit,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onSetNote: (String) -> Unit,
) {
    val request = state.request
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(kindLabel(request.kind), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(request.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(whenLabel(request, state.today), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        request.place?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        val author = if (state.isMine) stringResource(R.string.helprequest_by_me) else request.authorName
        Text(
            state.helpedName?.let { stringResource(R.string.helprequest_for_by, it, author) }
                ?: stringResource(R.string.helprequest_by, author),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        request.note?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        Text(
            stringResource(R.string.helprequest_warned, state.warnedNames.joinToString(", ")),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        StatusCard(state, onEdit)

        if (state.isBusy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        val enabled = !state.isBusy
        if (state.canVolunteer) {
            var taking by rememberSaveable { mutableStateOf(false) }
            Button(onClick = { taking = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.helprequest_take))
            }
            if (taking) {
                NoteDialog(
                    title = stringResource(R.string.helprequest_take_title),
                    label = stringResource(R.string.helprequest_note_label, request.authorName),
                    initial = "",
                    confirm = stringResource(R.string.helprequest_take),
                    onConfirm = { taking = false; onTake(it) },
                    onDismiss = { taking = false },
                )
            }
        }
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
        if (state.canThank) {
            Button(onClick = onThank, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.helprequest_thank, request.volunteerName.orEmpty()))
            }
        }
        if (state.canManage) {
            ActionLink(stringResource(R.string.helprequest_duplicate), enabled = enabled, onClick = onDuplicate)
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

/** Where the request stands, in one card. The author is offered to widen after a « Je ne peux pas ». */
@Composable
private fun StatusCard(state: HelpRequestDetailUiState.Success, onEdit: () -> Unit) {
    val request = state.request
    // The point of the screen: where the request stands, in full relief (DESIGN-11).
    ClayCard(Modifier.fillMaxWidth(), emphasis = true) {
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
    ActionLink(label, enabled = enabled, destructive = destructive, onClick = { asking = true })
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

/**
 * A secondary action as a text link of 56 dp, like ClayLink, that can be disabled while a write
 * runs, or drawn in the error color for « Annuler la demande » (DESIGN-11).
 */
@Composable
private fun ActionLink(text: String, enabled: Boolean, onClick: () -> Unit, destructive: Boolean = false) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors(),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
        if (!destructive) Text(" ›", style = MaterialTheme.typography.labelLarge, modifier = Modifier.clearAndSetSemantics { })
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
