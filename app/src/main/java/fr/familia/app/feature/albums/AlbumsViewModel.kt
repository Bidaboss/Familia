package fr.familia.app.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.concerns
import fr.familia.app.feature.events.album.eventAlbumFolder
import fr.familia.app.feature.trips.photos.tripAlbumFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/** One album the member can open: a trip they follow or an event that concerns them. */
data class AlbumEntry(
    val kind: Kind,
    val id: String,
    val title: String,
    /** The first day of the trip, or the day of the event. */
    val date: LocalDate,
    val photoCount: Int = 0,
    /** The thumbnail of the newest photo. */
    val coverThumb: String? = null,
) {
    enum class Kind { Trip, Event }

    val folder: String get() = if (kind == Kind.Trip) tripAlbumFolder(id) else eventAlbumFolder(id)
}

data class AlbumsUiState(
    val loading: Boolean = true,
    /** Only the albums with photos, the most recent first. */
    val albums: List<AlbumEntry> = emptyList(),
    /** The months of these albums, newest first: each opens its diaporama of the month. */
    val months: List<YearMonth> = emptyList(),
)

/** How far back the screen looks, and how many albums it listens to at most. */
const val ALBUMS_MONTHS_BACK = 12L
const val ALBUMS_MAX = 40

/**
 * The albums [memberId] can see, before any photo is read: the trips they follow and the events
 * of the last year that concern them, already started, the most recent first.
 */
fun albumCandidates(trips: List<Trip>, events: List<Event>, family: Family, memberId: String, today: LocalDate): List<AlbumEntry> {
    val me = family.members.firstOrNull { it.id == memberId } ?: return emptyList()
    val since = today.minusMonths(ALBUMS_MONTHS_BACK)
    val fromTrips = trips.filter { !it.startDate.isAfter(today) && !it.endDate.isBefore(since) }
        .map { AlbumEntry(AlbumEntry.Kind.Trip, it.id, it.title, it.startDate) }
    val fromEvents = events.filter { !it.date.isAfter(today) && !it.date.isBefore(since) && concerns(it.target, me, family) }
        .map { AlbumEntry(AlbumEntry.Kind.Event, it.id, it.title, it.date) }
    return (fromTrips + fromEvents).sortedWith(compareByDescending<AlbumEntry> { it.date }.thenBy { it.title.lowercase() }).take(ALBUMS_MAX)
}

/** Keeps the albums with photos, each with its count and its newest photo, and lists their months. */
fun albumsState(candidates: List<AlbumEntry>, photos: List<List<AlbumPhoto>>): AlbumsUiState {
    val albums = candidates.zip(photos).mapNotNull { (entry, list) ->
        // The repository gives the newest first.
        list.firstOrNull()?.let { entry.copy(photoCount = list.size, coverThumb = it.thumbnailPath) }
    }
    return AlbumsUiState(
        loading = false,
        albums = albums,
        months = albums.map { YearMonth.from(it.date) }.distinct().sortedDescending(),
    )
}

/**
 * « Albums » (R1 bis, Tout Familia): every album with photos, then the months. Read only: photos
 * are added and removed in each album. [tripsOn] and [eventsOn] follow the switches of the modules.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModel(
    trips: TripRepository?,
    events: EventRepository?,
    albums: AlbumPhotoRepository?,
    members: MemberRepository?,
    memberId: String,
    today: LocalDate,
    tripsOn: Boolean,
    eventsOn: Boolean,
) : ViewModel() {

    val uiState: StateFlow<AlbumsUiState> =
        if (trips == null || events == null || albums == null || members == null) {
            MutableStateFlow(AlbumsUiState(loading = false)) // Firebase not configured (previews).
        } else {
            val followed = if (tripsOn) trips.observeFollowedTrips(memberId) else flowOf(emptyList())
            val recent = if (eventsOn) events.observeUpcoming(today.minusMonths(ALBUMS_MONTHS_BACK)) else flowOf(emptyList())
            combine(followed, recent, members.observeFamily()) { trs, evts, family -> albumCandidates(trs, evts, family, memberId, today) }
                .distinctUntilChanged()
                .flatMapLatest { candidates -> photosOf(albums, candidates).map { albumsState(candidates, it) } }
                .catch { e -> if (e is CancellationException) throw e else emit(AlbumsUiState(loading = false)) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumsUiState())
        }

    /** An album that cannot be read counts as empty, so it is left out rather than breaking the list. */
    private fun photosOf(albums: AlbumPhotoRepository, candidates: List<AlbumEntry>): Flow<List<List<AlbumPhoto>>> =
        if (candidates.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(candidates.map { entry -> albums.observePhotos(entry.folder).catch { e -> if (e is CancellationException) throw e else emit(emptyList()) } }) { it.toList() }
        }
}
