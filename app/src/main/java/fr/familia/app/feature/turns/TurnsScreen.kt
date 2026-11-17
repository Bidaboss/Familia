package fr.familia.app.feature.turns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Turn
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.core.ui.PendingSign

/** A household's name, or « Foyer retiré » for one removed meanwhile. */
@Composable
internal fun householdLabel(name: String): String = name.ifBlank { stringResource(R.string.turns_household_removed) }

/** « À qui le tour ? » in Entraide (PRD, récit 58): the rotations, mine first. Stateless. */
@Composable
fun TurnsScreen(
    state: TurnsUiState,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        when (state) {
            TurnsUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
            TurnsUiState.Error -> EmptyState(
                icon = Icons.Outlined.Autorenew,
                title = stringResource(R.string.turns_error_title),
                body = stringResource(R.string.turns_error_body),
                action = {
                    Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_retry)) }
                },
            )
            is TurnsUiState.Success ->
                if (state.rows.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Autorenew,
                        title = stringResource(R.string.turns_empty_title),
                        body = stringResource(R.string.turns_empty_body),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = ClaySpacing.screen, top = 16.dp, end = ClaySpacing.screen, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
                    ) {
                        items(state.rows, key = { it.turn.id }) { row -> TurnCard(row, onOpen) }
                    }
                }
        }
        if (state is TurnsUiState.Success && state.canCreate) {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.turns_create)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun TurnCard(row: TurnRow, onOpen: (String) -> Unit) {
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpen(row.turn.id) },
        onClickLabel = stringResource(R.string.turns_open),
    ) {
        if (row.isMine || row.swapForMe) {
            Text(
                stringResource(if (row.isMine) R.string.turns_badge_yours else R.string.turns_badge_swap),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        ClayCardTitle(row.turn.title, style = MaterialTheme.typography.titleMedium)
        Text(
            if (row.isMine) stringResource(R.string.turns_now_us) else stringResource(R.string.turns_now, householdLabel(row.nowName)),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
        if (row.turn.pending) PendingSign(color = muted)
    }
}

/** Fictional data for previews and screenshots. */
internal fun previewTurns(): TurnsUiState.Success {
    fun t(id: String, title: String) = Turn(id, title, listOf("h1", "h2"), 0, emptyList(), null, "camille")
    return TurnsUiState.Success(
        rows = listOf(
            TurnRow(t("t1", "Qui reçoit Noël"), "Foyer Camille", isMine = true, swapForMe = false),
            TurnRow(t("t2", "Garder le chien cet été"), "Foyer Inès", isMine = false, swapForMe = false),
        ),
        canCreate = true,
    )
}

@FamiliaPreviews
@Composable
private fun TurnsScreenPreview() {
    FamiliaTheme { Surface { TurnsScreen(previewTurns(), {}, {}, {}) } }
}
