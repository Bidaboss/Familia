package fr.familia.app.feature.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Attractions
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.EURO
import fr.familia.app.core.model.ExpenseCategory
import fr.familia.app.core.model.ExpenseContainerType
import fr.familia.app.core.model.SplitMode
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.LocalClayColors
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.core.ui.form.CategoryTiles
import fr.familia.app.core.ui.form.ChipGroup
import fr.familia.app.core.ui.form.FormChip
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormLabel
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.form.PeopleRow
import fr.familia.app.core.ui.form.PersonChoice
import fr.familia.app.core.ui.form.StickyActionBar
import fr.familia.app.feature.trips.TripCheckRow
import fr.familia.app.feature.trips.TripDayBox
import fr.familia.app.feature.trips.TripDeleteButton
import java.time.LocalDate

/** Add or change an expense of a trip (récit 16 bis). Stateless. */
@Composable
fun ExpenseEditScreen(
    state: ExpenseEditUiState,
    onChange: ((ExpenseForm) -> ExpenseForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    /** The history of the expense being changed; null for a new one. */
    onHistory: (() -> Unit)? = null,
    /** A trip or an event (PRODUIT-57): only a few words change. */
    kind: ExpenseContainerType = ExpenseContainerType.Trip,
) {
    LaunchedEffect(state) { if (state == ExpenseEditUiState.Done) onDone() }
    val editing = state as? ExpenseEditUiState.Editing
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.themeBackground(),
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(if (editing?.isNew == false) R.string.expense_edit_title else R.string.expenses_add), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onDone) },
            )
        },
        // The single black button at the bottom (refonte lot 4, maquette « Une dépense »).
        bottomBar = {
            if (editing != null) {
                StickyActionBar(
                    text = stringResource(if (editing.isNew) R.string.expenses_add else R.string.member_save),
                    onClick = onSave,
                    enabled = !editing.isSaving,
                    busy = editing.isSaving,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                ExpenseEditUiState.Loading, ExpenseEditUiState.Done -> LoadingState(Modifier.align(Alignment.Center))
                ExpenseEditUiState.Unavailable -> EmptyState(
                    icon = Icons.Outlined.Payments,
                    title = stringResource(R.string.expense_unavailable_title),
                    body = stringResource(
                        if (kind == ExpenseContainerType.Event) R.string.expense_unavailable_body_event else R.string.expense_unavailable_body,
                    ),
                )
                is ExpenseEditUiState.Editing -> Form(state, onChange, onDelete, onHistory)
            }
        }
    }
}

