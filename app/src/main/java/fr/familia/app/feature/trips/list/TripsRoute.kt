package fr.familia.app.feature.trips.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects the trip list to its ViewModel. */
@Composable
fun TripsRoute(
    container: AppContainer?,
    currentMember: Member,
    onOpenTrip: (tripId: String) -> Unit,
    onAddTrip: () -> Unit,
    onBack: (() -> Unit)?,
    onOpenAlbums: (() -> Unit)? = null,
    onOpenPart: ((tripId: String, part: TripPart) -> Unit)? = null,
    expensesOn: Boolean = false,
) {
    val viewModel: TripsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TripsViewModel(container?.tripRepository, container?.memberRepository, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TripsScreen(state = state, onRetry = viewModel::retry, onOpenTrip = onOpenTrip, onAddTrip = onAddTrip, onBack = onBack, onOpenAlbums = onOpenAlbums, onOpenPart = onOpenPart, expensesOn = expensesOn)
}
