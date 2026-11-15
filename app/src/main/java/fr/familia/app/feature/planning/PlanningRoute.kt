package fr.familia.app.feature.planning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.LocalFeatureFlags

/** Connects the Planning tab to its ViewModel. */
@Composable
fun PlanningRoute(
    container: AppContainer?,
    currentMember: Member,
    onAddEvent: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    onOpenTrip: (tripId: String) -> Unit = {},
) {
    // Trips show in the planning once their module is switched on (PRD rév. 120).
    val tripsOn = LocalFeatureFlags.current.isOn(Feature.Trips)
    val viewModel: PlanningViewModel = viewModel(
        key = "planning-trips-$tripsOn",
        factory = viewModelFactory {
            initializer { PlanningViewModel(
                    container?.eventRepository,
                    container?.memberRepository,
                    currentMember.id,
                    rsvps = container?.rsvpRepository,
                    trips = if (tripsOn) container?.tripRepository else null,
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
