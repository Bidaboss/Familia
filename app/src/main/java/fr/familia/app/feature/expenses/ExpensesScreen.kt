package fr.familia.app.feature.expenses

import fr.familia.app.core.theme.themeBackground
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import fr.familia.app.core.ui.FamiliaTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.EURO
import fr.familia.app.core.model.Expense
import fr.familia.app.core.model.ExpenseCategory
import fr.familia.app.core.model.ExpenseContainerType
import fr.familia.app.core.model.SplitMode
import fr.familia.app.core.share.ShareContent
import fr.familia.app.core.share.share
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LocalSnackbarHost
import fr.familia.app.core.ui.formatDay
import fr.familia.app.core.ui.showConfirmation
import fr.familia.app.feature.gifts.formatEuros
import fr.familia.app.feature.trips.TripSectionTitle
import java.time.LocalDate
import java.util.Locale
import fr.familia.app.core.ui.LoadingState

/** Whole cents, the French way: « 12,50 € ». */
fun formatCents(cents: Long): String = formatEuros(cents / 100.0)

/** What a member does on the accounts, beyond adding and changing an expense. */
data class ExpensesActions(
    val onConfirm: (TransferRow) -> Unit,
    val onUndo: (SettlementRow) -> Unit,
    val onSetClosed: (Boolean) -> Unit,
    /** The shares typed for each person, by member id; false when one is wrong and the form stays open. */
    val onSaveShares: (Map<String, String>) -> Boolean,
    val onDismissError: () -> Unit,
)

