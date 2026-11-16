package fr.familia.app.feature.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.EURO
import fr.familia.app.core.model.Expense
import fr.familia.app.core.model.ExpenseCategory
import fr.familia.app.core.model.SplitMode
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.formatDay
import fr.familia.app.feature.gifts.formatEuros
import fr.familia.app.feature.trips.TripSectionTitle
import java.time.LocalDate
import java.util.Locale

/** Whole cents, the French way: « 12,50 € ». */
fun formatCents(cents: Long): String = formatEuros(cents / 100.0)

/**
 * The accounts of a trip (PRD rév. 193, récit 16 bis): the balance of each household, « Qui
 * rembourse qui » and the expenses, newest first. Stateless. [onAdd] and [onEdit] are null until
 * the expense form exists, and while the accounts are closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    state: ExpensesUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onAdd: (() -> Unit)? = null,
    onEdit: ((expenseId: String) -> Unit)? = null,
) {
    val success = state as? ExpensesUiState.Success
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(success?.let { stringResource(R.string.expenses_title_of, it.title) } ?: stringResource(R.string.expenses_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
        floatingActionButton = {
            if (onAdd != null && success != null && !success.closed) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.expenses_add)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                ExpensesUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ExpensesUiState.NotAllowed -> EmptyState(
                    icon = Icons.Outlined.Payments,
                    title = stringResource(R.string.expenses_not_allowed_title),
                    body = stringResource(R.string.expenses_not_allowed_body),
                )
                ExpensesUiState.Error -> EmptyState(
                    icon = Icons.Outlined.Payments,
                    title = stringResource(R.string.expenses_error_title),
                    body = stringResource(R.string.expenses_error_body),
                    action = {
                        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.action_retry))
                        }
                    },
                )
                is ExpensesUiState.Success -> Content(state, onEdit)
            }
        }
    }
}

@Composable
private fun Content(state: ExpensesUiState.Success, onEdit: ((String) -> Unit)?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Room at the bottom so the add button never hides the last expense.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary") {
            ClayCard(Modifier.fillMaxWidth(), tint = ClayTint.Family) {
                Text(stringResource(R.string.expenses_total, formatCents(state.totalCents)), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (state.closed) R.string.expenses_closed else R.string.expenses_no_payment),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item(key = "balance-title") { TripSectionTitle(stringResource(R.string.expenses_balance)) }
        items(state.households, key = { "household-${it.id}" }) { BalanceRow(it) }
        item(key = "transfers-title") { TripSectionTitle(stringResource(R.string.expenses_transfers)) }
        if (state.transfers.isEmpty()) {
            item(key = "settled") { Hint(stringResource(R.string.expenses_all_settled)) }
        }
        items(state.transfers, key = { "transfer-${it.fromName}-${it.toName}" }) {
            Text(
                stringResource(R.string.expenses_transfer, it.fromName, formatCents(it.amountCents), it.toName),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        item(key = "expenses-title") { TripSectionTitle(stringResource(R.string.expenses_list)) }
        if (state.expenses.isEmpty()) {
            item(key = "no-expense") { Hint(stringResource(R.string.expenses_empty)) }
        }
        items(state.expenses, key = { "expense-${it.expense.id}" }) { row ->
            ExpenseLine(row, onEdit?.takeIf { row.canEdit }?.let { edit -> { edit(row.expense.id) } })
        }
    }
}

@Composable
private fun BalanceRow(row: HouseholdRow) {
    ClayCard(Modifier.fillMaxWidth()) {
        Text(row.name, style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.expenses_paid_owed, formatCents(row.paidCents), formatCents(row.owedCents)),
            style = MaterialTheme.typography.bodyMedium,
        )
        val net = when {
            row.netCents > 0 -> stringResource(R.string.expenses_to_receive, formatCents(row.netCents))
            row.netCents < 0 -> stringResource(R.string.expenses_to_pay, formatCents(-row.netCents))
            else -> stringResource(R.string.expenses_even)
        }
        Text(
            net,
            style = MaterialTheme.typography.bodyLarge,
            color = if (row.netCents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ExpenseLine(row: ExpenseRow, onClick: (() -> Unit)?) {
    val e = row.expense
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.expenses_line_detail, formatDay(e.date, e.date), row.payerName, categoryLabel(e.category)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatCents(e.amountCents), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End)
            if (e.currency != EURO) {
                Text(
                    stringResource(R.string.expenses_original, String.format(Locale.FRANCE, "%.2f", e.originalCents / 100.0), e.currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun categoryLabel(category: ExpenseCategory): String = stringResource(
    when (category) {
        ExpenseCategory.Lodging -> R.string.expense_category_lodging
        ExpenseCategory.Transport -> R.string.expense_category_transport
        ExpenseCategory.Groceries -> R.string.expense_category_groceries
        ExpenseCategory.Restaurant -> R.string.expense_category_restaurant
        ExpenseCategory.Activity -> R.string.expense_category_activity
        ExpenseCategory.Other -> R.string.expense_category_other
    },
)

@FamiliaPreviews
@Composable
private fun ExpensesScreenPreview() {
    val groceries = Expense(
        "e1", "Courses du marché", 8_450, LocalDate.of(2027, 7, 12), "adult1", "h1",
        ExpenseCategory.Groceries, SplitMode.Shares, listOf("h1", "h2"), authorId = "adult1",
    )
    val state = ExpensesUiState.Success(
        title = "Corse", bookId = "trip_t1", closed = false, isCreator = true, totalCents = 8_450,
        households = listOf(
            HouseholdRow("h1", "Foyer Soleil", 2.5, 8_450, 6_035, 2_415),
            HouseholdRow("h2", "Foyer Lune", 1.0, 0, 2_415, -2_415),
        ),
        transfers = listOf(TransferRow("Foyer Lune", "Foyer Soleil", 2_415)),
        expenses = listOf(ExpenseRow(groceries, "Camille", canEdit = true)),
    )
    FamiliaTheme { Surface { ExpensesScreen(state, {}, {}, onAdd = {}) } }
}
