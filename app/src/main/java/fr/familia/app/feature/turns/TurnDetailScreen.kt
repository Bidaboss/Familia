package fr.familia.app.feature.turns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Turn
import fr.familia.app.core.model.TurnDone
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.core.ui.PendingSign
import fr.familia.app.core.ui.frenchDate
import java.time.Instant
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import fr.familia.app.core.model.TurnSwap


/** What a tap on the detail asks for. */
data class TurnDetailActions(
    val onDone: (String) -> Unit = {},
    val onSkip: () -> Unit = {},
    val onAskSwap: (String) -> Unit = {},
    val onAcceptSwap: () -> Unit = {},
    val onDropSwap: () -> Unit = {},
    /** « Créer l'événement »: the Planning with the title already there. */
    val onCreateEvent: (String) -> Unit = {},
    val onEdit: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onDismissFailure: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** The lines of the history shown at first, and added by each « Voir plus ». */
internal const val HISTORY_PAGE = 50

/** One rotation: whose turn it is, what comes next, the order and the history. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnDetailScreen(state: TurnDetailUiState, actions: TurnDetailActions, onBack: () -> Unit) {
    val success = state as? TurnDetailUiState.Success
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(success?.turn?.title ?: stringResource(R.string.feature_turns), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    if (success?.rights?.edit == true) {
                        IconButton(onClick = actions.onEdit) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.turns_edit)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.turns_delete)) }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                TurnDetailUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                TurnDetailUiState.Missing -> EmptyState(
                    icon = Icons.Outlined.Autorenew,
                    title = stringResource(R.string.turns_missing_title),
                    body = stringResource(R.string.turns_missing_body),
                )
                TurnDetailUiState.Error -> EmptyState(
                    icon = Icons.Outlined.Autorenew,
                    title = stringResource(R.string.turns_error_title),
                    body = stringResource(R.string.turns_error_body),
                    action = { Button(onClick = actions.onRetry, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_retry)) } },
                )
                is TurnDetailUiState.Success -> Detail(state, actions)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.turns_delete_title)) },
            text = { Text(stringResource(R.string.turns_delete_body)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; actions.onDelete() }) { Text(stringResource(R.string.turns_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (success?.someoneFirst == true) {
        AlertDialog(
            onDismissRequest = actions.onDismissFailure,
            text = { Text(stringResource(R.string.turns_someone_first)) },
            confirmButton = { TextButton(onClick = actions.onDismissFailure) { Text(stringResource(R.string.action_ok)) } },
        )
    }
    if (success?.failed == true) {
        AlertDialog(
            onDismissRequest = actions.onDismissFailure,
            text = { Text(stringResource(R.string.turns_action_failed)) },
            confirmButton = { TextButton(onClick = actions.onDismissFailure) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun Detail(state: TurnDetailUiState.Success, actions: TurnDetailActions) {
    val rights = state.rights
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    var askLabel by rememberSaveable { mutableStateOf(false) }
    var pickSwap by rememberSaveable { mutableStateOf(false) }
    var confirmSkip by rememberSaveable { mutableStateOf(false) }
    // The newest lines first, then more with « Voir plus », with no limit on the whole.
    var historyShown by rememberSaveable { mutableIntStateOf(HISTORY_PAGE) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        ClayCard(Modifier.fillMaxWidth(), tint = ClayTint.Family) {
            val mine = state.order.firstOrNull { it.isNow }?.isMine == true
            Text(
                if (mine) stringResource(R.string.turns_now_us) else stringResource(R.string.turns_now, householdLabel(state.nowName)),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(stringResource(R.string.turns_next, householdLabel(state.nextName)), style = MaterialTheme.typography.bodyMedium)
            if (state.turn.pending) PendingSign()
            if (rights.createEvent) {
                Button(onClick = { actions.onCreateEvent(state.turn.title) }, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.turns_create_event))
                }
            }
            if (rights.done) {
                OutlinedButton(onClick = { askLabel = true }, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.turns_done))
                }
            }
            if (rights.skip) {
                TextButton(onClick = { confirmSkip = true }, enabled = !state.isWorking, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.turns_skip))
                }
            }
        }

        SwapCard(state, actions, onPick = { pickSwap = true })

        Text(stringResource(R.string.turns_order_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        state.order.forEachIndexed { index, place ->
            val name = householdLabel(place.name)
            val line = stringResource(R.string.turns_order_line, index + 1, name)
            Text(
                when {
                    place.isNow && place.isMine -> stringResource(R.string.turns_order_now_us, line)
                    place.isNow -> stringResource(R.string.turns_order_now, line)
                    else -> line
                },
                style = if (place.isNow) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
            )
        }

        Text(stringResource(R.string.turns_history_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (state.history.isEmpty()) {
            Text(stringResource(R.string.turns_history_empty), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        state.history.take(historyShown).forEach { line ->
            val name = householdLabel(line.name)
            Text(
                if (line.done.label.isBlank()) {
                    stringResource(R.string.turns_history_line, name, frenchDate(line.done.at.atZone(FAMILY_ZONE).toLocalDate(), "d MMMM yyyy"))
                } else {
                    stringResource(R.string.turns_history_line_label, line.done.label, name)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (state.history.size > historyShown) {
            TextButton(onClick = { historyShown += HISTORY_PAGE }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.turns_history_more))
            }
        }
    }

    if (askLabel) DoneDialog(onDismiss = { askLabel = false }, onDone = { askLabel = false; actions.onDone(it) })
    if (pickSwap) SwapDialog(state.swapTargets, onDismiss = { pickSwap = false }, onPick = { pickSwap = false; actions.onAskSwap(it) })
    if (confirmSkip) {
        AlertDialog(
            onDismissRequest = { confirmSkip = false },
            title = { Text(stringResource(R.string.turns_skip_title)) },
            text = { Text(stringResource(R.string.turns_skip_body, householdLabel(state.nextName))) },
            confirmButton = { TextButton(onClick = { confirmSkip = false; actions.onSkip() }) { Text(stringResource(R.string.turns_skip)) } },
            dismissButton = { TextButton(onClick = { confirmSkip = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** The pending exchange, as each household sees it, or « Proposer un échange ». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwapCard(state: TurnDetailUiState.Success, actions: TurnDetailActions, onPick: () -> Unit) {
    val rights = state.rights
    val swap = state.turn.swap
    val other = householdLabel(state.swapOtherName)
    when {
        swap != null && rights.answerSwap -> ClayCard(Modifier.fillMaxWidth(), tint = ClayTint.Household) {
            Text(stringResource(R.string.turns_swap_for_us, swap.byName, other), style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions.onAcceptSwap, enabled = !state.isWorking, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.turns_swap_accept))
                }
                OutlinedButton(onClick = actions.onDropSwap, enabled = !state.isWorking, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.turns_swap_refuse))
                }
            }
        }
        swap != null && rights.withdrawSwap -> ClayCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.turns_swap_waiting, other), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = actions.onDropSwap, enabled = !state.isWorking, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.turns_swap_withdraw))
            }
        }
        swap != null -> Text(stringResource(R.string.turns_swap_pending), style = MaterialTheme.typography.bodyMedium)
        rights.askSwap && state.swapTargets.isNotEmpty() ->
            OutlinedButton(onClick = onPick, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.turns_swap_ask))
            }
    }
}

/** « C'est fait », with an optional label kept in the history (« Noël 2026 »). */
@Composable
private fun DoneDialog(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.turns_done_title)) },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(Turn.LABEL_MAX) },
                label = { Text(stringResource(R.string.turns_done_label)) },
                placeholder = { Text(stringResource(R.string.turns_done_label_hint)) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onDone(label) }) { Text(stringResource(R.string.turns_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Which household to propose the exchange to. */
@Composable
private fun SwapDialog(targets: List<Household>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.turns_swap_ask)) },
        text = {
            Column {
                targets.forEach { h ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .selectable(selected = picked == h.id, role = Role.RadioButton, onClick = { picked = h.id }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = picked == h.id, onClick = null)
                        Text(householdLabel(h.name), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { picked?.let(onPick) }, enabled = picked != null) { Text(stringResource(R.string.turns_swap_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Fictional data for previews and screenshots; [swapForUs]: another household asks us to swap. */
internal fun previewTurnDetail(swapForUs: Boolean = false): TurnDetailUiState.Success {
    val swap = if (swapForUs) TurnSwap("h2", "h1", 1, 0, "ines", "Inès") else null
    val turn = Turn("t1", "Qui reçoit Noël", listOf("h1", "h2", "h3"), 0, listOf(TurnDone("h3", "Noël 2025", Instant.parse("2025-12-26T10:00:00Z"))), swap, "camille")
    return TurnDetailUiState.Success(
        turn = turn,
        nowName = "Foyer Camille",
        nextName = "Foyer Inès",
        order = listOf(
            TurnPlace("h1", "Foyer Camille", isMine = true, isNow = true),
            TurnPlace("h2", "Foyer Inès", isMine = false, isNow = false),
            TurnPlace("h3", "Foyer Noé", isMine = false, isNow = false),
        ),
        history = listOf(TurnHistoryLine(turn.history.first(), "Foyer Noé")),
        rights = TurnRights(edit = true, done = true, skip = true, askSwap = !swapForUs, answerSwap = swapForUs, withdrawSwap = false, createEvent = true),
        swapTargets = listOf(Household("h2", "Foyer Inès"), Household("h3", "Foyer Noé")),
        swapOtherName = if (swapForUs) "Foyer Inès" else "",
    )
}

@FamiliaPreviews
@Composable
private fun TurnDetailScreenPreview() {
    FamiliaTheme { Surface { TurnDetailScreen(previewTurnDetail(), TurnDetailActions(), {}) } }
}
