package fr.familia.app.feature.trips.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects the trip view to its ViewModel. */
@Composable
fun TripDetailRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String,
    onEditTrip: () -> Unit,
    onAddStep: () -> Unit,
    onEditStep: (stepId: String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: TripDetailViewModel = viewModel(
        key = "trip-$tripId",
        factory = viewModelFactory {
            initializer { TripDetailViewModel(container?.tripRepository, container?.memberRepository, currentMember.id, tripId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val checklist = remember(viewModel) {
        ChecklistActions(
            onAdd = viewModel::addItem,
            onEdit = viewModel::editItem,
            onDone = viewModel::setItemDone,
            onDelete = viewModel::deleteItem,
        )
    }
    TripDetailScreen(
        state = state,
        onRetry = viewModel::retry,
        onEditTrip = onEditTrip,
        onAddStep = onAddStep,
        onEditStep = onEditStep,
        onArrived = viewModel::markArrived,
        onClearArrival = viewModel::clearArrival,
        onBack = onBack,
        checklist = checklist,
    )
}
