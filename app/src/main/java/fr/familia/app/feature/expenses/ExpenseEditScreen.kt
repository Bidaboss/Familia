package fr.familia.app.feature.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.EURO
import fr.familia.app.core.model.ExpenseCategory
import fr.familia.app.core.model.SplitMode
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.trips.TripCheckRow
import fr.familia.app.feature.trips.TripDateButton
import fr.familia.app.feature.trips.TripSectionTitle
import java.time.LocalDate

/** Add or change an expense of a trip (récit 16 bis). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditScreen(
    state: ExpenseEditUiState,
    onChange: ((ExpenseForm) -> ExpenseForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    /** The history of the expense being changed; null for a new one. */
    onHistory: (() -> Unit)? = null,
) {
    LaunchedEffect(state) { if (state == ExpenseEditUiState.Done) onDone() }
    val editing = state as? ExpenseEditUiState.Editing
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editing?.isNew == false) R.string.expense_edit_title else R.string.expenses_add)) },
                navigationIcon = { BackButton(onClick = onDone) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                ExpenseEditUiState.Loading, ExpenseEditUiState.Done -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ExpenseEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.Payments,
                    title = stringResource(R.string.expense_unavailable_title),
                    body = stringResource(R.string.expense_unavailable_body),
                )
                is ExpenseEditUiState.Editing -> Form(state, onChange, onSave, onDelete, onHistory)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(
    state: ExpenseEditUiState.Editing,
    onChange: ((ExpenseForm) -> ExpenseForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onHistory: (() -> Unit)?,
) {
    val form = state.form
    val errors = state.errors
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = form.title,
            onValueChange = { value -> onChange { it.copy(title = value.take(EXPENSE_TITLE_MAX)) } },
            label = { Text(stringResource(R.string.expense_title_field)) },
            isError = ExpenseFormError.Title in errors,
            supportingText = errorText(errors, ExpenseFormError.Title),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = form.amount,
                onValueChange = { value -> onChange { it.copy(amount = value) } },
                label = { Text(stringResource(R.string.expense_amount_field)) },
                isError = ExpenseFormError.Amount in errors,
                supportingText = errorText(errors, ExpenseFormError.Amount),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = form.currency,
                onValueChange = { value -> onChange { it.copy(currency = value.uppercase().take(3)) } },
                label = { Text(stringResource(R.string.expense_currency_field)) },
                isError = ExpenseFormError.Currency in errors,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.width(112.dp),
            )
        }
        if (form.currency.trim().uppercase() != EURO) {
            OutlinedTextField(
                value = form.rate,
                onValueChange = { value -> onChange { it.copy(rate = value) } },
                label = { Text(stringResource(R.string.expense_rate_field, form.currency.trim().uppercase())) },
                isError = ExpenseFormError.Rate in errors,
                supportingText = errorText(errors, ExpenseFormError.Rate) ?: { Text(stringResource(R.string.expense_rate_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TripDateButton(form.date, stringResource(R.string.expense_date_field), onPick = { day -> onChange { it.copy(date = day) } })

        TripSectionTitle(stringResource(R.string.expense_payer))
        Chips(state.payers.map { it.id to it.name }, form.payerId) { id -> onChange { it.copy(payerId = id) } }
        errorText(errors, ExpenseFormError.Payer)?.invoke()

        TripSectionTitle(stringResource(R.string.expense_category))
        Chips(ExpenseCategory.entries.map { it to categoryLabel(it) }, form.category) { c -> onChange { it.copy(category = c) } }

        TripSectionTitle(stringResource(R.string.expense_split))
        Chips(SplitMode.entries.map { it to splitLabel(it) }, form.mode) { m -> onChange { it.copy(mode = m) } }
        state.households.forEach { household ->
            val checked = household.id in form.householdIds
            TripCheckRow(household.name, checked, onCheckedChange = { on ->
                onChange { it.copy(householdIds = if (on) it.householdIds + household.id else it.householdIds - household.id) }
            })
            if (checked && form.mode == SplitMode.Amounts) {
                OutlinedTextField(
                    value = form.amounts[household.id].orEmpty(),
                    onValueChange = { value -> onChange { it.copy(amounts = it.amounts + (household.id to value)) } },
                    label = { Text(stringResource(R.string.expense_household_amount, household.name)) },
                    isError = ExpenseFormError.Amounts in errors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        errorText(errors, ExpenseFormError.Households, ExpenseFormError.Amounts)?.invoke()

        if (state.saveFailed) {
            Text(stringResource(R.string.trip_save_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            if (state.isSaving) {
                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(R.string.member_save))
            }
        }
        if (!state.isNew && onHistory != null) {
            OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.expense_history_open))
            }
        }
        if (!state.isNew) DeleteButton(form.title, enabled = !state.isSaving, onDelete)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T?, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isOn = value == selected
            FilterChip(
                selected = isOn,
                onClick = { onPick(value) },
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = if (isOn) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                modifier = Modifier.heightIn(min = 56.dp),
            )
        }
    }
}

@Composable
private fun DeleteButton(title: String, enabled: Boolean, onDelete: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { confirm = true },
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.expense_delete)) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.expense_delete_title, title.trim())) },
            text = { Text(stringResource(R.string.expense_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDelete() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.event_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_keep)) }
            },
        )
    }
}

@Composable
private fun splitLabel(mode: SplitMode): String = stringResource(
    when (mode) {
        SplitMode.Shares -> R.string.expense_split_shares
        SplitMode.Equal -> R.string.expense_split_equal
        SplitMode.Amounts -> R.string.expense_split_amounts
    },
)

@Composable
private fun errorText(errors: Set<ExpenseFormError>, vararg kinds: ExpenseFormError): (@Composable () -> Unit)? {
    val error = kinds.firstOrNull { it in errors } ?: return null
    val message = stringResource(
        when (error) {
            ExpenseFormError.Title -> R.string.expense_error_title
            ExpenseFormError.Amount -> R.string.expense_error_amount
            ExpenseFormError.Currency -> R.string.expense_error_currency
            ExpenseFormError.Rate -> R.string.expense_error_rate
            ExpenseFormError.Date -> R.string.expense_error_date
            ExpenseFormError.Payer -> R.string.expense_error_payer
            ExpenseFormError.Households -> R.string.expense_error_households
            ExpenseFormError.Amounts -> R.string.expense_error_amounts
        },
    )
    return { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}

@FamiliaPreviews
@Composable
private fun ExpenseEditScreenPreview() {
    val state = ExpenseEditUiState.Editing(
        form = ExpenseForm("Plein d'essence", "72,40", date = LocalDate.of(2027, 7, 11), payerId = "adult1", category = ExpenseCategory.Transport, householdIds = setOf("h1", "h2")),
        isNew = false,
        payers = listOf(ExpenseChoice("adult1", "Camille"), ExpenseChoice("adult2", "Sacha")),
        households = listOf(ExpenseChoice("h1", "Foyer Soleil"), ExpenseChoice("h2", "Foyer Lune")),
    )
    FamiliaTheme { Surface { ExpenseEditScreen(state, {}, {}, {}, {}) } }
}
