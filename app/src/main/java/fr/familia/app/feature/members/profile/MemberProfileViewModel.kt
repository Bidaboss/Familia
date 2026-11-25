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
import fr.familia.app.feature.souvenirs.SouvenirCelebrations
import fr.familia.app.feature.souvenirs.SouvenirKind
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
import java.time.Instant

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
        /** The newest souvenir not yet celebrated on this phone, for the « Bravo » sheet (my page only). */
        val bravo: Souvenir? = null,
    ) : MemberProfileUiState
}

/** The page of a member, read-only, opened from the Famille tab. */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberProfileViewModel(
    private val members: MemberRepository?,
    private val souvenirs: SouvenirRepository?,
    private val currentMemberId: String,
    private val memberId: String,
    private val celebrations: SouvenirCelebrations? = null,
) : ViewModel() {

    private val saveFailed = MutableStateFlow(false)
    private val isMe = memberId == currentMemberId
    private val celebrated = MutableStateFlow(if (isMe) celebrations?.celebrated(memberId).orEmpty() else emptySet())

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
                combine(mySouvenirs, counters, saveFailed, celebrated) { list, count, failed, done ->
                    MemberProfileUiState.Ready(
                        member = member,
                        householdName = family.households.firstOrNull { it.id == member.householdId }?.name,
                        isMe = isMe,
                        canEdit = me != null && family.canEditMember(me, member),
                        souvenirs = list.sortedBy { it.earnedAt },
                        counters = count,
                        saveFailed = failed,
                        bravo = if (isMe && celebrations != null) {
                            list.filter { it.key !in done && SouvenirKind.of(it.key) != null }.maxByOrNull { it.earnedAt ?: Instant.EPOCH }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        .catch { e ->
            if (e is CancellationException) throw e
            emit(MemberProfileUiState.NotFound)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemberProfileUiState.Loading)

    /**
     * Closes the « Bravo » sheet. Every souvenir already earned counts as celebrated, so a member who
     * opens the page for the first time sees one sheet only, for the newest.
     */
    fun dismissBravo() {
        val state = uiState.value as? MemberProfileUiState.Ready ?: return
        val keys = state.souvenirs.map { it.key }.toSet()
        celebrations?.markCelebrated(memberId, keys)
        celebrated.value = celebrated.value + keys
    }
}
