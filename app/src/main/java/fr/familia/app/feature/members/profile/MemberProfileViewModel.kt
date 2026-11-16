package fr.familia.app.feature.members.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.SouvenirRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Souvenir
import fr.familia.app.core.model.SouvenirCounters
import fr.familia.app.core.model.canEditMember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MemberProfileUiState {
    data object Loading : MemberProfileUiState
    data object NotFound : MemberProfileUiState

    /**
     * [souvenirs]: on one's own page, every souvenir earned with the switch to show it; on someone
     * else's, only those they show (récit 34, carte EXPERT-3). [counters] only on one's own page.
     */
    data class Ready(
        val member: Member,
        val householdName: String?,
        val isMe: Boolean,
        val canEdit: Boolean,
        val souvenirs: List<Souvenir>,
        val counters: SouvenirCounters = SouvenirCounters(),
        val saveFailed: Boolean = false,
    ) : MemberProfileUiState
}

/** The page of a member, read-only, opened from the Famille tab. */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberProfileViewModel(
    private val members: MemberRepository?,
    private val souvenirs: SouvenirRepository?,
    private val currentMemberId: String,
    private val memberId: String,
) : ViewModel() {

    private val saveFailed = MutableStateFlow(false)
    private val isMe = memberId == currentMemberId

    private val mySouvenirs = when {
        souvenirs == null -> flowOf(emptyList())
        isMe -> souvenirs.observeMine(memberId)
        else -> souvenirs.observeShown(memberId)
    }
    private val counters = if (isMe && souvenirs != null) souvenirs.observeCounters(memberId) else flowOf(SouvenirCounters())

    val uiState: StateFlow<MemberProfileUiState> = (members?.observeFamily() ?: emptyFlow())
        .flatMapLatest<Family, MemberProfileUiState> { family ->
            val member = family.members.firstOrNull { it.id == memberId }
            val me = family.members.firstOrNull { it.id == currentMemberId }
            if (member == null) {
                flowOf(MemberProfileUiState.NotFound)
            } else {
                combine(mySouvenirs, counters, saveFailed) { list, count, failed ->
                    MemberProfileUiState.Ready(
                        member = member,
                        householdName = family.households.firstOrNull { it.id == member.householdId }?.name,
                        isMe = isMe,
                        canEdit = me != null && family.canEditMember(me, member),
                        souvenirs = list.sortedBy { it.earnedAt },
                        counters = count,
                        saveFailed = failed,
                    )
                }
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(MemberProfileUiState.NotFound)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemberProfileUiState.Loading)

    /** Shows a souvenir on one's own page, or hides it again. */
    fun setShown(key: String, shown: Boolean) {
        val repo = souvenirs ?: return
        if (!isMe) return
        saveFailed.value = false
        viewModelScope.launch {
            try {
                repo.setShown(memberId, key, shown)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveFailed.value = true
            }
        }
    }
}

