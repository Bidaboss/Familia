package fr.familia.app.feature.trips.word

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.album.AlbumFilmViewModel
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.FILM_MIN_PHOTOS
import fr.familia.app.core.album.FilmActions
import fr.familia.app.core.album.FilmState
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Trip
import fr.familia.app.core.share.FilmDownloadLine
import fr.familia.app.core.share.FilmSource
import fr.familia.app.core.share.ShareConsentReminder
import fr.familia.app.core.share.galleryFilmName
import fr.familia.app.core.share.playVideo
import fr.familia.app.core.share.rememberFilmDownload
import fr.familia.app.core.share.shareVideo
import fr.familia.app.core.video.FilmStyle
import fr.familia.app.feature.trips.TripTile
import fr.familia.app.feature.trips.photos.FilmAiSheet
import fr.familia.app.feature.trips.photos.FilmAiSubject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import java.io.File
import java.time.LocalDate

/** Each photo of the film of a step stays this long (récit 108, PRD 158). */
const val STEP_FILM_SECONDS = 2.5

/** The photos of the film of [stepId]: those added from that step (récit 104), among the photos this person sees. */
fun stepFilmPhotos(album: List<AlbumPhoto>, stepId: String): List<AlbumPhoto> = album.filter { it.stepId == stepId }

/**
 * The film of [stepId] under its word (relecture #555, C2): its ViewModel, its photos, its tile, and
 * for a participant [me] the film of the day to publish (récit 108, PRD 156 and 157).
 */
@Composable
fun StepFilm(
    container: AppContainer?,
    tripId: String,
    stepId: String,
    album: List<AlbumPhoto>,
    trip: Trip? = null,
    me: Member? = null,
    /** The name of the step, for the file saved in the gallery. */
    place: String? = null,
    /** The step, its photos and its word, for « Faire un vrai film avec une IA » (règle 33). */
    aiSubject: FilmAiSubject? = null,
) {
    var aiOpen by rememberSaveable { mutableStateOf(false) }
    if (aiOpen && aiSubject != null) FilmAiSheet(aiSubject, container?.photoStorage) { aiOpen = false }
    val filmsDir = File(LocalContext.current.cacheDir, "films")
    val filmViewModel: AlbumFilmViewModel = viewModel(
        key = "step-film-$tripId-$stepId",
        factory = viewModelFactory {
            initializer { AlbumFilmViewModel(container?.photoStorage, container?.filmMaker, "etape-$tripId-$stepId", filmsDir, FilmStyle.Trip) { STEP_FILM_SECONDS } }
        },
    )
    val publisher: DayFilmPublisher = viewModel(
        key = "day-film-$tripId-$stepId",
        factory = viewModelFactory {
            initializer { DayFilmPublisher(container?.photoStorage, container?.filmMaker, container?.tripFilmRepository, tripId, stepId, filmsDir) }
        },
    )
    val film by filmViewModel.uiState.collectAsStateWithLifecycle()
    val publishing by publisher.uiState.collectAsStateWithLifecycle()
    val filmsFlow = remember(container, tripId) { container?.tripFilmRepository?.observe(tripId)?.catch { emit(emptyList()) } ?: flowOf(emptyList()) }
    val films by filmsFlow.collectAsStateWithLifecycle(emptyList())
    val today by remember { currentDayFlow() }.collectAsStateWithLifecycle(familyToday())
    val stepPhotos = remember(album, stepId) { stepFilmPhotos(album, stepId) }
    // A photo added or taken off since the film was made: it is made again at the next « Voir » (C1).
    LaunchedEffect(stepPhotos) { filmViewModel.keepFor(stepPhotos) }
    val actions = remember(filmViewModel, stepPhotos) { FilmActions(onMake = { filmViewModel.make(stepPhotos) }, onCancel = filmViewModel::cancel) }
    // Only a participant publishes, as the rules say; never in « Voir comme » (no repository there).
    val canPublish = trip != null && me != null && me.id in trip.participantIds && publisher.canPublish
    val day = if (canPublish && trip != null) dayToPublish(stepPhotos, films, stepId, trip, today) else null
    val publish: (LocalDate) -> Unit = { d -> if (me != null) publisher.publish(me, d, stepPhotos) }
    val repository = container?.tripFilmRepository
    StepFilmTile(film, stepPhotos.size, actions, downloadName = galleryFilmName("etape", place ?: stepId), onAiFilm = aiSubject?.takeIf { it.photos.isNotEmpty() }?.let { { aiOpen = true } }, onPublish = day?.takeIf { publishing !is DayFilmState.Working }?.let { d -> { publish(d) } }) {
        DayFilmsPart(
            state = publishing,
            published = stepFilms(films, stepId),
            reminderDay = day?.takeIf { remindsDayFilm(it, today) },
            onPublish = publish,
            onCancel = publisher::cancel,
            playUrl = { f -> repository?.playUrl(tripId, f) },
        )
    }
}