/** The tiles of the maquette: the expense, its category, who paid, which households share it. */
@Composable
private fun Form(
    state: ExpenseEditUiState.Editing,
    onChange: ((ExpenseForm) -> ExpenseForm) -> Unit,
    onDelete: () -> Unit,
    onHistory: (() -> Unit)?,
) {
    val form = state.form
    val errors = state.errors
    val scroll = rememberScrollState()
    // Where each tile starts, to bring the first one in error into view (recette commune n° 3).
    val tops = remember { mutableStateMapOf<ExpenseTile, Int>() }
    LaunchedEffect(errors) {
        val first = ExpenseTile.entries.firstOrNull { tile -> errors.any { it.tile == tile } } ?: return@LaunchedEffect
        tops[first]?.let { scroll.animateScrollTo(it) }
    }
    fun Modifier.top(tile: ExpenseTile) = onGloballyPositioned { tops[tile] = it.positionInParent().y.toInt() }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FormTile(Icons.Outlined.Euro, stringResource(R.string.expense_tile_main), Modifier.top(ExpenseTile.Main)) {
            Field(
                label = stringResource(R.string.expense_title_field),
                value = form.title,
                onValue = { value -> onChange { it.copy(title = value.take(EXPENSE_TITLE_MAX)) } },
                error = errorMessage(errors, ExpenseFormError.Title),
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(
                    label = stringResource(R.string.expense_amount_field),
                    value = form.amount,
                    onValue = { value -> onChange { it.copy(amount = value) } },
                    error = errorMessage(errors, ExpenseFormError.Amount),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Field(
                    label = stringResource(R.string.expense_currency_field),
                    value = form.currency,
                    onValue = { value -> onChange { it.copy(currency = value.uppercase().take(3)) } },
                    error = errorMessage(errors, ExpenseFormError.Currency),
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.width(112.dp),
                )
            }
            if (form.currency.trim().uppercase() != EURO) {
                Field(
                    label = stringResource(R.string.expense_rate_field, form.currency.trim().uppercase()),
                    value = form.rate,
                    onValue = { value -> onChange { it.copy(rate = value) } },
                    error = errorMessage(errors, ExpenseFormError.Rate),
                    hint = stringResource(R.string.expense_rate_hint),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            TripDayBox(
                label = stringResource(R.string.expense_date_field),
                date = form.date,
                onPick = { day -> onChange { it.copy(date = day) } },
                modifier = Modifier.fillMaxWidth(),
            )
            errorMessage(errors, ExpenseFormError.Date)?.let { FormHint(it, isError = true) }
        }

        FormTile(Icons.Outlined.ShoppingBasket, stringResource(R.string.expense_category)) {
            CategoryTiles(
                options = ExpenseCategory.entries,
                selected = form.category,
                onSelect = { c -> onChange { it.copy(category = c) } },
                label = { categoryLabel(it) },
                icon = ::categoryIcon,
            )
        }

        FormTile(Icons.Outlined.Group, stringResource(R.string.expense_payer), Modifier.top(ExpenseTile.Payer)) {
            PeopleRow(
                choices = state.payers.map { PersonChoice(it.id, it.name, selected = it.id == form.payerId, memberId = it.id) },
                multiple = false,
                onToggle = { id -> onChange { it.copy(payerId = id) } },
            )
            errorMessage(errors, ExpenseFormError.Payer)?.let { FormHint(it, isError = true) }
        }

        FormTile(Icons.Outlined.Home, stringResource(R.string.expense_split), Modifier.top(ExpenseTile.Split)) {
            ChipGroup {
                SplitMode.entries.forEach { mode ->
                    FormChip(splitLabel(mode), form.mode == mode, onClick = { onChange { it.copy(mode = mode) } })
                }
            }
            state.households.forEach { household ->
                val checked = household.id in form.householdIds
                TripCheckRow(household.name, checked, onCheckedChange = { on ->
                    onChange { it.copy(householdIds = if (on) it.householdIds + household.id else it.householdIds - household.id) }
                })
                if (checked && form.mode == SplitMode.Amounts) {
                    Field(
                        label = stringResource(R.string.expense_household_amount, household.name),
                        value = form.amounts[household.id].orEmpty(),
                        onValue = { value -> onChange { it.copy(amounts = it.amounts + (household.id to value)) } },
                        error = null,
                        isError = ExpenseFormError.Amounts in errors,
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
            errorMessage(errors, ExpenseFormError.Households, ExpenseFormError.Amounts)?.let { FormHint(it, isError = true) }
        }

        if (state.saveFailed) {
            Surface(shape = RoundedCornerShape(16.dp), color = LocalClayColors.current.card) {
                FormHint(stringResource(R.string.trip_save_failed), isError = true, modifier = Modifier.padding(14.dp))
            }
        }
        if (!state.isNew && onHistory != null) {
            ClayLink(stringResource(R.string.expense_history_open), onClick = onHistory)
        }
        // « Supprimer » at the very bottom, outlined in pale red (recette commune n° 7).
        if (!state.isNew) DeleteButton(form.title, enabled = !state.isSaving, onDelete)
        Spacer(Modifier.height(8.dp))
    }
}

/** A label, the field in its tile, then its help or its error (recette commune n° 3). */
@Composable
private fun Field(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    error: String?,
    keyboard: KeyboardOptions,
    modifier: Modifier = Modifier,
    hint: String? = null,
    isError: Boolean = error != null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FormLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            isError = isError,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = keyboard,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
        (error ?: hint)?.let { FormHint(it, isError = error != null) }
    }
}

/** The tiles that can hold an error, top to bottom. */
private enum class ExpenseTile { Main, Payer, Split }

private val ExpenseFormError.tile: ExpenseTile
    get() = when (this) {
        ExpenseFormError.Title, ExpenseFormError.Amount, ExpenseFormError.Currency, ExpenseFormError.Rate, ExpenseFormError.Date -> ExpenseTile.Main
        ExpenseFormError.Payer -> ExpenseTile.Payer
        ExpenseFormError.Households, ExpenseFormError.Amounts -> ExpenseTile.Split
    }

/** The drawing of each category, in the tiles of the form. */
internal fun categoryIcon(category: ExpenseCategory): ImageVector = when (category) {
    ExpenseCategory.Lodging -> Icons.Outlined.Hotel
    ExpenseCategory.Transport -> Icons.Outlined.DirectionsCar
    ExpenseCategory.Groceries -> Icons.Outlined.ShoppingCart
    ExpenseCategory.Restaurant -> Icons.Outlined.Restaurant
    ExpenseCategory.Activity -> Icons.Outlined.Attractions
    ExpenseCategory.Other -> Icons.Outlined.MoreHoriz
}

@Composable
private fun DeleteButton(title: String, enabled: Boolean, onDelete: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    TripDeleteButton(stringResource(R.string.expense_delete), enabled = enabled, onClick = { confirm = true })
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
private fun errorMessage(errors: Set<ExpenseFormError>, vararg kinds: ExpenseFormError): String? {
    val error = kinds.firstOrNull { it in errors } ?: return null
    return stringResource(
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
