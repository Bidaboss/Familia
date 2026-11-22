package fr.familia.app.feature.trips.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.feature.trips.detail.TripDetailViewModel

/** « La carte du voyage »: the same trip and steps as its page, read with the same rules. */
@Composable
fun TripMapRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String,
    onOpenStep: (stepId: String) -> Unit,
    onEditStep: (stepId: String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: TripDetailViewModel = viewModel(
        key = "trip-map-$tripId",
        factory = viewModelFactory {
            initializer { TripDetailViewModel(container?.tripRepository, container?.memberRepository, currentMember.id, tripId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TripMapScreen(state, onOpenStep, onEditStep, onBack)
}
