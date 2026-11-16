package fr.familia.app.feature.expenses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/** Connects the accounts of a trip to their ViewModel. */
@Composable
fun ExpensesRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String,
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
    ExpensesScreen(state = state, onRetry = viewModel::retry, onBack = onBack)
}
