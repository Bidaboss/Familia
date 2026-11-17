package fr.familia.app.feature.events.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.album.AlbumFilmPart
import fr.familia.app.core.album.AlbumFilmViewModel
import fr.familia.app.core.album.AlbumPhotosSection
import fr.familia.app.core.album.AlbumPhotosViewModel
import fr.familia.app.core.album.AlbumScreen
import fr.familia.app.core.album.FilmActions
import fr.familia.app.core.album.book.PhotoBookButton
import fr.familia.app.core.album.rememberPhotoActions
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.Member
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import java.io.File

/** The album of [eventId], for the event view and for « Voir les photos ». */
@Composable
fun rememberEventAlbum(container: AppContainer?, currentMember: Member, eventId: String): AlbumPhotosViewModel = viewModel(
    key = "event-album-$eventId",
    factory = viewModelFactory {
        initializer {
            val events = container?.eventRepository
            val members = container?.memberRepository
            val rsvps = container?.rsvpRepository
            AlbumPhotosViewModel(
                photos = container?.albumPhotoRepository,
                rights = if (events != null && members != null && rsvps != null) {
                    eventAlbumRights(events, members, rsvps, eventId, currentMember.id, currentDayFlow())
                } else {
                    null
                },
                uploader = container?.photoUploader,
                storage = container?.photoStorage,
                currentMemberId = currentMember.id,
                folder = eventAlbumFolder(eventId),
                savedState = createSavedStateHandle(),
            )
        }
    },
)

/**
 * The « Photos » part of the event view. Before the day of the event it only says when the album
 * opens. [isOpen] comes from the event view, which knows the day.
 */
@Composable
fun EventAlbumSection(container: AppContainer?, currentMember: Member, eventId: String, isOpen: Boolean, onSeeAll: () -> Unit) {
    val album = rememberEventAlbum(container, currentMember, eventId)
    val state by album.uiState.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.event_album_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        val empty = when {
            !isOpen -> R.string.event_album_not_open
            state.canAdd -> R.string.event_album_empty_coming
            else -> R.string.event_album_empty
        }
        AlbumPhotosSection(state, rememberPhotoActions(album, onSeeAll), stringResource(empty))
    }
}

/** « Voir les photos » of an event, with its film. */
@Composable
fun EventAlbumRoute(container: AppContainer?, currentMember: Member, eventId: String, onBack: () -> Unit, onMakeBook: (() -> Unit)? = null, startPhotoId: String? = null) {
    val album = rememberEventAlbum(container, currentMember, eventId)
    val state by album.uiState.collectAsStateWithLifecycle()
    val filmsDir = File(LocalContext.current.cacheDir, "films")
    val filmViewModel: AlbumFilmViewModel = viewModel(
        key = "event-film-$eventId",
        factory = viewModelFactory {
            initializer { AlbumFilmViewModel(container?.photoStorage, container?.filmMaker, "album-$eventId", filmsDir) }
        },
    )
    val film by filmViewModel.uiState.collectAsStateWithLifecycle()
    val filmActions = remember(filmViewModel) {
        FilmActions(onMake = { filmViewModel.make(album.uiState.value.photos) }, onCancel = filmViewModel::cancel)
    }
    AlbumScreen(
        startPhotoId = startPhotoId,
        title = stringResource(R.string.event_album_title),
        state = state,
        actions = rememberPhotoActions(album),
        onBack = onBack,
        actionsBar = { HelpButton(ScreenHelp.EventAlbum) },
        top = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AlbumFilmPart(film, state.photos.size, filmActions)
                // Le livre photo (récit 59), behind the switch photoBook: the caller gives it or not.
                if (onMakeBook != null && state.photos.isNotEmpty()) PhotoBookButton(onMakeBook)
            }
        },
    )
}
