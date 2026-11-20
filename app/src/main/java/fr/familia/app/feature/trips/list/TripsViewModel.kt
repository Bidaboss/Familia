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
                combine(trips.observeFollowedTrips(currentMemberId), members.observeFamily(), today) { list, family, day ->
                    state(list, family, day)
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

    private fun state(list: List<Trip>, family: Family, day: LocalDate): TripsUiState {
        val byId = family.members.associateBy { it.id }
        val byPhase = list.map { trip ->
            TripSummary(
                trip,
                firstNames(trip.participantIds, family),
                people = trip.participantIds.mapNotNull { byId[it] },
                isParticipant = currentMemberId in trip.participantIds,
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
