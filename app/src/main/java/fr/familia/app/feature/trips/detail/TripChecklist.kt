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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.model.TripItem
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.form.PeopleRow
import fr.familia.app.core.ui.form.PersonChoice
import fr.familia.app.feature.trips.TripPageCard
import fr.familia.app.feature.trips.TripPageCheck
import fr.familia.app.feature.trips.TripPageLink

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
    /** On the light blue page of « Valise » (fiche F3): the white card, the dark boxes, « Responsable : ». */
    onPage: Boolean = false,
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val lines: @Composable () -> Unit = {
        if (items.isEmpty()) {
            Text(
                stringResource(if (isParticipant) R.string.trip_checklist_empty_participant else R.string.trip_checklist_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = if (onPage) TripPathColors.Ink.copy(alpha = 0.7f) else FamiliaClay.colors.onTintVariant(ClayTint.Plain),
            )
        }
        items.forEach { item ->
            ItemRow(item, isParticipant, onDone = { actions.onDone(item.id, it) }, onEdit = { editingId = item.id }, onPage = onPage)
            if (isParticipant && !item.done) TakeButton(item, actions, onPage)
        }
    }
    if (onPage) {
        TripPageCard {
            lines()
            if (isParticipant) TripPageLink(stringResource(R.string.trip_checklist_add_line), onClick = { editingId = NEW_ITEM })
        }
    } else {
        ClayCard(Modifier.fillMaxWidth(), tint = ClayTint.Plain) {
            lines()
            if (isParticipant) {
                ClayLink(stringResource(R.string.trip_checklist_add), onClick = { editingId = NEW_ITEM }, modifier = Modifier.padding(top = 8.dp))
            }
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
private fun TakeButton(item: TripItem, actions: ChecklistActions, onPage: Boolean) {
    val take = actions.onTake
    val release = actions.onRelease
    val colors = if (onPage) ButtonDefaults.textButtonColors(contentColor = TripPathColors.Ink) else ButtonDefaults.textButtonColors()
    when {
        item.assigneeId == null && take != null ->
            TextButton(onClick = { take(item) }, modifier = Modifier.heightIn(min = 56.dp).padding(start = 36.dp), colors = colors) {
                Text(stringResource(R.string.packing_take))
            }
        item.assigneeId != null && item.assigneeId == actions.myId && release != null ->
            TextButton(onClick = { release(item) }, modifier = Modifier.heightIn(min = 56.dp).padding(start = 36.dp), colors = colors) {
                Text(stringResource(R.string.packing_release))
            }
    }
}

@Composable
private fun ItemRow(item: TripItem, isParticipant: Boolean, onDone: (Boolean) -> Unit, onEdit: () -> Unit, onPage: Boolean) {
    val variant = if (onPage) TripPathColors.Ink.copy(alpha = 0.7f) else FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    val ink = if (onPage) TripPathColors.Ink else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        val text: @Composable () -> Unit = {
            Column {
                if (onPage) {
                    Text(
                        item.label,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.done) variant else ink,
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    )
                    item.assigneeName?.let { Text(stringResource(R.string.trip_checklist_in_charge, it), fontSize = 13.sp, color = variant) }
                } else {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (item.done) variant else ink,
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    )
                    item.assigneeName?.let {
                        Text(stringResource(R.string.trip_checklist_assignee, it), style = MaterialTheme.typography.bodyMedium, color = variant)
                    }
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
                if (onPage) TripPageCheck(item.done) else Checkbox(checked = item.done, onCheckedChange = null)
                Spacer(Modifier.width(12.dp))
                text()
            }
            Spacer(Modifier.width(8.dp))
            val editLabel = stringResource(R.string.trip_checklist_edit_action, item.label)
            TextButton(
                onClick = onEdit,
                modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = editLabel },
                colors = if (onPage) ButtonDefaults.textButtonColors(contentColor = TripPathColors.Ink) else ButtonDefaults.textButtonColors(),
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.trip_checklist_edit))
            }
        } else {
            Icon(
                if (item.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = stringResource(if (item.done) R.string.trip_checklist_done else R.string.trip_checklist_todo),
                tint = if (item.done) (if (onPage) ink else MaterialTheme.colorScheme.primary) else variant,
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
                // One round each, as everywhere else (refonte lot 4); « Personne » first.
                PeopleRow(
                    choices = listOf(
                        PersonChoice(
                            key = NOBODY,
                            label = stringResource(R.string.trip_checklist_nobody),
                            selected = assigneeId == null,
                            icon = Icons.Outlined.PersonOff,
                        ),
                    ) + assignees.map { a -> PersonChoice(key = a.id, label = a.name, selected = assigneeId == a.id, memberId = a.id) },
                    multiple = false,
                    onToggle = { key -> assigneeId = key.takeIf { it != NOBODY } },
                )
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

/** The key of « Personne » among the rounds; the others are member ids. */
private const val NOBODY = ""
