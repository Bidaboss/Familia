package fr.familia.app.feature.trips.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.album.AlbumPhotosViewModel
import fr.familia.app.core.album.AlbumScreen
import fr.familia.app.core.album.PhotoActions
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp

/** The album of [tripId], for the trip view and for « Voir les photos ». */
@Composable
fun rememberTripAlbum(container: AppContainer?, currentMember: Member, tripId: String): AlbumPhotosViewModel = viewModel(
    key = "trip-album-$tripId",
    factory = viewModelFactory {
        initializer {
            val trips = container?.tripRepository
            val members = container?.memberRepository
            AlbumPhotosViewModel(
                photos = container?.albumPhotoRepository,
                rights = if (trips != null && members != null) tripAlbumRights(trips, members, tripId, currentMember.id) else null,
                uploader = container?.photoUploader,
                storage = container?.photoStorage,
                currentMemberId = currentMember.id,
                folder = tripAlbumFolder(tripId),
            )
        }
    },
)

/** The actions of the photo views, given once per ViewModel. */
@Composable
fun rememberPhotoActions(viewModel: AlbumPhotosViewModel, onSeeAll: () -> Unit = {}): PhotoActions =
    remember(viewModel, onSeeAll) {
        PhotoActions(
            onAdd = viewModel::addPhotos,
            onRemove = viewModel::removePhoto,
            onSeeAll = onSeeAll,
            onDismissProblem = viewModel::dismissProblem,
        )
    }

/** « Voir les photos » of a trip. */
@Composable
fun TripPhotosRoute(container: AppContainer?, currentMember: Member, tripId: String, onBack: () -> Unit) {
    val album = rememberTripAlbum(container, currentMember, tripId)
    val state by album.uiState.collectAsStateWithLifecycle()
    AlbumScreen(
        title = stringResource(R.string.trip_photos),
        state = state,
        actions = rememberPhotoActions(album),
        onBack = onBack,
        actionsBar = { HelpButton(ScreenHelp.TripDetail) },
    )
}
