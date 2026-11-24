package fr.familia.app.feature.trips.step

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.BuildConfig
import fr.familia.app.core.model.Member
import fr.familia.app.feature.trips.map.TripMapBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf

/** Connects the step form to its ViewModel, and closes it once saved or deleted. */
@Composable
fun StepEditRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String,
    stepId: String?,
    onDone: () -> Unit,
    presetPlace: String? = null,
    presetKind: StepAddKind? = null,
    presetDay: java.time.LocalDate? = null,
) {
    val viewModel: StepEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                StepEditViewModel(
                    container?.tripRepository,
                    container?.memberRepository,
                    currentMember.id,
                    tripId,
                    stepId,
                    presetPlace = presetPlace,
                    presetKind = presetKind,
                    presetDay = presetDay,
                    locator = container?.placeLocator,
                    savedState = createSavedStateHandle(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == StepEditUiState.Done) onDone()
    }
    // The map of the trip stays open above the form (règles 4, 6, 23), the step chosen on it.
    val trip by remember(tripId) {
        container?.tripRepository?.observeTrip(tripId)?.catch { e -> if (e is CancellationException) throw e else emit(null) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)
    val steps by remember(tripId) {
        container?.tripRepository?.observeSteps(tripId)?.catch { e -> if (e is CancellationException) throw e else emit(emptyList()) } ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    TripMapBackdrop(token = BuildConfig.MAPBOX_PUBLIC_TOKEN, trip = trip, steps = steps, focusId = stepId) { inSheet ->
        StepEditScreen(state = state, onChange = viewModel::update, onSave = viewModel::save, onDelete = viewModel::delete, onBack = onDone, inSheet = inSheet)
    }
}
