package fr.familia.app.feature.expenses

import fr.familia.app.core.theme.themeBackground
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import fr.familia.app.core.ui.FamiliaTopBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.model.ExpenseHistoryAction
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.formatDay
import fr.familia.app.core.ui.formatTime
import java.time.LocalDateTime
import java.util.Locale
import fr.familia.app.core.ui.LoadingState

/** The history of an expense (récit 16 bis): who added it, changed it, and what. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseHistoryScreen(state: ExpenseHistoryUiState, onRetry: () -> Unit, onBack: () -> Unit) {
    val success = state as? ExpenseHistoryUiState.Success
    Scaffold(
        // The background of the theme, as on the redone screens (refonte lot 4).
        containerColor = Color.Transparent,
        modifier = Modifier.themeBackground(),
        topBar = {
            FamiliaTopBar(
                title = {
                    Text(
                        success?.title?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.expense_history_title_of, it) }
                            ?: stringResource(R.string.expense_history_title),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                ExpenseHistoryUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                ExpenseHistoryUiState.Error -> EmptyState(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.expenses_error_title),
                    body = stringResource(R.string.expense_history_error),
                    action = {
                        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_retry)) }
                    },
                )
                is ExpenseHistoryUiState.Success -> if (state.rows.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.History,
                        title = stringResource(R.string.expense_history_title),
                        body = stringResource(R.string.expense_history_empty),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.rows, key = { it.id }) { HistoryLine(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryLine(row: HistoryRow) {
    val who = row.byName.ifBlank { stringResource(R.string.expense_history_someone) }
    val what = when (row.action) {
        ExpenseHistoryAction.Created -> stringResource(R.string.expense_history_created, who, formatCents(row.amountCents))
        ExpenseHistoryAction.Deleted -> stringResource(R.string.expense_history_deleted, who)
        ExpenseHistoryAction.Edited -> if (row.fields.isEmpty()) {
            stringResource(R.string.expense_history_edited_plain, who, formatCents(row.amountCents))
        } else {
            stringResource(
                R.string.expense_history_edited,
            who,
                row.fields.map { fieldLabel(it) }.joinToString(", "),
                formatCents(row.amountCents),
            )
        }
    }
    ClayCard(Modifier.fillMaxWidth()) {
        Text(what, style = MaterialTheme.typography.bodyLarge)
        row.at?.let {
            Text(
                stringResource(R.string.expense_history_when, formatDay(it.toLocalDate(), familyToday()), formatTime(it.toLocalTime())),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun fieldLabel(field: ExpenseField): String = stringResource(
    when (field) {
        ExpenseField.Title -> R.string.expense_field_title
        ExpenseField.Amount -> R.string.expense_field_amount
        ExpenseField.Date -> R.string.expense_field_date
        ExpenseField.Payer -> R.string.expense_field_payer
        ExpenseField.Category -> R.string.expense_field_category
        ExpenseField.Split -> R.string.expense_field_split
        ExpenseField.Currency -> R.string.expense_field_currency
    },
).lowercase(Locale.FRANCE)

@FamiliaPreviews
@Composable
private fun ExpenseHistoryScreenPreview() {
    val state = ExpenseHistoryUiState.Success(
        "Courses du marché",
        listOf(
            HistoryRow("2", ExpenseHistoryAction.Edited, "Camille", 8_450, listOf(ExpenseField.Amount, ExpenseField.Split), LocalDateTime.of(2027, 7, 12, 19, 5)),
            HistoryRow("1", ExpenseHistoryAction.Created, "Camille", 8_000, emptyList(), LocalDateTime.of(2027, 7, 12, 11, 30)),
        ),
    )
    FamiliaTheme { Surface { ExpenseHistoryScreen(state, {}, {}) } }
}
