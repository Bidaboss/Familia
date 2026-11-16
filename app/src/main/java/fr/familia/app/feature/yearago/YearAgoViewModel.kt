package fr.familia.app.feature.yearago

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Trip
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** The souvenirs a member set aside, on this phone. */
interface DismissedMemories {
    fun load(memberId: String): Set<String>
    fun save(memberId: String, keys: Set<String>)
}

class YearAgoPreferences(context: Context) : DismissedMemories {
    private val prefs = context.getSharedPreferences("familia", Context.MODE_PRIVATE)
    override fun load(memberId: String): Set<String> = prefs.getStringSet(key(memberId), emptySet()).orEmpty().toSet()
    override fun save(memberId: String, keys: Set<String>) = prefs.edit { putStringSet(key(memberId), keys) }
    private fun key(memberId: String) = "yearAgoDismissed-$memberId"
}

@OptIn(ExperimentalCoroutinesApi::class)
class YearAgoViewModel(
    goodNews: GoodNewsRepository?,
    trips: TripRepository?,
    albums: AlbumPhotoRepository?,
    private val memberId: String,
    today: LocalDate,
    private val store: DismissedMemories?,
    /** False in « Voir comme »: the button is not offered. */
    val canDismiss: Boolean = true,
) : ViewModel() {
    private val dismissed = MutableStateFlow(store?.load(memberId).orEmpty())

    private fun <T> Flow<T>.orElse(value: T): Flow<T> = catch { e -> if (e is CancellationException) throw e else emit(value) }

    private val news: Flow<List<GoodNews>> = goodNews?.let {
        val (from, to) = YearAgo.bounds(today)
        it.observeBetween(from, to).orElse(emptyList())
    } ?: flowOf(emptyList())

    private val tripPhotos: Flow<List<Pair<Trip, AlbumPhoto>>> =
        if (trips == null || albums == null) {
            flowOf(emptyList())
        } else {
            trips.observeFollowedTrips(memberId)
                .map { YearAgo.tripsOfThatDay(it, today) }
                .flatMapLatest { list ->
                    if (list.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(list.map { trip -> albums.observePhotos(tripAlbumFolder(trip.id)).map { photos -> photos.map { trip to it } } }) {
                            it.toList().flatten()
                        }
                    }
                }
                .orElse(emptyList())
        }

    val memory: StateFlow<YearAgoMemory?> = combine(news, tripPhotos, dismissed) { n, p, d ->
        YearAgo.pick(today, memberId, n, p, d)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** « Ne plus me montrer ce souvenir »: the next souvenir of that day shows, if there is one. */
    fun dismiss(memory: YearAgoMemory) {
        if (!canDismiss) return
        dismissed.update { it + memory.key }
        store?.save(memberId, dismissed.value)
    }
}
