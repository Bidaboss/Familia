package fr.familia.app.feature.month

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.album.albumPhotoOrder
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TreeRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MonthChallenge
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
import java.time.YearMonth
import java.time.ZoneId

data class MonthSlideshowUiState(
    val loading: Boolean = true,
    /** Newest first, like an album. */
    val photos: List<AlbumPhoto> = emptyList(),
    val birthdays: List<Member> = emptyList(),
    /** The challenge of the month (récits 35 and 35 bis), when the tree is on. */
    val challenge: MonthChallenge? = null,
)

/**
 * The photos and birthdays of [month] for [currentMemberId], read live from the albums they can
 * see (see monthAlbums), and the challenge of the month from [tree] (null when the tree is off).
 * Nothing is written here: each photo is removed from its own album.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthSlideshowViewModel(
    trips: TripRepository?,
    events: EventRepository?,
    albums: AlbumPhotoRepository?,
    members: MemberRepository?,
    currentMemberId: String,
    private val month: YearMonth,
    private val zone: ZoneId = ZoneId.systemDefault(),
    tree: TreeRepository? = null,
) : ViewModel() {

    val uiState: StateFlow<MonthSlideshowUiState> =
        if (trips == null || events == null || albums == null || members == null) {
            MutableStateFlow(MonthSlideshowUiState(loading = false)) // Firebase not configured (previews).
        } else {
            val family = members.observeFamily()
            val followed = trips.observeFollowedTrips(currentMemberId)
            val ofMonth = events.observeUpcoming(month.atDay(1)).map { list -> list.filter { YearMonth.from(it.date) == month } }
            combine(family, ofMonth, followed) { fam, evts, trs ->
                val me = fam.members.firstOrNull { it.id == currentMemberId }
                val folders = if (me == null) MonthAlbums(emptyList(), emptyList()) else monthAlbums(month, me, fam, evts, trs)
                folders to birthdaysOf(fam, month)
            }
                .distinctUntilChanged()
                .flatMapLatest { (folders, birthdays) ->
                    combine(photosOf(albums, folders), challengeOf(tree)) { photos, challenge ->
                        MonthSlideshowUiState(
                            loading = false,
                            photos = photos.filter { folders.keep(it, month, zone) }.sortedWith(albumPhotoOrder),
                            birthdays = birthdays,
                            challenge = challenge,
                        )
                    }
                }
                // An album that cannot be read leaves the slideshow empty rather than broken.
                .catch { e -> if (e is CancellationException) throw e else emit(MonthSlideshowUiState(loading = false)) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthSlideshowUiState())
        }

    /** A challenge that cannot be read is left out; the photos still show. */
    private fun challengeOf(tree: TreeRepository?): Flow<MonthChallenge?> =
        tree?.observeChallenge(month)?.catch { e -> if (e is CancellationException) throw e else emit(null) } ?: flowOf(null)

    private fun photosOf(albums: AlbumPhotoRepository, folders: MonthAlbums): Flow<List<AlbumPhoto>> =
        if (folders.all.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(folders.all.map { folder -> albums.observePhotos(folder).catch { e -> if (e is CancellationException) throw e else emit(emptyList()) } }) { lists -> lists.flatMap { it } }
        }
}
