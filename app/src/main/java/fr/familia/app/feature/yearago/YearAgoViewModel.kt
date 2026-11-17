package fr.familia.app.feature.yearago

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.GoodNews
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** The souvenirs a member set aside, and the one shown today, on this phone. */
interface DismissedMemories {
    fun load(memberId: String): Set<String>
    fun save(memberId: String, keys: Set<String>)

    /** « 2027-11-21|trip-t1-camille_2 »: the souvenir shown on that day, kept all day. */
    fun loadToday(memberId: String): String? = null
    fun saveToday(memberId: String, value: String) = Unit
}

class YearAgoPreferences(context: Context) : DismissedMemories {
    private val prefs = context.getSharedPreferences("familia", Context.MODE_PRIVATE)
    override fun load(memberId: String): Set<String> = prefs.getStringSet(key(memberId), emptySet()).orEmpty().toSet()
    override fun save(memberId: String, keys: Set<String>) = prefs.edit { putStringSet(key(memberId), keys) }
    override fun loadToday(memberId: String): String? = prefs.getString("yearAgoToday-$memberId", null)
    override fun saveToday(memberId: String, value: String) = prefs.edit { putString("yearAgoToday-$memberId", value) }
    private fun key(memberId: String) = "yearAgoDismissed-$memberId"
}

@OptIn(ExperimentalCoroutinesApi::class)
class YearAgoViewModel(
    goodNews: GoodNewsRepository?,
    trips: TripRepository?,
    albums: AlbumPhotoRepository?,
    private val memberId: String,
    private val today: LocalDate,
    private val store: DismissedMemories?,
    /** False in « Voir comme »: the button is not offered. */
    val canDismiss: Boolean = true,
    events: EventRepository? = null,
    /** To keep only the events that concern me; without it, no event. */
    members: MemberRepository? = null,
) : ViewModel() {
    private val dismissed = MutableStateFlow(store?.load(memberId).orEmpty())

    private fun <T> Flow<T>.orElse(value: T): Flow<T> = catch { e -> if (e is CancellationException) throw e else emit(value) }

    private val news: Flow<List<GoodNews>> = goodNews?.let { repo ->
        combine(YearAgo.bounds(today).map { (from, to) -> repo.observeBetween(from, to).orElse(emptyList()) }) { it.toList().flatten() }
    } ?: flowOf(emptyList())

    private fun albumPhotos(albumsOf: Flow<List<MemoryAlbum>>, repo: AlbumPhotoRepository): Flow<List<Pair<MemoryAlbum, AlbumPhoto>>> =
        albumsOf.flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(list.map { album -> repo.observePhotos(album.folder).orElse(emptyList()).map { photos -> photos.map { album to it } } }) {
                    it.toList().flatten()
                }
            }
        }.orElse(emptyList())

    private val tripPhotos: Flow<List<Pair<MemoryAlbum, AlbumPhoto>>> =
        if (trips == null || albums == null) {
            flowOf(emptyList())
        } else {
            albumPhotos(
                trips.observeFollowedTrips(memberId).map { list ->
                    YearAgo.tripsOfThoseDays(list, today).map { MemoryAlbum(MemoryAlbum.Kind.Trip, it.id, it.title) }
                },
                albums,
            )
        }

    private val eventPhotos: Flow<List<Pair<MemoryAlbum, AlbumPhoto>>> =
        if (events == null || albums == null || members == null) {
            flowOf(emptyList())
        } else {
            albumPhotos(
                combine(events.observeOnDays(YearAgo.days(today)), members.observeFamily()) { list, family ->
                    YearAgo.eventsOfThoseDays(list, today, family, memberId).map { MemoryAlbum(MemoryAlbum.Kind.Event, it.id, it.title) }
                },
                albums,
            )
        }

    private val kept: String? = store?.loadToday(memberId)
        ?.split('|', limit = 2)
        ?.takeIf { it.size == 2 && it[0] == today.toString() }
        ?.get(1)

    val memory: StateFlow<YearAgoMemory?> = combine(news, tripPhotos, eventPhotos, dismissed) { n, t, e, d ->
        YearAgo.pick(today, memberId, n, t + e, d, kept = kept)
    }.onEach { m -> m?.let { store?.saveToday(memberId, "$today|${it.key}") } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** « Ne plus me montrer ce souvenir »: the next souvenir of that day shows, if there is one. */
    fun dismiss(memory: YearAgoMemory) {
        if (!canDismiss) return
        dismissed.update { it + memory.key }
        store?.save(memberId, dismissed.value)
    }
}

/** Where the photos of the album are (trips/{id} or events/{id}). */
val MemoryAlbum.folder: String
    get() = when (kind) {
        MemoryAlbum.Kind.Trip -> tripAlbumFolder(id)
        MemoryAlbum.Kind.Event -> eventAlbumFolder(id)
    }
