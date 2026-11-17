package fr.familia.app.feature.trips.detail

import fr.familia.app.core.ui.ClayLink
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.TripItem
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.ui.ClayCard

/** What the checklist can do; only a participant gets the changing ones. */
data class ChecklistActions(
    val onAdd: (label: String, assigneeId: String?) -> Unit = { _, _ -> },
    val onEdit: (item: TripItem, label: String, assigneeId: String?) -> Unit = { _, _, _ -> },
    val onDone: (itemId: String, done: Boolean) -> Unit = { _, _ -> },
    val onDelete: (itemId: String) -> Unit = {},
    /** « Je m'en charge » on a free line (récit 62); null while packingList is off. */
    val onTake: ((TripItem) -> Unit)? = null,
    /** « Je ne peux plus » on a line I took. */
    val onRelease: ((TripItem) -> Unit)? = null,
    /** Who I am, to offer « Je ne peux plus » on my lines only. */
    val myId: String? = null,
)

/** In the dialog: a new line, or the id of the line being edited. Saved, so a rotation keeps it open. */
private const val NEW_ITEM = ""

/**
 * « À prévoir » (récit 13): what to bring or book, who takes care of it, ticked once done. A
 * participant ticks, adds and edits the lines; a follower only reads them.
 */
@Composable
internal fun TripChecklist(
    items: List<TripItem>,
    assignees: List<ItemAssignee>,
    isParticipant: Boolean,
    actions: ChecklistActions,
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    ClayCard(Modifier.fillMaxWidth(), tint = ClayTint.Plain) {
        if (items.isEmpty()) {
            Text(
                stringResource(if (isParticipant) R.string.trip_checklist_empty_participant else R.string.trip_checklist_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = FamiliaClay.colors.onTintVariant(ClayTint.Plain),
            )
        }
        items.forEach { item ->
            ItemRow(item, isParticipant, onDone = { actions.onDone(item.id, it) }, onEdit = { editingId = item.id })
            if (isParticipant && !item.done) TakeButton(item, actions)
        }
        if (isParticipant) {
            ClayLink(stringResource(R.string.trip_checklist_add), onClick = { editingId = NEW_ITEM }, modifier = Modifier.padding(top = 8.dp))
        }
    }
    val id = editingId
    if (id != null) {
        // A line removed meanwhile by someone else closes the dialog.
        val item = items.firstOrNull { it.id == id }
        if (id != NEW_ITEM && item == null) {
            LaunchedEffect(id) { editingId = null }
        } else {
            ItemDialog(
                item = item,
                assignees = assignees,
                onSave = { label, assigneeId ->
                    if (item == null) actions.onAdd(label, assigneeId) else actions.onEdit(item, label, assigneeId)
                    editingId = null
                },
                onDelete = item?.let { line -> { actions.onDelete(line.id); editingId = null } },
                onDismiss = { editingId = null },
            )
        }
    }
}

/** « Je m'en charge » on a free line, « Je ne peux plus » on mine: one tap, no dialog (récit 62). */
@Composable
private fun TakeButton(item: TripItem, actions: ChecklistActions) {
    val take = actions.onTake
    val release = actions.onRelease
    when {
        item.assigneeId == null && take != null ->
            TextButton(onClick = { take(item) }, modifier = Modifier.heightIn(min = 56.dp).padding(start = 36.dp)) {
                Text(stringResource(R.string.packing_take))
            }
        item.assigneeId != null && item.assigneeId == actions.myId && release != null ->
            TextButton(onClick = { release(item) }, modifier = Modifier.heightIn(min = 56.dp).padding(start = 36.dp)) {
                Text(stringResource(R.string.packing_release))
            }
    }
}

@Composable
private fun ItemRow(item: TripItem, isParticipant: Boolean, onDone: (Boolean) -> Unit, onEdit: () -> Unit) {
    val variant = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        val text: @Composable () -> Unit = {
            Column {
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (item.done) variant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                )
                item.assigneeName?.let {
                    Text(stringResource(R.string.trip_checklist_assignee, it), style = MaterialTheme.typography.bodyMedium, color = variant)
                }
            }
        }
        if (isParticipant) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .toggleable(value = item.done, role = Role.Checkbox, onValueChange = onDone),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = item.done, onCheckedChange = null)
                Spacer(Modifier.width(12.dp))
                text()
            }
            Spacer(Modifier.width(8.dp))
            val editLabel = stringResource(R.string.trip_checklist_edit_action, item.label)
            TextButton(
                onClick = onEdit,
                modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = editLabel },
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.trip_checklist_edit))
            }
        } else {
            Icon(
                if (item.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = stringResource(if (item.done) R.string.trip_checklist_done else R.string.trip_checklist_todo),
                tint = if (item.done) MaterialTheme.colorScheme.primary else variant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { text() }
        }
    }
}

/** Adds or edits one line: what, and who takes care of it. Removing it is offered when editing. */
@Composable
private fun ItemDialog(
    item: TripItem?,
    assignees: List<ItemAssignee>,
    onSave: (label: String, assigneeId: String?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    // Declared before the confirmation, so « Garder » finds the choices not saved yet.
    var label by rememberSaveable { mutableStateOf(item?.label.orEmpty()) }
    var assigneeId by rememberSaveable { mutableStateOf(item?.assigneeId?.takeIf { id -> assignees.any { it.id == id } }) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmDelete && onDelete != null && item != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.trip_checklist_delete_title, item.label)) },
            text = { Text(stringResource(R.string.trip_checklist_delete_body)) },
            confirmButton = {
                TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.trip_checklist_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.trip_checklist_delete_keep))
                }
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (item == null) R.string.trip_checklist_add else R.string.trip_checklist_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(ITEM_LABEL_MAX) },
                    label = { Text(stringResource(R.string.trip_checklist_label)) },
                    supportingText = { Text(stringResource(R.string.trip_checklist_label_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.trip_checklist_who),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                Column(Modifier.selectableGroup()) {
                    AssigneeRow(stringResource(R.string.trip_checklist_nobody), assigneeId == null) { assigneeId = null }
                    assignees.forEach { a -> AssigneeRow(a.name, assigneeId == a.id) { assigneeId = a.id } }
                }
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 8.dp).heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.trip_checklist_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(label, assigneeId) },
                enabled = label.isNotBlank(),
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.trip_checklist_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun AssigneeRow(name: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge)
    }
}
