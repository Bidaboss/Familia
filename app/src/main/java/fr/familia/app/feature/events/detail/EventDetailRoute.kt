package fr.familia.app.feature.events.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.platform.LocalContext
import fr.familia.app.R
import fr.familia.app.core.calendar.CalendarEntry
import fr.familia.app.core.calendar.addToCalendar
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.model.Member

/** Connects the event view to its ViewModel, and closes it when the event is deleted meanwhile. */
@Composable
fun EventDetailRoute(
    container: AppContainer?,
    currentMember: Member,
    eventId: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: EventDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                EventDetailViewModel(
                    container?.eventRepository,
                    container?.memberRepository,
                    container?.rsvpRepository,
                    currentMember.id,
                    eventId,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var wasShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        // Deleted from the form opened here, or by someone else: back to the planning.
        if (state is EventDetailUiState.Success) wasShown = true
        if (state == EventDetailUiState.Missing && wasShown) onBack()
    }
    val context = LocalContext.current
    val calendarOn = LocalFeatureFlags.current.isOn(Feature.Calendar)
    val shown = state as? EventDetailUiState.Success
    EventDetailScreen(
        state = state,
        onAddToCalendar = shown?.takeIf { calendarOn }?.let { success ->
            { addToCalendar(context, CalendarEntry.forEvent(success.event, context.getString(R.string.calendar_created_from))) }
        },
        onAnswer = viewModel::answer,
        onRetry = viewModel::retry,
        onEdit = onEdit,
        onBack = onBack,
    )
}
