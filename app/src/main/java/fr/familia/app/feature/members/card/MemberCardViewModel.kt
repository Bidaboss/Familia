package fr.familia.app.feature.members.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberCardRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.MemberCard
import fr.familia.app.core.model.canChangePhoto
import fr.familia.app.core.model.cleaned
import fr.familia.app.core.ui.sendWrite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MemberCardUiState {
    data object Loading : MemberCardUiState

    /** Nothing to show: the card cannot be read, or it is empty and this member may not fill it in. */
    data object Hidden : MemberCardUiState

    data class Ready(
        val card: MemberCard,
        val firstName: String,
        val isMe: Boolean,
        val canEdit: Boolean,
        val saveFailed: Boolean = false,
    ) : MemberCardUiState
}

/**
 * The card of a member (récit 7 bis), on their page. Those who may change their photo change it too:
 * the member, an adult of their household for a child, and the admin principal. The rules say the same.
 */
class MemberCardViewModel(
    private val cards: MemberCardRepository?,
    members: MemberRepository?,
    private val currentMemberId: String,
    private val memberId: String,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val saveFailed = MutableStateFlow(false)

    val uiState: StateFlow<MemberCardUiState> = if (cards == null || members == null) {
        MutableStateFlow(MemberCardUiState.Hidden)
    } else {
        combine(cards.observeCard(memberId), members.observeFamily(), saveFailed) { card, family, failed ->
            val member = family.members.firstOrNull { it.id == memberId }
            val me = family.members.firstOrNull { it.id == currentMemberId }
            val canEdit = member != null && me != null && family.canChangePhoto(me, member)
            val shown = card ?: MemberCard(memberId)
            when {
                member == null -> MemberCardUiState.Hidden
                shown.isEmpty && !canEdit -> MemberCardUiState.Hidden
                else -> MemberCardUiState.Ready(shown, member.firstName, memberId == currentMemberId, canEdit, failed)
            }
        }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(MemberCardUiState.Hidden)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemberCardUiState.Loading)
    }

    /** Saves the card as typed. False when a field is too long: nothing is saved, the form stays open. */
    fun save(typed: MemberCard): Boolean {
        val repo = cards ?: return false
        val state = uiState.value as? MemberCardUiState.Ready ?: return false
        if (!state.canEdit) return false
        val card = typed.copy(memberId = memberId).cleaned() ?: return false
        saveFailed.value = false
        viewModelScope.launch {
            // Offline, the card shows at once and leaves with the network.
            if (sendWrite(writeTimeoutMillis) { repo.saveCard(card, currentMemberId) } == null) saveFailed.value = true
        }
        return true
    }

    fun dismissError() {
        saveFailed.value = false
    }
}

