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
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.feature.events.album.EventAlbumSection
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
    EventDetailScreen(
        state = state,
        onAnswer = viewModel::answer,
        onRetry = viewModel::retry,
        onEdit = onEdit,
        onBack = onBack,
        album = {
            val success = state as? EventDetailUiState.Success
            if (success != null && LocalFeatureFlags.current.isOn(Feature.EventAlbums)) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                EventAlbumSection(container, currentMember, eventId, isAlbumOpen(success.event, success.today), onOpenAlbum)
            }
        },
    )
}
