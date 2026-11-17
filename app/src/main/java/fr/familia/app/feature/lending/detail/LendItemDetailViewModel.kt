package fr.familia.app.feature.lending.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.LendItemRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.writeOrQueue
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.LendItem
import fr.familia.app.core.model.LendStatus
import fr.familia.app.core.model.Member
import fr.familia.app.feature.lending.canAskLendItem
import fr.familia.app.feature.lending.canManageLendItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

sealed interface LendItemDetailUiState {
    data object Loading : LendItemDetailUiState

    /** Removed, or never readable: the screen goes back once it was shown. */
    data object Missing : LendItemDetailUiState

    data class Ready(
        val item: LendItem,
        val isMine: Boolean,
        /** I asked for it, or I have it. */
        val iAmBorrower: Boolean,
        val canManage: Boolean,
        /** « Je voudrais l'emprunter »: an adult, not the owner, on a free thing. */
        val canAsk: Boolean,
        /** « D'accord » and « Pas cette fois »: the owner, on an ask. */
        val canAnswer: Boolean,
        /** « Rendu »: the owner or the borrower, on a loan. */
        val canReturn: Boolean,
        /** « Retirer ma demande »: the one who asked. */
        val canDropAsk: Boolean,
        val actionFailed: Boolean = false,
    ) : LendItemDetailUiState
}

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

/** One thing to lend (PRD, récit 51): ask, answer, « Rendu », and for its owner, change or remove. */
@OptIn(ExperimentalCoroutinesApi::class)
class LendItemDetailViewModel(
    private val items: LendItemRepository?,
    private val members: MemberRepository?,
    /** The member reading, the admin principal in « Voir comme »: rights are theirs. */
    private val currentMemberId: String,
    private val itemId: String,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val failed = MutableStateFlow(false)
    private var me: Member? = null

    val uiState: StateFlow<LendItemDetailUiState> = failed
        .flatMapLatest { didFail -> load(didFail) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LendItemDetailUiState.Loading)

    private fun load(didFail: Boolean): Flow<LendItemDetailUiState> {
        val itemRepo = items ?: return flowOf(LendItemDetailUiState.Missing)
        val memberRepo = members ?: return flowOf(LendItemDetailUiState.Missing)
        return combine(itemRepo.observe(itemId, currentMemberId), memberRepo.observeFamily()) { item, family -> build(item, family, didFail) }
            .catch { emit(LendItemDetailUiState.Missing) }
    }

    private fun build(item: LendItem?, family: Family, didFail: Boolean): LendItemDetailUiState {
        val reader = family.members.firstOrNull { it.id == currentMemberId }
        if (item == null || reader == null) return LendItemDetailUiState.Missing
        me = reader
        val isMine = item.ownerId == reader.id
        val iAmBorrower = item.status != LendStatus.Available && item.borrowerId == reader.id
        return LendItemDetailUiState.Ready(
            item = item,
            isMine = isMine,
            iAmBorrower = iAmBorrower,
            canManage = canManageLendItem(item, reader, family),
            canAsk = canAskLendItem(item, reader),
            canAnswer = isMine && item.status == LendStatus.Asked,
            canReturn = (isMine || iAmBorrower) && item.status == LendStatus.Lent,
            canDropAsk = iAmBorrower && item.status == LendStatus.Asked,
            actionFailed = didFail,
        )
    }

    /** « Je voudrais l'emprunter », with optional days: from the start of [from], until [until]. */
    fun ask(from: LocalDate?, until: LocalDate?) {
        val state = uiState.value as? LendItemDetailUiState.Ready ?: return
        val who = me ?: return
        val repo = items ?: return
        if (!state.canAsk) return
        val start = from?.atStartOfDay(PARIS)?.toInstant()
        // The return is the start of its day, so a return before the start is never sent.
        val end = until?.takeIf { from == null || !it.isBefore(from) }?.atStartOfDay(PARIS)?.toInstant()
        perform { repo.ask(itemId, state.item.ownerId, who, start, end) }
    }

    fun accept() {
        val state = uiState.value as? LendItemDetailUiState.Ready ?: return
        val repo = items ?: return
        if (state.canAnswer) perform { repo.accept(itemId) }
    }

    /** « Pas cette fois », « Retirer ma demande » or « Rendu »: the thing is free again. */
    fun release() {
        val state = uiState.value as? LendItemDetailUiState.Ready ?: return
        val repo = items ?: return
        if (state.canAnswer || state.canDropAsk || state.canReturn) perform { repo.release(itemId) }
    }

    fun remove() {
        val state = uiState.value as? LendItemDetailUiState.Ready ?: return
        val repo = items ?: return
        if (state.canManage) perform { repo.delete(itemId) }
    }

    // Offline, the write lands in the cache at once: only a refusal or an error counts as failed.
    private fun perform(write: suspend () -> Unit) {
        failed.value = false
        viewModelScope.launch {
            // A refusal that comes once the network is back is said too.
            val ok = writeOrQueue(viewModelScope, writeTimeoutMillis, onLateFailure = { failed.value = true }) { write() }
            if (!ok) failed.value = true
        }
    }
}
