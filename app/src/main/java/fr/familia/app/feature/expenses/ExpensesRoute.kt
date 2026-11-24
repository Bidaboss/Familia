package fr.familia.app.feature.expenses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.ExpenseContainer
import fr.familia.app.core.model.ExpenseContainerType
import fr.familia.app.core.model.Member
import kotlinx.serialization.Serializable

/** The accounts of the trip [tripId] (récit 16 bis). */
@Serializable
data class TripExpensesDestination(val tripId: String)

/** The form of an expense of [tripId]: a new one when [expenseId] is null. */
@Serializable
data class TripExpenseEditDestination(val tripId: String, val expenseId: String? = null)

/** The history of the expense [expenseId] of [tripId]. */
@Serializable
data class TripExpenseHistoryDestination(val tripId: String, val expenseId: String)

/** The accounts of the event [eventId], a weekend or a meal (PRODUIT-57). */
@Serializable
data class EventExpensesDestination(val eventId: String)

/** The form of an expense of the event [eventId]: a new one when [expenseId] is null. */
@Serializable
data class EventExpenseEditDestination(val eventId: String, val expenseId: String? = null)

/** The history of the expense [expenseId] of the event [eventId]. */
@Serializable
data class EventExpenseHistoryDestination(val eventId: String, val expenseId: String)

private fun AppContainer?.contextOf(of: ExpenseContainer): ExpenseContextSource? =
    this?.let { expenseContextSource(of, it.tripRepository, it.eventRepository, it.rsvpRepository) }

/** Connects the accounts of a trip to their ViewModel. */
@Composable
fun ExpensesRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String,
    onAdd: () -> Unit,
    onEdit: (expenseId: String) -> Unit,
    onHistory: (expenseId: String) -> Unit,
    onBack: () -> Unit,
    /** « Monnaie » of the trip (récit 112, règle 42), from the top bar. */
    onMoney: (() -> Unit)? = null,
) = ExpensesRoute(container, currentMember, ExpenseContainer(ExpenseContainerType.Trip, tripId), onAdd, onEdit, onHistory, onBack, onMoney)

/** Connects the accounts of an event to their ViewModel. */
@Composable
fun EventExpensesRoute(
    container: AppContainer?,
    currentMember: Member,
    eventId: String,
    onAdd: () -> Unit,
    onEdit: (expenseId: String) -> Unit,
    onHistory: (expenseId: String) -> Unit,
    onBack: () -> Unit,
) = ExpensesRoute(container, currentMember, ExpenseContainer(ExpenseContainerType.Event, eventId), onAdd, onEdit, onHistory, onBack)

@Composable
private fun ExpensesRoute(
    container: AppContainer?,
    currentMember: Member,
    of: ExpenseContainer,
    onAdd: () -> Unit,
    onEdit: (expenseId: String) -> Unit,
    onHistory: (expenseId: String) -> Unit,
    onBack: () -> Unit,
    onMoney: (() -> Unit)? = null,
) {
    val viewModel: ExpensesViewModel = viewModel(
        key = "expenses-${of.bookId}",
        factory = viewModelFactory {
            initializer { ExpensesViewModel(container?.expenseRepository, container.contextOf(of), container?.memberRepository, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        ExpensesActions(
            onConfirm = viewModel::confirm,
            onUndo = viewModel::undo,
            onSetClosed = viewModel::setClosed,
            onSaveShares = viewModel::saveShares,
            onDismissError = viewModel::dismissError,
        )
    }
    ExpensesScreen(
        state = state,
        onRetry = viewModel::retry,
        onBack = onBack,
        onAdd = onAdd,
        onEdit = onEdit,
        actions = actions,
        onHistory = onHistory,
        kind = of.type,
        onMoney = onMoney,
    )
}

/** Connects the expense form of a trip to its ViewModel. */
@Composable
fun ExpenseEditRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String,
    expenseId: String?,
    onHistory: (expenseId: String) -> Unit,
    onDone: () -> Unit,
) = ExpenseEditRoute(container, currentMember, ExpenseContainer(ExpenseContainerType.Trip, tripId), expenseId, onHistory, onDone)

/** Connects the expense form of an event to its ViewModel. */
@Composable
fun EventExpenseEditRoute(
    container: AppContainer?,
    currentMember: Member,
    eventId: String,
    expenseId: String?,
    onHistory: (expenseId: String) -> Unit,
    onDone: () -> Unit,
) = ExpenseEditRoute(container, currentMember, ExpenseContainer(ExpenseContainerType.Event, eventId), expenseId, onHistory, onDone)

@Composable
private fun ExpenseEditRoute(
    container: AppContainer?,
    currentMember: Member,
    of: ExpenseContainer,
    expenseId: String?,
    onHistory: (expenseId: String) -> Unit,
    onDone: () -> Unit,
) {
    val viewModel: ExpenseEditViewModel = viewModel(
        key = "expense-${of.bookId}-${expenseId ?: "new"}",
        factory = viewModelFactory {
            initializer {
                ExpenseEditViewModel(container?.expenseRepository, container.contextOf(of), container?.memberRepository, currentMember.id, expenseId)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExpenseEditScreen(
        state = state,
        onChange = viewModel::update,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onDone = onDone,
        onHistory = expenseId?.let { id -> { onHistory(id) } },
        kind = of.type,
    )
}

/** Connects the history of an expense of a trip to its ViewModel. */
@Composable
fun ExpenseHistoryRoute(container: AppContainer?, tripId: String, expenseId: String, onBack: () -> Unit) =
    ExpenseHistoryRoute(container, ExpenseContainer(ExpenseContainerType.Trip, tripId), expenseId, onBack)

/** Connects the history of an expense of an event to its ViewModel. */
@Composable
fun EventExpenseHistoryRoute(container: AppContainer?, eventId: String, expenseId: String, onBack: () -> Unit) =
    ExpenseHistoryRoute(container, ExpenseContainer(ExpenseContainerType.Event, eventId), expenseId, onBack)

@Composable
private fun ExpenseHistoryRoute(container: AppContainer?, of: ExpenseContainer, expenseId: String, onBack: () -> Unit) {
    val viewModel: ExpenseHistoryViewModel = viewModel(
        key = "expense-history-${of.bookId}-$expenseId",
        factory = viewModelFactory { initializer { ExpenseHistoryViewModel(container?.expenseRepository, of, expenseId) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExpenseHistoryScreen(state = state, onRetry = viewModel::retry, onBack = onBack)
}
