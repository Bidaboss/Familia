package fr.familia.app.feature.events.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import fr.familia.app.core.model.Member
import fr.familia.app.core.share.eventShare
import fr.familia.app.core.share.share
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.feature.events.album.EventAlbumSection
import fr.familia.app.feature.events.bring.BringSection
import fr.familia.app.feature.events.comments.EventCommentsSection
import fr.familia.app.feature.events.album.isAlbumOpen

/** Connects the event view to its ViewModel, and closes it when the event is deleted meanwhile. */
@Composable
fun EventDetailRoute(
    container: AppContainer?,
    currentMember: Member,
    eventId: String,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    onOpenAlbum: () -> Unit = {},
    /** « Comptes de l'événement » (PRODUIT-57), wired once the expenses screens exist. */
    onOpenExpenses: (() -> Unit)? = null,
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
    val shareOn = LocalFeatureFlags.current.isOn(Feature.Share)
    val expensesOn = LocalFeatureFlags.current.isOn(Feature.Expenses)
    val shown = state as? EventDetailUiState.Success
    EventDetailScreen(
        state = state,
        onAddToCalendar = shown?.takeIf { calendarOn }?.let { success ->
            { addToCalendar(context, CalendarEntry.forEvent(success.event, context.getString(R.string.calendar_created_from))) }
        },
        onShare = shown?.takeIf { shareOn }?.let { success ->
            { share(context, eventShare(context, success.event, success.today)) }
        },
        onOpenExpenses = onOpenExpenses?.takeIf { expensesOn && shown != null && canOpenExpenses(shown, currentMember) },
        onAnswer = viewModel::answer,
        onRetry = viewModel::retry,
        onEdit = onEdit,
        onBack = onBack,
        album = {
            val success = state as? EventDetailUiState.Success
            // « Qui apporte quoi » (récit 4 bis), before the album.
            if (success != null && LocalFeatureFlags.current.isOn(Feature.Bring)) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                BringSection(container, currentMember, eventId, success.canManage)
            }
            if (success != null && LocalFeatureFlags.current.isOn(Feature.EventAlbums)) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                EventAlbumSection(container, currentMember, eventId, isAlbumOpen(success.event, success.today), onOpenAlbum)
            }
            // The comments (récit 28), last: they can grow long.
            if (success != null && LocalFeatureFlags.current.isOn(Feature.Comments)) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                EventCommentsSection(container, currentMember, eventId)
            }
        },
    )
}
