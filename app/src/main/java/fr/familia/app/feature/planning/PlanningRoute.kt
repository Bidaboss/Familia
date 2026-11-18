package fr.familia.app.feature.planning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.share.birthdayShare
import fr.familia.app.core.share.share
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
                    // Always the whole family: no filter by household in the Planning (Kévin le 18/11, 22 h 39).
                    initialScope = PlanningScope.Family,
                    trips = if (tripsOn) container?.tripRepository else null,
                ) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val v2On = LocalFeatureFlags.current.isOnForFamily(Feature.PlanningV2)
    val screenContext = LocalContext.current
    val shareOn = LocalFeatureFlags.current.isOn(Feature.Share)
    PlanningScreen(
        state = state,
        onRetry = viewModel::retry,
        onAddEvent = onAddEvent,
        onOpenEvent = onOpenEvent,
        onOpenTrip = onOpenTrip,
        r1 = LocalFeatureFlags.current.isOn(Feature.PresentationR1bis),
        onShareBirthday = if (shareOn) ({ birthday -> share(screenContext, birthdayShare(screenContext, birthday, familyToday())) }) else null,
        v2 = if (v2On) {
            { success ->
                PlanningV2(
                    state = success,
                    me = currentMember,
                    onOpenEvent = onOpenEvent,
                    onOpenTrip = onOpenTrip,
                    onShareBirthday = if (shareOn) ({ birthday -> share(screenContext, birthdayShare(screenContext, birthday, familyToday())) }) else null,
                )
            }
        } else {
            null
        },
    )
}
