package fr.familia.app.feature.trips.carnet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.album.AlbumPhotoRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TravelPlaceRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.TravelPlace
import fr.familia.app.core.model.Trip
import fr.familia.app.core.place.Place
import fr.familia.app.core.place.PlaceLocator
import fr.familia.app.feature.trips.edit.destinationName
import fr.familia.app.feature.trips.list.tripLength
import fr.familia.app.feature.trips.photos.tripAlbumFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

sealed interface CarnetUiState {
    data object Loading : CarnetUiState

    /** [manual]: the hand-added destinations, most recent first, each with its « Retirer ». */
    data class Ready(val carnet: Carnet, val manual: List<TravelPlace>, val today: LocalDate, val canAdd: Boolean) : CarnetUiState
}

/** What the sheet « Ajouter une destination » is doing. */
enum class AddState { Idle, Saving, Saved, Failed }

/**
 * « Carnet de baroudeur » (récit 107, règles 36 à 38): fed by the trips the member took part in and
 * that are over, plus the destinations they added by hand. The countries come from the geocoder of
 * the phone, from the point each step already has; nothing new is stored but the hand-added ones.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CarnetViewModel(
    private val trips: TripRepository?,
    private val photos: AlbumPhotoRepository?,
    private val places: TravelPlaceRepository?,
    private val members: MemberRepository?,
    private val locator: PlaceLocator?,
    private val memberId: String,
    today: Flow<LocalDate> = currentDayFlow(),
) : ViewModel() {

    private val day = today.distinctUntilChanged()

    private val finished: Flow<List<CarnetTrip>> = combine(
        trips?.observeFollowedTrips(memberId)?.catch { e -> if (e is CancellationException) throw e else emit(emptyList()) } ?: flowOf(emptyList()),
        day,
    // A trip counts from its last day (récit 112): the evening of the return, it is in the carnet.
    ) { all, now -> all.filter { memberId in it.participantIds && !it.endDate.isAfter(now) }.sortedBy { it.startDate } }
        .distinctUntilChanged()
        .flatMapLatest { done -> if (done.isEmpty()) flowOf(emptyList()) else combine(done.map(::counted)) { it.toList() } }

    private val manual: Flow<List<TravelPlace>> =
        places?.observePlaces(memberId)?.catch { e -> if (e is CancellationException) throw e else emit(emptyList()) } ?: flowOf(emptyList())

    private val home: Flow<Pair<Double, Double>?> =
        (members?.observeFamily()?.map { family ->
            val me = family.members.firstOrNull { it.id == memberId }
            family.households.firstOrNull { it.id == me?.householdId }?.address?.takeIf { it.isNotBlank() }
        }?.catch { e -> if (e is CancellationException) throw e else emit(null) } ?: flowOf(null))
            .distinctUntilChanged()
            .mapLatest { address -> address?.let { homePoint(it) } }

    val uiState: StateFlow<CarnetUiState> = combine(finished, manual, home, day) { done, added, point, now ->
        val all = done + added.map { CarnetTrip(it.date, it.days, listOf(CarnetStop(it.name, it.lat, it.lng, it.countryCode)), manual = true) }
        CarnetUiState.Ready(carnetOf(all, point), added, now, canAdd = places != null) as CarnetUiState
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CarnetUiState.Loading)

    private val _adding = MutableStateFlow(AddState.Idle)
    val adding: StateFlow<AddState> = _adding.asStateFlow()

    /** A trip as the carnet counts it: its placed steps with their country, and its photos. */
    private fun counted(trip: Trip): Flow<CarnetTrip> {
        val steps = trips?.observeSteps(trip.id)?.catch { e -> if (e is CancellationException) throw e else emit(emptyList()) } ?: flowOf(emptyList())
        val count = photos?.observePhotos(tripAlbumFolder(trip.id))?.map { it.size }
            ?.catch { e -> if (e is CancellationException) throw e else emit(0) } ?: flowOf(0)
        return combine(steps.mapLatest { list ->
            list.filter { it.hasPosition }.map { CarnetStop(it.place, it.lat!!, it.lng!!, countryOf(it.lat, it.lng)) }
        }, count) { stops, n -> CarnetTrip(trip.startDate, tripLength(trip), stops, n) }
    }

    private suspend fun countryOf(lat: Double, lng: Double): String? {
        val key = "${(lat * 10).roundToInt()}/${(lng * 10).roundToInt()}"
        COUNTRIES[key]?.let { return it }
        val code = try {
            locator?.countryCodeOf(lat, lng)?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (code != null) COUNTRIES[key] = code
        return code
    }

    private suspend fun homePoint(address: String): Pair<Double, Double>? = try {
        locator?.find(address)?.let { it.lat to it.lng }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** Adds [place] to the carnet: the sheet checked the date (past) and the days (1 to 365) first. */
    fun add(place: Place, date: LocalDate, days: Int) {
        val repo = places ?: return
        _adding.value = AddState.Saving
        viewModelScope.launch {
            _adding.value = try {
                val code = withTimeoutOrNull(COUNTRY_WAIT_MS) { countryOf(place.lat, place.lng) }
                // Offline, Firestore keeps the write and sends it later: the carnet shows it at once.
                withTimeoutOrNull(SAVE_WAIT_MS) {
                    repo.addPlace(
                    memberId,
                    TravelPlace(
                        id = "",
                        name = destinationName(place).ifBlank { place.title.orEmpty() }.take(TravelPlace.MAX_NAME),
                        country = code?.let(::countryName),
                        countryCode = code,
                        lat = place.lat,
                        lng = place.lng,
                        date = date,
                        days = days,
                    ),
                )
                }
                AddState.Saved
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AddState.Failed
            }
        }
    }

    fun remove(placeId: String) {
        val repo = places ?: return
        viewModelScope.launch {
            try {
                repo.deletePlace(memberId, placeId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline: the cache removes it now, Firestore sends it later.
            }
        }
    }

    fun resetAdding() {
        _adding.value = AddState.Idle
    }

    private companion object {
        /** The countries already found, for the life of the app: the carnet and the trips ask the same points. */
        val COUNTRIES = ConcurrentHashMap<String, String>()
        const val COUNTRY_WAIT_MS = 6_000L
        const val SAVE_WAIT_MS = 5_000L
    }
}