/**
 * The accounts of a trip (PRD rév. 193, récit 16 bis): the balance of each household, « Qui
 * rembourse qui » and the expenses, newest first. Stateless. Nothing is added or changed once the
 * accounts are closed; an expense opens only for its author and the creator of the trip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    state: ExpensesUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onAdd: (() -> Unit)? = null,
    onEdit: ((expenseId: String) -> Unit)? = null,
    actions: ExpensesActions? = null,
    /** The history of an expense this member may not change: who added it, who changed what. */
    onHistory: ((expenseId: String) -> Unit)? = null,
    /** A trip or an event (PRODUIT-57): only a few words change. */
    kind: ExpenseContainerType = ExpenseContainerType.Trip,
    /** « Monnaie » of a trip (récit 112, règle 42): the currency of the budget and the rate of the day. */
    onMoney: (() -> Unit)? = null,
) {
    val isEvent = kind == ExpenseContainerType.Event
    val success = state as? ExpensesUiState.Success
    val snackbar = LocalSnackbarHost.current
    val failedText = stringResource(R.string.expenses_write_failed)
    LaunchedEffect(success?.writeFailed) {
        if (success?.writeFailed == true) {
            actions?.onDismissError?.invoke()
            snackbar.showConfirmation(failedText)
        }
    }
    Scaffold(
        // The background of the theme, as on the redone screens (refonte lot 4).
        containerColor = Color.Transparent,
        modifier = Modifier.themeBackground(),
        topBar = {
            FamiliaTopBar(
                title = { Text(success?.let { stringResource(R.string.expenses_title_of, it.title) } ?: stringResource(if (isEvent) R.string.expenses_title_event else R.string.expenses_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    if (onMoney != null && !isEvent) {
                        IconButton(onClick = onMoney, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Outlined.Euro, contentDescription = stringResource(R.string.money_from_expenses))
                        }
                    }
                    if (success != null) ExportMenu(success)
                },
            )
        },
        floatingActionButton = {
            if (onAdd != null && success != null && !success.closed) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    // The text of an extended button is hidden from TalkBack (Material 3): the icon says it.
                    icon = { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.expenses_add)) },
                    text = { Text(stringResource(R.string.expenses_add)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                ExpensesUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                ExpensesUiState.NotAllowed -> EmptyState(
                    icon = Icons.Outlined.Payments,
                    title = stringResource(R.string.expenses_not_allowed_title),
                    body = stringResource(if (isEvent) R.string.expenses_not_allowed_body_event else R.string.expenses_not_allowed_body),
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
                is ExpensesUiState.Success -> Content(state, onEdit, actions, onHistory, isEvent)
            }
        }
    }
}

@Composable
private fun Content(
    state: ExpensesUiState.Success,
    onEdit: ((String) -> Unit)?,
    actions: ExpensesActions?,
    onHistory: ((String) -> Unit)?,
    isEvent: Boolean,
) {
    var editShares by rememberSaveable { mutableStateOf(false) }
    var confirmClose by rememberSaveable { mutableStateOf(false) }
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
        items(state.transfers, key = { "transfer-${it.fromId}-${it.toId}" }) { row ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.expenses_transfer, row.fromName, formatCents(row.amountCents), row.toName),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                if (actions != null && row.canConfirm) {
                    OutlinedButton(onClick = { actions.onConfirm(row) }, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.expenses_settle))
                    }
                }
            }
        }
        if (state.settlements.isNotEmpty()) {
            item(key = "settled-title") { TripSectionTitle(stringResource(R.string.expenses_settled_title)) }
            items(state.settlements, key = { "settlement-${it.id}" }) { row ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.expenses_settled_line, row.fromName, formatCents(row.amountCents), row.toName, row.byName),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (actions != null && row.canUndo) {
                        val undoLabel = stringResource(R.string.expenses_settled_undo, row.fromName, row.toName)
                        TextButton(
                            onClick = { actions.onUndo(row) },
                            modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = undoLabel },
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
            }
        }
        item(key = "expenses-title") { TripSectionTitle(stringResource(R.string.expenses_list)) }
        if (state.expenses.isEmpty()) {
            item(key = "no-expense") { Hint(stringResource(if (isEvent) R.string.expenses_empty_event else R.string.expenses_empty)) }
        }
        items(state.expenses, key = { "expense-${it.expense.id}" }) { row ->
            val open = if (row.canEdit) onEdit else onHistory
            ExpenseLine(row, open?.let { go -> { go(row.expense.id) } })
        }
        if (actions != null && state.isCreator) {
            item(key = "creator-title") { TripSectionTitle(stringResource(R.string.expenses_creator_title)) }
            item(key = "creator") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.closed) {
                        OutlinedButton(onClick = { editShares = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.expenses_edit_shares))
                        }
                        OutlinedButton(onClick = { confirmClose = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.expenses_close))
                        }
                    } else {
                        OutlinedButton(onClick = { actions.onSetClosed(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.expenses_reopen))
                        }
                    }
                }
            }
        }
    }
    if (actions != null && editShares && !state.closed) {
        SharesDialog(state.shareGroups, state.sharesByHousehold, onSave = actions.onSaveShares, onDismiss = { editShares = false })
    }
    if (actions != null && confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(stringResource(R.string.expenses_close_title)) },
            text = { Text(stringResource(R.string.expenses_close_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; actions.onSetClosed(true) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.expenses_close))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * The shares of each person, typed by the creator under the name of their household: from 0 to 10,
 * by halves, 1 by default. Each household shows the sum of its people, which the balance uses.
 */
@Composable
private fun SharesDialog(groups: List<ShareGroup>, byHousehold: Boolean, onSave: (Map<String, String>) -> Boolean, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf(groups.flatMap { it.people }.associate { it.id to shareText(it.shares) }) }
    val sums = groups.associate { g -> g.householdId to g.people.map { parsePersonShare(typed[it.id].orEmpty()) } }
    val allValid = sums.values.all { values -> values.all { it != null } && values.sumOf { it ?: 0.0 } >= SHARE_MIN }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.expenses_shares_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.expenses_shares_body), style = MaterialTheme.typography.bodyMedium)
                if (byHousehold) {
                    // A book opened before the shares by person: saving replaces the shares of each household.
                    Text(stringResource(R.string.expenses_shares_legacy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                groups.forEach { g ->
                    val values = sums[g.householdId].orEmpty()
                    val total = values.sumOf { it ?: 0.0 }
                    Text(
                        // French: « 0,5 part », « 1 part », « 1,5 parts ».
                        pluralStringResource(R.plurals.expenses_shares_household, if (total < 2.0) 1 else 2, g.name, shareText(total)),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (values.all { it != null } && total < SHARE_MIN) {
                        Text(stringResource(R.string.expenses_shares_household_empty), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    g.people.forEach { p ->
                        val value = typed[p.id].orEmpty()
                        val wrong = parsePersonShare(value) == null
                        OutlinedTextField(
                            value = value,
                            onValueChange = { typed = typed + (p.id to it) },
                            label = { Text(p.firstName) },
                            singleLine = true,
                            isError = wrong,
                            supportingText = if (wrong) {
                                { Text(stringResource(R.string.expenses_shares_error)) }
                            } else {
                                null
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (onSave(typed)) onDismiss() },
                enabled = allValid,
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.member_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun BalanceRow(row: HouseholdRow) {
    ClayCard(Modifier.fillMaxWidth()) {
        Text(row.name, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.expenses_shares, shareText(row.shares)), style = MaterialTheme.typography.bodyMedium)
        if (row.people.isNotEmpty()) {
            Text(
                row.people.joinToString(" · ") { "${it.firstName} ${shareText(it.shares)}" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

/** « Partager les comptes »: as a table for a spreadsheet, or as an Argile image. */
@Composable
private fun ExportMenu(state: ExpensesUiState.Success) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 56.dp)) {
            Icon(Icons.Outlined.Share, contentDescription = null)
            Text(stringResource(R.string.share_button), modifier = Modifier.padding(start = 8.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.expenses_export_csv)) },
                onClick = { open = false; exportCsv(context, state) },
                modifier = Modifier.heightIn(min = 56.dp),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.expenses_export_image)) },
                onClick = { open = false; exportImage(context, state) },
                modifier = Modifier.heightIn(min = 56.dp),
            )
        }
    }
}

private fun exportCsv(context: Context, state: ExpensesUiState.Success) {
    val csv = expensesCsv(state, { context.getString(categoryRes(it)) }, { context.getString(splitRes(it)) })
    val title = context.getString(R.string.expenses_title_of, state.title)
    if (!shareCsv(context, csv, csvFileName(state.title), title, context.getString(R.string.share_chooser))) {
        Toast.makeText(context, R.string.share_no_app, Toast.LENGTH_LONG).show()
    }
}

private fun exportImage(context: Context, state: ExpensesUiState.Success) {
    val lines = expensesImageLines(
        state,
        total = context.getString(R.string.expenses_total, formatCents(state.totalCents)),
        transfer = { context.getString(R.string.expenses_transfer, it.fromName, formatCents(it.amountCents), it.toName) },
        allSettled = context.getString(R.string.expenses_all_settled),
        more = { context.resources.getQuantityString(R.plurals.expenses_more_transfers, it, it) },
    )
    val title = context.getString(R.string.expenses_title_of, state.title)
    share(context, ShareContent(title = title, lines = lines, text = (listOf(title) + lines).joinToString("\n"), link = ""))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun categoryLabel(category: ExpenseCategory): String = stringResource(categoryRes(category))

@StringRes
fun categoryRes(category: ExpenseCategory): Int = when (category) {
    ExpenseCategory.Lodging -> R.string.expense_category_lodging
    ExpenseCategory.Transport -> R.string.expense_category_transport
    ExpenseCategory.Groceries -> R.string.expense_category_groceries
    ExpenseCategory.Restaurant -> R.string.expense_category_restaurant
    ExpenseCategory.Activity -> R.string.expense_category_activity
    ExpenseCategory.Other -> R.string.expense_category_other
}

@StringRes
fun splitRes(mode: SplitMode): Int = when (mode) {
    SplitMode.Shares -> R.string.expense_split_shares
    SplitMode.Equal -> R.string.expense_split_equal
    SplitMode.Amounts -> R.string.expense_split_amounts
}

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
        transfers = listOf(TransferRow("h2", "h1", "Foyer Lune", "Foyer Soleil", 2_415, canConfirm = true)),
        expenses = listOf(ExpenseRow(groceries, "Camille", canEdit = true)),
    )
    FamiliaTheme { Surface { ExpensesScreen(state, {}, {}, onAdd = {}) } }
}
