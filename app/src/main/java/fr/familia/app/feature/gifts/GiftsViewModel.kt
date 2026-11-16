package fr.familia.app.feature.gifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.upcomingBirthdays
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

/** The lists of one person, as the Cadeaux tab shows them. */
data class GiftPersonSection(
    val member: Member,
    val isMe: Boolean,
    /** [GiftPersonSection.member] is the signed-in member or one of their children without the app. */
    val keptByMe: Boolean,
    /** Their « Mes envies », null while nothing was added to it yet. */
    val wishes: GiftList?,
    /** Their lists for an occasion that are not revealed yet, by date. */
    val lists: List<GiftList>,
)

sealed interface GiftsUiState {
    data object Loading : GiftsUiState

    data class Success(
        val today: LocalDate,
        /** The next birthdays, shown first (carte DESIGN-4: the tab replaces the Anniversaires tab). */
        val birthdays: List<Birthday>,
        /** Adults may prepare a surprise list from a birthday. */
        val canPrepareSurprise: Boolean,
        val sections: List<GiftPersonSection>,
        /** Revealed lists, newest first, with the first name of their beneficiary. */
        val archived: List<Pair<GiftList, String>>,
        /** What each « Envies » holds, by list id, as this member may see it; missing while it loads. */
        val wishPreviews: Map<String, WishPreview> = emptyMap(),
    ) : GiftsUiState

    data object Error : GiftsUiState
}

/**
 * Cadeaux tab (PRD, récit 7): the signed-in member and the children whose lists they keep first,
 * then everyone who has a list they can see. Surprise lists for the member never reach this
 * screen: the server does not send them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GiftsViewModel(
    private val gifts: GiftRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val today: Flow<LocalDate> = currentDayFlow(),
    /** Who really reads: the admin principal in « Voir comme », else the member themselves. */
    private val readerId: String = currentMemberId,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<GiftsUiState> = attempt
        .flatMapLatest { n ->
            if (gifts == null || members == null) {
                flowOf(GiftsUiState.Error)
            } else {
                combine(members.observeFamily(), gifts.observeVisibleLists(currentMemberId), today) { family, lists, day ->
                    giftsState(family, lists, currentMemberId, day)
                }
                    .flatMapLatest { state -> withPreviews(gifts, state) }
                    .onStart { if (n > 0) emit(GiftsUiState.Loading) }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(GiftsUiState.Error)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GiftsUiState.Loading)

    fun retry() {
        attempt.update { it + 1 }
    }

    /**
     * Adds what each « Envies » holds: one live read of its items, never more than the list screen
     * itself reads. The beneficiary (and, in « Voir comme », the admin who reads for them) only counts
     * the ideas they may see, so a surprise never shows through the number. A read that fails leaves
     * the card as it was, without the preview.
     */
    private fun withPreviews(repo: GiftRepository, state: GiftsUiState): Flow<GiftsUiState> {
        if (state !is GiftsUiState.Success) return flowOf(state)
        val wishes = state.sections.mapNotNull { it.wishes }
        if (wishes.isEmpty()) return flowOf(state)
        val previews = wishes.map { list ->
            val hidden = !list.revealed && (list.beneficiaryId == currentMemberId || list.beneficiaryId == readerId)
            repo.observeItems(list.id, hiddenFromMe = hidden)
                .map<List<GiftItem>, Pair<String, WishPreview>?> { items -> list.id to wishPreview(items) }
                .onStart { emit(null) }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(null)
                }
        }
        return combine(previews) { all -> state.copy(wishPreviews = all.filterNotNull().toMap()) }
    }
}

/** What a list of wishes holds: how many, and the first titles for the card. */
data class WishPreview(val count: Int, val titles: List<String>)

/** The preview of [items]: their number, and the first [WISH_PREVIEW_TITLES] titles in alphabetical order. */
internal fun wishPreview(items: List<GiftItem>): WishPreview =
    WishPreview(items.size, items.map { it.title.trim() }.sortedBy { it.lowercase() }.take(WISH_PREVIEW_TITLES))

private const val WISH_PREVIEW_TITLES = 3

/** Groups the visible lists by beneficiary. Pure, so the tests check it without Firestore. */
internal fun giftsState(family: Family, lists: List<GiftList>, meId: String, today: LocalDate): GiftsUiState {
    val me = family.members.firstOrNull { it.id == meId } ?: return GiftsUiState.Error
    val byId = family.members.associateBy { it.id }
    // Lists of removed members are not shown.
    val known = lists.filter { it.beneficiaryId in byId }
    val active = known.filter { !it.revealed }
    val kept = keptMembers(me, family)
    val keptIds = kept.map { it.id }.toSet()
    val others = family.members
        .filter { it.id !in keptIds && active.any { list -> list.beneficiaryId == it.id } }
        .sortedBy { it.firstName.lowercase() }
    val sections = (kept + others).map { member ->
        val theirs = active.filter { it.beneficiaryId == member.id }
        GiftPersonSection(
            member = member,
            isMe = member.id == me.id,
            keptByMe = member.id in keptIds,
            wishes = theirs.firstOrNull { it.kind == GiftListKind.Wishes },
            lists = theirs.filter { it.kind != GiftListKind.Wishes }
                .sortedWith(compareBy<GiftList>({ it.date ?: LocalDate.MAX }, { it.title.lowercase() })),
        )
    }
    val archived = known.filter { it.revealed }
        .sortedByDescending { it.date ?: LocalDate.MIN }
        .map { it to byId.getValue(it.beneficiaryId).firstName }
    return GiftsUiState.Success(
        today = today,
        birthdays = upcomingBirthdays(family.members, today).take(UPCOMING_BIRTHDAYS),
        canPrepareSurprise = me.profile == MemberProfile.Adult,
        sections = sections,
        archived = archived,
    )
}

/** How many birthdays the top of the tab shows; « Tous les anniversaires » opens the full list. */
private const val UPCOMING_BIRTHDAYS = 3
