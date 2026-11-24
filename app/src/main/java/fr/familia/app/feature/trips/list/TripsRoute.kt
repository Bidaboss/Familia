package fr.familia.app.feature.trips.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf

/** Connects the trip list to its ViewModel. */
@Composable
fun TripsRoute(
    container: AppContainer?,
    currentMember: Member,
    onOpenTrip: (tripId: String) -> Unit,
    onAddTrip: () -> Unit,
    onBack: (() -> Unit)?,
    onOpenAlbums: (() -> Unit)? = null,
    globeToken: String = "",
    onOpenCarnet: (() -> Unit)? = null,
    onAccount: (() -> Unit)? = null,
) {
    val viewModel: TripsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TripsViewModel(container?.tripRepository, container?.memberRepository, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The destinations added by hand to the carnet (récit 107): in Souvenirs and on the globe.
    val handAdded by remember(currentMember.id) {
        container?.travelPlaceRepository?.observePlaces(currentMember.id)
            ?.catch { e -> if (e is CancellationException) throw e else emit(emptyList()) }
            ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())
    TripsScreen(state = state, onRetry = viewModel::retry, onOpenTrip = onOpenTrip, onAddTrip = onAddTrip, onBack = onBack, onOpenAlbums = onOpenAlbums, globeToken = globeToken, placePhotos = container?.placePhotos, onOpenCarnet = onOpenCarnet, handAdded = handAdded, account = currentMember, onAccount = onAccount)
}
