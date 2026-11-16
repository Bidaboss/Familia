package fr.familia.app.feature.trips.detail

import fr.familia.app.core.share.tripShare
import fr.familia.app.core.share.share
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.model.Feature
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.album.rememberPhotoActions
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.feature.trips.photos.rememberTripAlbum

/** Connects the trip view to its ViewModel. */
@Composable
fun TripDetailRoute(
    container: AppContainer?,
    currentMember: Member,
    tripId: String,
    onEditTrip: () -> Unit,
    onAddStep: () -> Unit,
    onEditStep: (stepId: String) -> Unit,
    onSeePhotos: () -> Unit,
    onBack: () -> Unit,
    /** « Comptes du voyage » (récit 16 bis): null while the switch is off or for a child. */
    onOpenExpenses: (() -> Unit)? = null,
) {
    val viewModel: TripDetailViewModel = viewModel(
        key = "trip-$tripId",
        factory = viewModelFactory {
            initializer { TripDetailViewModel(container?.tripRepository, container?.memberRepository, currentMember.id, tripId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val checklist = remember(viewModel) {
        ChecklistActions(
            onAdd = viewModel::addItem,
            onEdit = viewModel::editItem,
            onDone = viewModel::setItemDone,
            onDelete = viewModel::deleteItem,
        )
    }
    val album = rememberTripAlbum(container, currentMember, tripId)
    val photos by album.uiState.collectAsStateWithLifecycle()
    val photoActions = rememberPhotoActions(album, onSeePhotos)
    val context = LocalContext.current
    val shareOn = LocalFeatureFlags.current.isOn(Feature.Share)
    val shown = state as? TripDetailUiState.Success
    TripDetailScreen(
        state = state,
        onRetry = viewModel::retry,
        onEditTrip = onEditTrip,
        onAddStep = onAddStep,
        onEditStep = onEditStep,
        onArrived = viewModel::markArrived,
        onClearArrival = viewModel::clearArrival,
        onBack = onBack,
        checklist = checklist,
        photos = photos,
        photoActions = photoActions,
        onOpenExpenses = onOpenExpenses,
        onShare = shown?.takeIf { shareOn && it.canShare }?.let { success -> { share(context, tripShare(context, success.trip, success.today)) } },
    )
}
