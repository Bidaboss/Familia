package fr.familia.app.feature.events.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.ui.form.FormTileHeading
import fr.familia.app.core.ui.form.FormChip
import fr.familia.app.core.ui.form.ChipGroup
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventReminders
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.ReminderOffset
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.FamiliaPreviews

/**
 * « Me le rappeler » on the event's page (PRD rév. 248, récit 56). Nothing shows without the
 * repository (« Voir comme »: the reminders of a member are theirs alone).
 */
@Composable
fun EventRemindersSection(container: AppContainer?, currentMember: Member, event: Event, coming: Boolean) {
    val repository = container?.eventReminderRepository ?: return
    val viewModel: EventRemindersViewModel = viewModel(
        key = "reminders-${event.id}-${event.seriesId}-${event.time}-$coming",
        factory = viewModelFactory {
            initializer { EventRemindersViewModel(repository, currentMember.id, event.id, event.seriesId, event.time, coming) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EventRemindersContent(state, onSave = viewModel::save)
}

/** Stateless: the row, and the dialog it opens. */
@Composable
fun EventRemindersContent(state: EventRemindersUiState, onSave: (Set<ReminderOffset>, Boolean) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val summary = when {
        state.effective.isEmpty() -> stringResource(R.string.reminders_none)
        state.byDefault -> stringResource(R.string.reminders_by_default, labels(state.effective))
        state.fromSeries -> stringResource(R.string.reminders_every_time_summary, labels(state.effective))
        else -> labels(state.effective).replaceFirstChar { it.uppercase() }
    }
    // The heading of its tile, then what is set; the whole block opens the choice (plan de refonte du 21/11).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = !state.isLoading, onClickLabel = stringResource(R.string.reminders_title)) { open = true },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FormTileHeading(Icons.Outlined.Alarm, stringResource(R.string.reminders_title))
        Text(summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (state.failed) {
        Text(stringResource(R.string.reminders_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    if (open) {
        RemindersDialog(
            state = state,
            onDismiss = { open = false },
            onSave = { offsets, everyTime ->
                open = false
                onSave(offsets, everyTime)
            },
        )
    }
}

@Composable
private fun RemindersDialog(state: EventRemindersUiState, onDismiss: () -> Unit, onSave: (Set<ReminderOffset>, Boolean) -> Unit) {
    var chosen by remember { mutableStateOf(state.effective.filter { it in state.choices }.toSet()) }
    var everyTime by remember { mutableStateOf(state.fromSeries) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminders_title)) },
        text = {
            Column {
                Text(stringResource(R.string.reminders_private), style = MaterialTheme.typography.bodyMedium)
                state.choices.forEach { offset ->
                    val checked = offset in chosen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .toggleable(value = checked, role = Role.Checkbox) { on -> chosen = if (on) chosen + offset else chosen - offset },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(offset.label).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (state.inSeries) {
                    // Pills instead of radio buttons (plan de refonte du 21/11).
                    ChipGroup(Modifier.padding(top = 12.dp)) {
                        FormChip(stringResource(R.string.reminders_this_time), selected = !everyTime, onClick = { everyTime = false })
                        FormChip(stringResource(R.string.reminders_every_time), selected = everyTime, onClick = { everyTime = true })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(chosen, everyTime) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.reminders_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** « 1 h avant, le matin à 8 h », in the order of the choices. */
@Composable
private fun labels(offsets: Set<ReminderOffset>): String =
    ReminderOffset.entries.filter { it in offsets }.map { stringResource(it.label) }.joinToString(", ")

/** « Rappels quand je viens », in Notifications: they count for every event I say « Je viens » to. */
@Composable
fun DefaultRemindersSection(container: AppContainer?, currentMember: Member) {
    val repository = container?.eventReminderRepository ?: return
    val viewModel: DefaultRemindersViewModel = viewModel(
        key = "default-reminders-${currentMember.id}",
        factory = viewModelFactory { initializer { DefaultRemindersViewModel(repository, currentMember.id) } },
    )
    val defaults by viewModel.defaults.collectAsStateWithLifecycle()
    val failed by viewModel.saveFailed.collectAsStateWithLifecycle()
    DefaultRemindersContent(defaults, failed, onToggle = viewModel::toggle)
}

@Composable
fun DefaultRemindersContent(defaults: Set<ReminderOffset>, failed: Boolean, onToggle: (ReminderOffset, Boolean) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.reminders_defaults_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.reminders_defaults_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReminderOffset.entries.forEach { offset ->
            val checked = offset in defaults
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(value = checked, role = Role.Checkbox) { on -> onToggle(offset, on) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(offset.label).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (failed) Text(stringResource(R.string.reminders_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@FamiliaPreviews
@Composable
private fun EventRemindersPreview() {
    FamiliaTheme {
        Column(Modifier.padding(16.dp)) {
            EventRemindersContent(
                EventRemindersUiState(reminders = EventReminders(defaults = setOf(ReminderOffset.OneHour, ReminderOffset.Morning)), coming = true, isLoading = false),
                onSave = { _, _ -> },
            )
            DefaultRemindersContent(setOf(ReminderOffset.OneHour), failed = false, onToggle = { _, _ -> })
        }
    }
}
