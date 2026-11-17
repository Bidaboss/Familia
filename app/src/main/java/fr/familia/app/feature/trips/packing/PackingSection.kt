package fr.familia.app.feature.trips.packing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.PackingLine
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.feature.presentation.LocalPresenting

/**
 * « Ma valise » (récit 62) under « Pour tout le monde »: my bag, then those of my children who come.
 * Nothing for a follower, in « Voir comme » (no repository) or while the Mode présentation is on.
 */
@Composable
fun PackingSection(container: AppContainer?, currentMember: Member, tripId: String) {
    val context = LocalContext.current
    val viewModel: PackingViewModel = viewModel(
        key = "packing-$tripId-${currentMember.id}",
        factory = viewModelFactory {
            initializer {
                PackingViewModel(
                    container?.packingRepository,
                    container?.tripRepository,
                    container?.memberRepository,
                    currentMember.id,
                    tripId,
                    context.resources.getStringArray(R.array.packing_default).toList(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (LocalPresenting.current || state.bags.isEmpty()) return
    PackingBags(
        state,
        PackingActions(
            onToggle = viewModel::toggle,
            onAdd = viewModel::add,
            onRemove = viewModel::remove,
            onKeepAsMyList = viewModel::keepAsMyList,
            onDismissFailure = viewModel::dismissFailure,
        ),
    )
}

class PackingActions(
    val onToggle: (ownerId: String, index: Int, done: Boolean) -> Unit = { _, _, _ -> },
    val onAdd: (ownerId: String, label: String) -> Unit = { _, _ -> },
    val onRemove: (ownerId: String, index: Int) -> Unit = { _, _ -> },
    val onKeepAsMyList: () -> Unit = {},
    val onDismissFailure: () -> Unit = {},
)

/** Stateless: one card per bag. */
@Composable
fun PackingBags(state: PackingUiState, actions: PackingActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.sendFailed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.packing_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = actions.onDismissFailure, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        }
        state.bags.forEach { bag -> BagCard(bag, actions) }
    }
}

@Composable
private fun BagCard(bag: BagUiState, actions: PackingActions) {
    val ownerId = bag.owner.memberId
    var adding by rememberSaveable(ownerId) { mutableStateOf(false) }
    var removing by rememberSaveable(ownerId) { mutableStateOf<Int?>(null) }
    var kept by rememberSaveable(ownerId) { mutableStateOf(false) }
    val tint = ClayTint.Plain
    ClayCard(Modifier.fillMaxWidth(), tint = tint) {
        Text(
            if (bag.owner.isMe) stringResource(R.string.packing_my_bag) else stringResource(R.string.packing_bag_of, bag.owner.firstName),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            if (bag.left == 0) stringResource(R.string.packing_all_done) else stringResource(R.string.packing_left, bag.left),
            style = MaterialTheme.typography.bodyMedium,
            color = FamiliaClay.colors.onTintVariant(tint),
        )
        bag.lines.forEachIndexed { index, line ->
            LineRow(line, onToggle = { actions.onToggle(ownerId, index, it) }, onRemove = { removing = index })
        }
        ClayLink(stringResource(R.string.packing_add), onClick = { adding = true })
        if (bag.owner.isMe) {
            if (kept) {
                Text(stringResource(R.string.packing_kept), style = MaterialTheme.typography.bodyMedium, color = FamiliaClay.colors.onTintVariant(tint))
            } else {
                ClayLink(stringResource(R.string.packing_keep), onClick = { actions.onKeepAsMyList(); kept = true })
            }
        }
    }
    if (adding) {
        AddDialog(onAdd = { actions.onAdd(ownerId, it); adding = false }, onDismiss = { adding = false })
    }
    removing?.let { index ->
        val line = bag.lines.getOrNull(index)
        if (line == null) {
            // Removed meanwhile (by the child, or the other parent): nothing left to ask.
            LaunchedEffect(index) { removing = null }
        } else {
            AlertDialog(
                onDismissRequest = { removing = null },
                title = { Text(stringResource(R.string.packing_remove_title, line.label)) },
                confirmButton = {
                    TextButton(onClick = { actions.onRemove(ownerId, index); removing = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.packing_remove_confirm), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { removing = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun LineRow(line: PackingLine, onToggle: (Boolean) -> Unit, onRemove: () -> Unit) {
    val variant = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 56.dp).toggleable(value = line.done, role = Role.Checkbox, onValueChange = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = line.done, onCheckedChange = null)
            Spacer(Modifier.width(12.dp))
            Text(
                line.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (line.done) variant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (line.done) TextDecoration.LineThrough else null,
            )
        }
        val removeLabel = stringResource(R.string.packing_remove_action, line.label)
        TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = removeLabel }) {
            Text(stringResource(R.string.packing_remove))
        }
    }
}

@Composable
private fun AddDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.packing_add)) },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(PackingLine.MAX_LABEL) },
                label = { Text(stringResource(R.string.packing_add_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(label) }, enabled = label.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.packing_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
