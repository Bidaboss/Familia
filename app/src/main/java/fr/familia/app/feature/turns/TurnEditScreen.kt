package fr.familia.app.feature.turns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Turn
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews

/** Create a rotation, or change one: a title, then the households in their order. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnEditScreen(
    state: TurnEditUiState,
    onTitle: (String) -> Unit,
    onToggle: (String) -> Unit,
    onUp: (String) -> Unit,
    onDown: (String) -> Unit,
    onSave: () -> Unit,
    onDismissFailure: () -> Unit,
    onBack: () -> Unit,
) {
    val editing = state as? TurnEditUiState.Editing
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editing?.isNew == false) R.string.turns_edit_title else R.string.turns_create_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                TurnEditUiState.Loading, TurnEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                TurnEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.Autorenew,
                    title = stringResource(R.string.turns_missing_title),
                    body = stringResource(R.string.turns_missing_body),
                )
                is TurnEditUiState.Editing -> Form(state, onTitle, onToggle, onUp, onDown, onSave)
            }
        }
    }
    if (editing?.failed == true) {
        AlertDialog(
            onDismissRequest = onDismissFailure,
            text = { Text(stringResource(R.string.turns_save_failed)) },
            confirmButton = { TextButton(onClick = onDismissFailure) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun Form(
    state: TurnEditUiState.Editing,
    onTitle: (String) -> Unit,
    onToggle: (String) -> Unit,
    onUp: (String) -> Unit,
    onDown: (String) -> Unit,
    onSave: () -> Unit,
) {
    val order = state.form.order
    val names = state.households.associate { it.id to it.name }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = state.form.title,
            onValueChange = onTitle,
            label = { Text(stringResource(R.string.turns_field_title)) },
            placeholder = { Text(stringResource(R.string.turns_field_title_hint)) },
            isError = TurnFormError.TitleMissing in state.errors,
            supportingText = if (TurnFormError.TitleMissing in state.errors) {
                { Text(stringResource(R.string.turns_error_title_missing)) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.turns_order_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (order.isEmpty()) {
            Text(stringResource(R.string.turns_order_empty), style = MaterialTheme.typography.bodyMedium)
        }
        order.forEachIndexed { index, id ->
            val name = householdLabel(names[id].orEmpty())
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.turns_order_line, index + 1, name), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { onUp(id) }, enabled = index > 0, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.turns_move_up, name))
                }
                IconButton(onClick = { onDown(id) }, enabled = index < order.lastIndex, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.turns_move_down, name))
                }
            }
        }
        if (TurnFormError.TooFewHouseholds in state.errors) {
            Text(stringResource(R.string.turns_error_households), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Text(stringResource(R.string.turns_households_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        state.households.forEach { household -> HouseholdRow(household, household.id in order, order.size < Turn.HOUSEHOLDS_MAX, onToggle) }

        Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(if (state.isNew) R.string.turns_create_action else R.string.turns_save_action))
        }
    }
}

@Composable
private fun HouseholdRow(household: Household, checked: Boolean, canAdd: Boolean, onToggle: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = checked || canAdd, role = Role.Checkbox, onValueChange = { onToggle(household.id) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = checked || canAdd)
        Text(householdLabel(household.name), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

@FamiliaPreviews
@Composable
private fun TurnEditScreenPreview() {
    val households = listOf(Household("h1", "Foyer Camille"), Household("h2", "Foyer Inès"), Household("h3", "Foyer Noé"))
    FamiliaTheme {
        Surface {
            TurnEditScreen(
                TurnEditUiState.Editing(TurnForm("Qui reçoit Noël", listOf("h2", "h1")), households, isNew = true),
                {}, {}, {}, {}, {}, {}, {},
            )
        }
    }
}
