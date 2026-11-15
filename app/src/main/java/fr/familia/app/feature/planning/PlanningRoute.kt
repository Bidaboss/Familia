package fr.familia.app.feature.planning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects the Planning tab to its ViewModel. */
@Composable
fun PlanningRoute(
    container: AppContainer?,
    currentMember: Member,
    onAddEvent: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    onOpenTrip: (tripId: String) -> Unit = {},
) {
    val viewModel: PlanningViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PlanningViewModel(
                    container?.eventRepository,
                    container?.memberRepository,
                    currentMember.id,
                    rsvps = container?.rsvpRepository,
                    trips = container?.tripRepository,
                ) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlanningScreen(
        state = state,
        onRetry = viewModel::retry,
        onAddEvent = onAddEvent,
        onOpenEvent = onOpenEvent,
        onOpenTrip = onOpenTrip,
    )
}
