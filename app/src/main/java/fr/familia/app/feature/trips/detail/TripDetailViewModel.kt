package fr.familia.app.feature.trips.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripItem
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
        /** The checklist (récit 13): what is left to do first. */
        val items: List<TripItem> = emptyList(),
        /** The participants, who can be put in charge of a line. */
        val assignees: List<ItemAssignee> = emptyList(),
        /**
         * « Partager » (récit 31): only when the whole family follows the trip. A surprise trip,
         * hidden from someone, must never reach them through a family group.
         */
        val canShare: Boolean = false,
    ) : TripDetailUiState
}

/** A participant offered as the person in charge of a line of the checklist. */
data class ItemAssignee(val id: String, val name: String)

/** The longest label of a line of the checklist, as the rules allow. */
const val ITEM_LABEL_MAX = 80

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
                    combine(trips.observeSteps(tripId), trips.observeItems(tripId)) { steps, items -> steps to items },
                    members.observeFamily(),
                    today,
                    sendFailed,
                ) { trip, (steps, items), family, day, failed -> state(trip, steps, items, family, day, failed) }
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

    /** Adds a line to the checklist, maybe in the charge of a participant. Blank labels are ignored. */
    fun addItem(label: String, assigneeId: String?) {
        val repo = trips ?: return
        val item = item(repo.newItemId(tripId), label, assigneeId, done = false) ?: return
        write { repo.saveItem(tripId, item, isNew = true) }
    }

    /** Changes the label or the person in charge of a line, keeping whether it is done. */
    fun editItem(item: TripItem, label: String, assigneeId: String?) {
        val repo = trips ?: return
        val edited = item(item.id, label, assigneeId, item.done) ?: return
        write { repo.saveItem(tripId, edited, isNew = false) }
    }

    /** « Je m'en charge » (récit 62): a free line is now mine. */
    fun takeItem(item: TripItem) {
        val repo = trips ?: return
        val member = me ?: return
        if (item.assigneeId != null) return
        write { repo.saveItem(tripId, item.copy(assigneeId = member.id, assigneeName = member.firstName), isNew = false) }
    }

    /** « Je ne peux plus »: a line I took is free again. */
    fun releaseItem(item: TripItem) {
        val repo = trips ?: return
        if (item.assigneeId != currentMemberId) return
        write { repo.saveItem(tripId, item.copy(assigneeId = null, assigneeName = null), isNew = false) }
    }

    fun setItemDone(itemId: String, done: Boolean) {
        val repo = trips ?: return
        write { repo.setItemDone(tripId, itemId, done) }
    }

    fun deleteItem(itemId: String) {
        val repo = trips ?: return
        write { repo.deleteItem(tripId, itemId) }
    }

    private fun item(id: String, label: String, assigneeId: String?, done: Boolean): TripItem? {
        val clean = label.trim().take(ITEM_LABEL_MAX)
        if (clean.isEmpty()) return null
        val assignee = (uiState.value as? TripDetailUiState.Success)?.assignees?.firstOrNull { it.id == assigneeId }
        return TripItem(id, clean, assignee?.id, assignee?.name, done)
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

    private fun state(
        trip: Trip?,
        steps: List<TripStep>,
        items: List<TripItem>,
        family: Family,
        day: LocalDate,
        failed: Boolean,
    ): TripDetailUiState {
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
            items = items,
            assignees = trip.participantIds.mapNotNull { id -> family.members.firstOrNull { it.id == id } }
                .map { ItemAssignee(it.id, it.firstName) }
                .sortedBy { it.name.lowercase() },
            canShare = family.members.all { it.id in trip.readerIds },
        )
    }
}
