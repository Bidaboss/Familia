package fr.familia.app.feature.trips.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.album.AlbumFilmPart
import fr.familia.app.core.album.AlbumFilmViewModel
import fr.familia.app.core.album.AlbumPhotosViewModel
import fr.familia.app.core.album.AlbumScreen
import fr.familia.app.core.album.FilmActions
import fr.familia.app.core.album.book.PhotoBookButton
import fr.familia.app.core.album.rememberPhotoActions
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import java.io.File

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

/** « Voir les photos » of a trip, with its film (récit 16). */
@Composable
fun TripPhotosRoute(container: AppContainer?, currentMember: Member, tripId: String, onBack: () -> Unit, onMakeBook: (() -> Unit)? = null, startPhotoId: String? = null) {
    val album = rememberTripAlbum(container, currentMember, tripId)
    val state by album.uiState.collectAsStateWithLifecycle()
    val filmsDir = File(LocalContext.current.cacheDir, "films")
    val filmViewModel: AlbumFilmViewModel = viewModel(
        key = "trip-film-$tripId",
        factory = viewModelFactory {
            initializer { AlbumFilmViewModel(container?.photoStorage, container?.filmMaker, "voyage-$tripId", filmsDir) }
        },
    )
    val film by filmViewModel.uiState.collectAsStateWithLifecycle()
    val filmActions = remember(filmViewModel) {
        FilmActions(onMake = { filmViewModel.make(album.uiState.value.photos) }, onCancel = filmViewModel::cancel)
    }
    AlbumScreen(
        startPhotoId = startPhotoId,
        title = stringResource(R.string.trip_photos),
        state = state,
        actions = rememberPhotoActions(album),
        onBack = onBack,
        actionsBar = { HelpButton(ScreenHelp.TripDetail) },
        top = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AlbumFilmPart(film, state.photos.size, filmActions)
                // Le livre photo (récit 59), behind the switch photoBook: the caller gives it or not.
                if (onMakeBook != null && state.photos.isNotEmpty()) PhotoBookButton(onMakeBook)
            }
        },
    )
}
