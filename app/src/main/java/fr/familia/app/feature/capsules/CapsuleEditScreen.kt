package fr.familia.app.feature.capsules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Capsule
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** What the screen can ask of its ViewModel. */
data class CapsuleEditActions(
    val onText: (String) -> Unit = {},
    val onToggleRecipient: (String) -> Unit = {},
    val onOpenAt: (LocalDate) -> Unit = {},
    val onSeal: () -> Unit = {},
)

/** « Nouvelle capsule »: the message, who it is for, and when it opens. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapsuleEditScreen(state: CapsuleEditUiState, actions: CapsuleEditActions, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capsules_add)) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { HelpButton(ScreenHelp.Capsules) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                CapsuleEditUiState.Loading, is CapsuleEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                CapsuleEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.HourglassEmpty,
                    title = stringResource(R.string.capsules_error_title),
                    body = stringResource(R.string.capsules_error_body),
                )
                is CapsuleEditUiState.Editing -> Form(state, actions)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(state: CapsuleEditUiState.Editing, actions: CapsuleEditActions) {
    val form = state.form
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    val enabled = !state.isSaving
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = form.text,
            onValueChange = actions.onText,
            label = { Text(stringResource(R.string.capsule_edit_text)) },
            supportingText = { Text(stringResource(R.string.capsule_edit_count, form.text.length, Capsule.MAX_TEXT)) },
            isError = CapsuleFormError.TextMissing in state.errors,
            minLines = 5,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        SectionTitle(stringResource(R.string.capsule_edit_who))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.members.forEach { member ->
                val selected = member.id in form.recipientIds
                FilterChip(
                    selected = selected,
                    onClick = { actions.onToggleRecipient(member.id) },
                    enabled = enabled,
                    label = { Text(member.firstName, style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
        if (CapsuleFormError.NoRecipient in state.errors) ErrorText(stringResource(R.string.capsule_edit_no_recipient))
        SectionTitle(stringResource(R.string.capsule_edit_when))
        val first = Capsule.earliestOpening(state.today)
        val last = Capsule.latestOpening(state.today)
        OpenDayButton(form.openAt, first, last, enabled, actions.onOpenAt)
        Text(stringResource(R.string.capsule_edit_when_hint, capsuleDay(first), capsuleDay(last)), style = MaterialTheme.typography.bodyMedium, color = muted)
        if (CapsuleFormError.DateMissing in state.errors || CapsuleFormError.DateOutOfRange in state.errors) {
            ErrorText(stringResource(R.string.capsule_edit_no_date))
        }
        Text(stringResource(R.string.capsule_edit_sealed_note), style = MaterialTheme.typography.bodyMedium, color = muted)
        if (state.saveFailed) ErrorText(stringResource(R.string.capsule_edit_failed))
        Button(onClick = actions.onSeal, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.capsule_edit_seal))
        }
    }
}

/** Opens a calendar where only the days the rules accept can be picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenDayButton(day: LocalDate?, first: LocalDate, last: LocalDate, enabled: Boolean, onPick: (LocalDate) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(day?.let { stringResource(R.string.capsule_opens_on, capsuleDay(it)) } ?: stringResource(R.string.capsule_edit_pick_day))
    }
    if (open) {
        val firstMillis = first.toUtcMillis()
        val lastMillis = last.toUtcMillis()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (day ?: first.plusMonths(11)).toUtcMillis(),
            yearRange = first.year..last.year,
            selectableDates = remember(firstMillis, lastMillis) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in firstMillis..lastMillis
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = pickerState) }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

/** A half-written capsule of Camille for Inès, for previews and screenshots. */
internal fun previewCapsuleEdit(): CapsuleEditUiState.Editing = CapsuleEditUiState.Editing(
    form = CapsuleForm(text = "Un petit mot pour Noël prochain.", recipientIds = setOf("b"), openAt = LocalDate.of(2027, 12, 24)),
    today = LocalDate.of(2026, 11, 16),
    members = listOf(RecipientChoice("a", "Camille"), RecipientChoice("b", "Inès"), RecipientChoice("c", "Noé"), RecipientChoice("d", "Lou")),
)

@FamiliaPreviews
@Composable
private fun CapsuleEditPreview() {
    FamiliaTheme { Surface { CapsuleEditScreen(previewCapsuleEdit(), CapsuleEditActions(), {}) } }
}
