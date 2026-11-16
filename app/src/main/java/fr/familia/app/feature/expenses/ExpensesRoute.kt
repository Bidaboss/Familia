package fr.familia.app.feature.expenses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import kotlinx.serialization.Serializable

/** The accounts of the trip [tripId] (récit 16 bis); later, an event will have its own. */
@Serializable
data class TripExpensesDestination(val tripId: String)

/** The form of an expense of [tripId]: a new one when [expenseId] is null. */
@Serializable
data class TripExpenseEditDestination(val tripId: String, val expenseId: String? = null)

/** Connects the accounts of a trip to their ViewModel. */
@Composable
fun ExpensesRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String,
    onAdd: () -> Unit,
    onEdit: (expenseId: String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: ExpensesViewModel = viewModel(
        key = "expenses-trip-$tripId",
        factory = viewModelFactory {
            initializer {
                ExpensesViewModel(container?.expenseRepository, container?.tripRepository, container?.memberRepository, currentMember.id, tripId)
            }
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
    ExpensesScreen(state = state, onRetry = viewModel::retry, onBack = onBack, onAdd = onAdd, onEdit = onEdit, actions = actions)
}

/** Connects the expense form to its ViewModel. */
@Composable
fun ExpenseEditRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String,
    expenseId: String?,
    onDone: () -> Unit,
) {
    val viewModel: ExpenseEditViewModel = viewModel(
        key = "expense-$tripId-${expenseId ?: "new"}",
        factory = viewModelFactory {
            initializer {
                ExpenseEditViewModel(
                    container?.expenseRepository, container?.tripRepository, container?.memberRepository,
                    currentMember.id, tripId, expenseId,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExpenseEditScreen(state = state, onChange = viewModel::update, onSave = viewModel::save, onDelete = viewModel::delete, onDone = onDone)
}
