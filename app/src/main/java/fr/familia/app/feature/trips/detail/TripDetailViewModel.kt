package fr.familia.app.feature.trips.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.feature.trips.canManageTrip
import fr.familia.app.feature.trips.firstNames
import fr.familia.app.feature.trips.isParticipant
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

sealed interface TripDetailUiState {
    data object Loading : TripDetailUiState

    /** Deleted, or this member was unticked from the followers meanwhile. */
    data object Missing : TripDetailUiState
    data object Error : TripDetailUiState

    data class Success(
        val trip: Trip,
        val participants: List<String>,
        /** How many members follow the trip, participants included. */
        val followers: Int,
        val steps: List<TripStep>,
        /** Creator or admin principal: « Modifier le voyage ». */
        val canManage: Boolean,
        /** Adds steps and says « bien arrivé ». */
        val isParticipant: Boolean,
        val today: LocalDate,
        val sendFailed: Boolean = false,
    ) : TripDetailUiState
}

/**
 * One trip and its steps (PRD, récits 11 and 14). A participant says « bien arrivé » in one tap
 * for everyone of the trip; the followers are told by the server. Offline, the tap shows at once
 * and leaves when the network is back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripDetailViewModel(
    private val trips: TripRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val tripId: String,
    private val writeTimeoutMillis: Long = 4_000,
    private val today: Flow<LocalDate> = currentDayFlow(),
) : ViewModel() {

    private val attempt = MutableStateFlow(0)
    private val sendFailed = MutableStateFlow(false)
    private var me: Member? = null

    val uiState: StateFlow<TripDetailUiState> = attempt
        .flatMapLatest { n ->
            if (trips == null || members == null) {
                flowOf(TripDetailUiState.Missing) // Firebase not configured (previews).
            } else {
                combine(
                    trips.observeTrip(tripId),
                    trips.observeSteps(tripId),
                    members.observeFamily(),
                    today,
                    sendFailed,
                ) { trip, steps, family, day, failed -> state(trip, steps, family, day, failed) }
                    .onStart { if (n > 0) emit(TripDetailUiState.Loading) }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(TripDetailUiState.Error)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUiState.Loading)

    fun retry() {
        attempt.update { it + 1 }
    }

    /** « Bien arrivé » for [stepId], said by this member for everyone of the trip. */
    fun markArrived(stepId: String) {
        val repo = trips ?: return
        val member = me ?: return
        write { repo.markArrived(tripId, stepId, member) }
    }

    /** Takes back a « bien arrivé » tapped by mistake. */
    fun clearArrival(stepId: String) {
        val repo = trips ?: return
        write { repo.clearArrival(tripId, stepId) }
    }

    private fun write(block: suspend () -> Unit) {
        sendFailed.value = false
        viewModelScope.launch {
            val failed = try {
                // Still pending after the timeout: queued offline, Firestore sends it later.
                withTimeoutOrNull(writeTimeoutMillis) { block() }
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                true
            }
            sendFailed.value = failed
        }
    }

    private fun state(trip: Trip?, steps: List<TripStep>, family: Family, day: LocalDate, failed: Boolean): TripDetailUiState {
        val current = family.members.firstOrNull { it.id == currentMemberId }
        if (trip == null || current == null) return TripDetailUiState.Missing
        me = current
        return TripDetailUiState.Success(
            trip = trip,
            participants = firstNames(trip.participantIds, family),
            followers = trip.readerIds.count { id -> family.members.any { it.id == id } },
            steps = steps,
            canManage = canManageTrip(trip, current, family),
            isParticipant = isParticipant(trip, current),
            today = day,
            sendFailed = failed,
        )
    }
}
