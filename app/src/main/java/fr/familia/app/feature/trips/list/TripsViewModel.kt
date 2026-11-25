package fr.familia.app.feature.trips.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Trip
import fr.familia.app.feature.trips.TripPhase
import fr.familia.app.feature.trips.canCreateTrip
import fr.familia.app.feature.trips.firstNames
import fr.familia.app.feature.trips.map.GeoPoint
import fr.familia.app.feature.trips.tripPhase
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

sealed interface TripsUiState {
    data object Loading : TripsUiState
    data object Error : TripsUiState

    /** Ongoing trips first, then upcoming ones, then past ones, most recent first. */
    data class Success(
        val ongoing: List<TripSummary>,
        val upcoming: List<TripSummary>,
        val past: List<TripSummary>,
        val today: LocalDate,
        /** Adults only: a child never creates a trip (PRD rév. 131). */
        val canCreate: Boolean = true,
    ) : TripsUiState {
        val isEmpty: Boolean get() = ongoing.isEmpty() && upcoming.isEmpty() && past.isEmpty()
    }
}

/** The trips this member follows (PRD, récit 12): only those, as the rules allow. */
@OptIn(ExperimentalCoroutinesApi::class)
class TripsViewModel(
    private val trips: TripRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val today: Flow<LocalDate> = currentDayFlow(),
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<TripsUiState> = attempt
        .flatMapLatest { n ->
            if (trips == null || members == null) {
                today.flatMapLatest { flowOf(TripsUiState.Success(emptyList(), emptyList(), emptyList(), it)) }
            } else {
                trips.observeFollowedTrips(currentMemberId).flatMapLatest { list ->
                    combine(placesOf(trips, list), members.observeFamily(), today) { places, family, day -> state(list, family, day, places) }
                }
                    .onStart { if (n > 0) emit(TripsUiState.Loading) }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(TripsUiState.Error)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsUiState.Loading)

    fun retry() {
        attempt.update { it + 1 }
    }

    /** Where it goes (its first destination with a position, else its first step with one) and how far (E-31). A trip whose steps cannot be read has neither. */
    private fun placesOf(repo: TripRepository, list: List<Trip>): Flow<Map<String, TripWhere>> =
        if (list.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(
                list.map { trip ->
                    combine(repo.observeSteps(trip.id), repo.observeCovers(trip.id)) { steps, covers -> steps to covers }
                        .map { (steps, covers) ->
                            // The photo is the one of the first destination, never of a transport nor its departure (retour du 25/11 soir);
                            // a trip without a stay takes the first arrival or activity, as its cover does.
                            val first = fr.familia.app.feature.trips.detail.coverPlaces(trip, steps).firstOrNull { it.fromStep }
                            trip.id to TripWhere(
                                point = first?.point ?: steps.firstOrNull { it.hasPosition }?.let { GeoPoint(it.lat!!, it.lng!!) },
                                name = first?.name,
                                km = fr.familia.app.feature.trips.detail.tripFigures(trip, steps, 0)?.distanceKm,
                                // « Autre photo »: the photo chosen for the first destination, for everyone.
                                cover = first?.stepId?.let { covers[it] },
                            )
                        }
                        .catch { e -> if (e is CancellationException) throw e else emit(trip.id to TripWhere(null, null, null)) }
                },
            ) { pairs -> pairs.toMap() }
        }

    private fun state(list: List<Trip>, family: Family, day: LocalDate, places: Map<String, TripWhere>): TripsUiState {
        val byId = family.members.associateBy { it.id }
        val byPhase = list.map { trip ->
            TripSummary(
                trip,
                firstNames(trip.participantIds, family),
                people = trip.participantIds.mapNotNull { byId[it] },
                isParticipant = currentMemberId in trip.participantIds,
                place = places[trip.id]?.point,
                placeName = places[trip.id]?.name?.ifEmpty { null },
                distanceKm = places[trip.id]?.km,
                cover = places[trip.id]?.cover,
            )
        }
            .groupBy { tripPhase(it.trip, day) }
        return TripsUiState.Success(
            ongoing = byPhase[TripPhase.Ongoing].orEmpty(),
            upcoming = byPhase[TripPhase.Upcoming].orEmpty(),
            past = byPhase[TripPhase.Past].orEmpty().reversed(),
            today = day,
            canCreate = family.members.firstOrNull { it.id == currentMemberId }?.let(::canCreateTrip) == true,
        )
    }
}

/** What the steps of a trip tell the list: where it goes, its name, how far it goes. */
private data class TripWhere(val point: GeoPoint?, val name: String?, val km: Int?, val cover: fr.familia.app.core.place.PlacePhoto? = null)
