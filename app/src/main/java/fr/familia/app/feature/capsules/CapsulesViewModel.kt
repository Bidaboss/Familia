package fr.familia.app.feature.capsules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.CapsuleRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.openedCapsuleOrder
import fr.familia.app.core.data.sealedCapsuleOrder
import fr.familia.app.core.model.Capsule
import fr.familia.app.core.model.Family
import fr.familia.app.feature.trips.firstNames
import fr.familia.app.feature.trips.joinNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A capsule of a list, with its recipients by first name (« Noé et Lou »), and whether one wrote it. */
data class CapsuleRow(val capsule: Capsule, val recipients: String, val isMine: Boolean)

sealed interface CapsulesUiState {
    data object Loading : CapsulesUiState
    data object Failed : CapsulesUiState

    /** [opened]: opened for me, or written by me and opened; [sealed]: mine, still closed. */
    data class Ready(val opened: List<CapsuleRow>, val sealed: List<CapsuleRow>) : CapsulesUiState {
        val isEmpty: Boolean get() = opened.isEmpty() && sealed.isEmpty()
    }
}

internal fun capsuleRow(capsule: Capsule, family: Family, me: String) =
    CapsuleRow(capsule, joinNames(firstNames(capsule.recipientIds, family)), capsule.authorId == me)

/** « Mes capsules » (PRD, récit 39): those opened for me, then mine still sealed. */
class CapsulesViewModel(capsules: CapsuleRepository?, members: MemberRepository?, currentMemberId: String) : ViewModel() {

    val uiState: StateFlow<CapsulesUiState> =
        if (capsules == null || members == null) {
            MutableStateFlow(CapsulesUiState.Ready(emptyList(), emptyList())) // Firebase not configured (previews).
        } else {
            combine(capsules.observeWrittenBy(currentMemberId), capsules.observeOpenedFor(currentMemberId), members.observeFamily()) { mine, forMe, family ->
                // A capsule one wrote for oneself shows once.
                val opened = (forMe + mine.filter { it.isOpened }).distinctBy { it.id }.sortedWith(openedCapsuleOrder)
                val ready: CapsulesUiState = CapsulesUiState.Ready(
                    opened = opened.map { capsuleRow(it, family, currentMemberId) },
                    sealed = mine.filterNot { it.isOpened }.sortedWith(sealedCapsuleOrder).map { capsuleRow(it, family, currentMemberId) },
                )
                ready
            }
                .catch { e -> if (e is CancellationException) throw e else emit(CapsulesUiState.Failed) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CapsulesUiState.Loading)
        }
}

sealed interface CapsuleDetailUiState {
    data object Loading : CapsuleDetailUiState

    /** Removed meanwhile, or not readable (still sealed for someone else). */
    data object Missing : CapsuleDetailUiState

    data class Ready(val row: CapsuleRow, val removeFailed: Boolean = false) : CapsuleDetailUiState

    data object Removed : CapsuleDetailUiState
}

/** One capsule. Its author reads it at any time and may remove it; the recipients once opened. */
class CapsuleDetailViewModel(
    private val capsules: CapsuleRepository?,
    members: MemberRepository?,
    private val currentMemberId: String,
    private val capsuleId: String,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val removal = MutableStateFlow<Boolean?>(null) // null: nothing asked; true: removed; false: failed.

    val uiState: StateFlow<CapsuleDetailUiState> =
        if (capsules == null || members == null) {
            MutableStateFlow(CapsuleDetailUiState.Missing)
        } else {
            combine(capsules.observeCapsule(capsuleId), members.observeFamily(), removal) { capsule, family, removed ->
                val state: CapsuleDetailUiState = when {
                    removed == true -> CapsuleDetailUiState.Removed
                    capsule == null -> CapsuleDetailUiState.Missing
                    // The rules already hide it; this keeps a sealed one closed if a cache still holds it.
                    capsule.authorId != currentMemberId && currentMemberId !in capsule.openedFor -> CapsuleDetailUiState.Missing
                    else -> CapsuleDetailUiState.Ready(capsuleRow(capsule, family, currentMemberId), removed == false)
                }
                state
            }
                .catch { e -> if (e is CancellationException) throw e else emit(CapsuleDetailUiState.Missing) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CapsuleDetailUiState.Loading)
        }

    /** Removes the capsule and its photos, after the screen asked for confirmation. Its author only, while sealed. */
    fun remove() {
        val repo = capsules ?: return
        val ready = uiState.value as? CapsuleDetailUiState.Ready ?: return
        if (!ready.row.isMine || ready.row.capsule.isOpened) return
        viewModelScope.launch {
            removal.value = try {
                // Offline, the removal waits in Firestore and is sent later.
                withTimeoutOrNull(writeTimeoutMillis) { repo.deleteCapsule(capsuleId) }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        }
    }
}
