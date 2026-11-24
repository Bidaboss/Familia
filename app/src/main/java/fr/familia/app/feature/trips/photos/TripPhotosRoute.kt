package fr.familia.app.feature.trips.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.album.AlbumFilmPart
import fr.familia.app.core.album.AlbumPhotosViewModel
import fr.familia.app.core.album.AlbumScreen
import fr.familia.app.core.album.AlbumSends
import fr.familia.app.core.album.FilmActions
import fr.familia.app.core.album.book.PhotoBookButton
import fr.familia.app.core.album.rememberPhotoActions
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.share.galleryFilmName
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
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
                savedState = createSavedStateHandle(),
                sends = AlbumSends.shared,
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
    // Récit 108, règle 32: the film of the trip is made of the films of its steps, in the order of « Le trajet ».
    val context = LocalContext.current.applicationContext
    val filmViewModel: TripFilmViewModel = viewModel(
        key = "trip-film-$tripId",
        factory = viewModelFactory {
            initializer {
                TripFilmViewModel(container?.photoStorage, container?.filmMaker, tripId, filmsDir) { title, dir -> writeTitleCard(context, title, dir) }
            }
        },
    )
    val film by filmViewModel.uiState.collectAsStateWithLifecycle()
    val trip by remember(container, tripId) {
        container?.tripRepository?.observeTrip(tripId)?.catch { e -> if (e is CancellationException) throw e else emit(null) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(null)
    val steps by remember(container, tripId) {
        container?.tripRepository?.observeSteps(tripId)?.catch { e -> if (e is CancellationException) throw e else emit(emptyList()) } ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())
    val plan = remember(trip, steps, state.photos) { trip?.let { tripFilmPlan(it, steps, state.photos) } }
    // A photo or a step changed since the film was made: it is made again at the next « Voir » (C2 of #557).
    LaunchedEffect(plan) { filmViewModel.keepFor(plan) }
    val filmActions = remember(filmViewModel, plan) {
        FilmActions(onMake = { filmViewModel.make(plan) }, onCancel = filmViewModel::cancel)
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
                AlbumFilmPart(
                    film,
                    if (plan == null) 0 else state.photos.size,
                    filmActions,
                    help = stringResource(R.string.trip_film_help),
                    downloadName = galleryFilmName("voyage", trip?.title ?: tripId),
                )
                // Le livre photo (récit 59), behind the switch photoBook: the caller gives it or not.
                if (onMakeBook != null && state.photos.isNotEmpty()) PhotoBookButton(onMakeBook)
            }
        },
    )
}