/** The words of a film tile: those of the step by default, those of the trip for the film of the trip. */
data class FilmTileLabels(
    val title: Int = R.string.step_film_title,
    val watch: Int = R.string.step_film_watch,
    val help: Int = R.string.step_film_help,
    val needPhotos: Int = R.string.step_film_need_photos,
) {
    companion object {
        /** The film of the trip works like that of a step (règle 32, DESIGN-79 B). */
        val Trip = FilmTileLabels(R.string.trip_film_title, R.string.trip_film_watch, R.string.trip_film_help, R.string.album_film_need_photos)
    }
}

/**
 * « Film de l'étape » (récit 108, règle 32): the photos of the step, oldest first, 2,5 s each with
 * a slow zoom, made on the phone, without sound for now. « Voir » is the main button and plays the
 * film once it is made; « Partager » is in the menu (DESIGN-79 B), after the reminder of EXPERT-6.
 * The film of the trip uses the same tile with [FilmTileLabels.Trip].
 */
@Composable
fun StepFilmTile(
    state: FilmState,
    photoCount: Int,
    actions: FilmActions,
    /** The file name in the gallery for « Télécharger » (règle 32); null offers none. */
    downloadName: String? = null,
    /** « Publier le film de la journée » in the menu, for a participant with a day to publish (PRD 156). */
    onPublish: (() -> Unit)? = null,
    /** « Faire un vrai film avec une IA » in the menu (règle 33). */
    onAiFilm: (() -> Unit)? = null,
    labels: FilmTileLabels = FilmTileLabels(),
    below: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    // « Voir » asked before the film was made: it plays as soon as it is ready.
    var playWhenReady by rememberSaveable { mutableStateOf(false) }
    var noPlayer by rememberSaveable { mutableStateOf(false) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var remind by rememberSaveable { mutableStateOf(false) }
    val download = rememberFilmDownload()?.takeIf { downloadName != null }
    LaunchedEffect(state) {
        if (state is FilmState.Ready && playWhenReady) {
            playWhenReady = false
            noPlayer = !playVideo(context, File(state.path))
        }
        if (state is FilmState.Failed) playWhenReady = false
    }
    TripTile(Icons.Outlined.Movie, stringResource(labels.title)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                FilmState.Idle, FilmState.Failed -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { playWhenReady = true; actions.onMake() },
                            enabled = photoCount >= FILM_MIN_PHOTOS,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(labels.watch))
                        }
                        if (onPublish != null || onAiFilm != null) {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(56.dp)) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.step_film_more))
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    if (onPublish != null) PublishItem { menu = false; onPublish() }
                                    if (onAiFilm != null) AiFilmItem { menu = false; onAiFilm() }
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(
                            when {
                                state == FilmState.Failed -> R.string.album_film_failed
                                photoCount < FILM_MIN_PHOTOS -> labels.needPhotos
                                else -> labels.help
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state == FilmState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilmState.Making -> {
                    Text(stringResource(R.string.album_film_making), style = MaterialTheme.typography.bodyLarge)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = { playWhenReady = false; actions.onCancel() }, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.album_film_cancel))
                    }
                }
                is FilmState.Ready -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { noPlayer = !playVideo(context, File(state.path)) },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(labels.watch))
                        }
                        // The rest in a menu (DESIGN-79 B): « Partager », « Télécharger », « Publier », the IA.
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.step_film_more))
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.album_film_share)) },
                                    leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                    onClick = { menu = false; remind = true },
                                    modifier = Modifier.heightIn(min = 56.dp),
                                )
                                if (download != null && downloadName != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.film_download)) },
                                        leadingIcon = { Icon(Icons.Outlined.SaveAlt, contentDescription = null) },
                                        onClick = { menu = false; download.download(FilmSource.Local(File(state.path), downloadName)) },
                                        modifier = Modifier.heightIn(min = 56.dp),
                                    )
                                }
                                if (onPublish != null) PublishItem { menu = false; onPublish() }
                                if (onAiFilm != null) AiFilmItem { menu = false; onAiFilm() }
                            }
                        }
                    }
                    FilmDownloadLine(download)
                    if (noPlayer) {
                        Text(stringResource(R.string.album_film_no_player), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    if (remind) {
                        val chooser = stringResource(R.string.album_film_share)
                        ShareConsentReminder(
                            onContinue = {
                                remind = false
                                shareVideo(context, File(state.path), chooser)
                            },
                            onDismiss = { remind = false },
                        )
                    }
                }
            }
            below()
        }
    }
}

@Composable
private fun PublishItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.day_film_publish)) },
        leadingIcon = { Icon(Icons.Outlined.DynamicFeed, contentDescription = null) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 56.dp),
    )
}

@Composable
private fun AiFilmItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.film_ai_open)) },
        leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 56.dp),
    )
}
