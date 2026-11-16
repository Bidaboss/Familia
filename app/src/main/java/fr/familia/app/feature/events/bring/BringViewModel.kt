package fr.familia.app.feature.events.bring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.BringRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.BringItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** One line as this member sees it. */
data class BringRow(
    val item: BringItem,
    /** I took it. */
    val isMine: Boolean,
    /** « Libérer » a line someone else took: its author, or one who manages the event. */
    val canRelease: Boolean,
    val canDelete: Boolean,
)

data class BringUiState(
    val rows: List<BringRow> = emptyList(),
    val isLoading: Boolean = true,
    val failed: Boolean = false,
    val isBusy: Boolean = false,
)

/**
 * « Qui apporte quoi » of an event (PRD, récit 4 bis): every member with the app adds a line or takes
 * one, and lets it go. [canManageEvent] (the event's « Modifier ») also frees and deletes any line.
 * Offline, the writes land in the cache at once and Firestore sends them when the network is back.
 */
class BringViewModel(
    private val bring: BringRepository?,
    members: MemberRepository?,
    private val currentMemberId: String,
    private val eventId: String,
    private val canManageEvent: Boolean,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private data class Action(val busy: Boolean = false, val failed: Boolean = false)

    private val action = MutableStateFlow(Action())
    private var myName = ""

    val uiState: StateFlow<BringUiState> = run {
        val repo = bring
        val memberRepo = members
        if (repo == null || memberRepo == null) {
            flowOf(BringUiState(isLoading = false))
        } else {
            combine(repo.observe(eventId), memberRepo.observeFamily(), action) { items, family, act ->
                myName = family.members.firstOrNull { it.id == currentMemberId }?.firstName.orEmpty()
                BringUiState(
                    rows = items.map { item ->
                        val mine = item.takerId == currentMemberId
                        BringRow(
                            item = item,
                            isMine = mine,
                            canRelease = item.takerId != null && (mine || item.authorId == currentMemberId || canManageEvent),
                            canDelete = item.authorId == currentMemberId || canManageEvent,
                        )
                    },
                    isLoading = false,
                    failed = act.failed,
                    isBusy = act.busy,
                )
            }.catch { emit(BringUiState(isLoading = false, failed = true)) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BringUiState())

    /** Adds [label], taken by me at once when [take] is set. False when the label is empty or too long. */
    fun add(label: String, take: Boolean): Boolean {
        val clean = label.trim()
        val repo = bring ?: return false
        if (clean.isEmpty() || clean.length > BringItem.MAX_LABEL || myName.isEmpty()) return false
        write { repo.add(eventId, clean, currentMemberId, myName, take) }
        return true
    }

    fun take(itemId: String) {
        val repo = bring ?: return
        val row = uiState.value.rows.firstOrNull { it.item.id == itemId } ?: return
        if (row.item.takerId != null || myName.isEmpty()) return
        write { repo.take(eventId, itemId, currentMemberId, myName) }
    }

    fun release(itemId: String) {
        val repo = bring ?: return
        val row = uiState.value.rows.firstOrNull { it.item.id == itemId } ?: return
        if (!row.canRelease) return
        write { repo.release(eventId, itemId) }
    }

    fun delete(itemId: String) {
        val repo = bring ?: return
        val row = uiState.value.rows.firstOrNull { it.item.id == itemId } ?: return
        if (!row.canDelete) return
        write { repo.delete(eventId, itemId) }
    }

    fun dismissError() {
        action.update { it.copy(failed = false) }
    }

    private fun write(block: suspend () -> Unit) {
        if (action.value.busy) return
        viewModelScope.launch {
            action.value = Action(busy = true)
            action.value = try {
                withTimeoutOrNull(writeTimeoutMillis) { block() }
                Action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Action(failed = true)
            }
        }
    }
}
