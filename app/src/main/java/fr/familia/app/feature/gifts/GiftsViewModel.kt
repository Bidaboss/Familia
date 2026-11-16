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
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.isPermanent
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
    /** Their lists for an occasion that are not revealed yet, by date. Empty with the single list. */
    val lists: List<GiftList>,
    /** « Idées de la famille » for them (récit 9 ter), null while nobody added one; never for oneself. */
    val ideas: GiftList? = null,
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
        /** What each « Envies » and « Idées de la famille » holds, by list id, as this member may see it; missing while it loads. */
        val wishPreviews: Map<String, WishPreview> = emptyMap(),
        /** The items read for the cards, by list id: what [wishPreviews] counts from. */
        val listItems: Map<String, List<GiftItem>> = emptyMap(),
        /** One list of wishes per person, and the ideas of the family (récit 9 ter): no list for an occasion. */
        val singleList: Boolean = false,
        /** The occasions to prepare (récit 9 ter): the next Christmas, then the next birthdays. */
        val occasions: List<GiftMoment> = emptyList(),
        /** The occasion chosen, or null for everything. */
        val moment: GiftMoment? = null,
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
    /** The switch « Liste unique » (récit 9 ter). */
    private val singleList: Boolean = false,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)
    private val moment = MutableStateFlow<GiftMoment?>(null)

    val uiState: StateFlow<GiftsUiState> = attempt
        .flatMapLatest { n ->
            if (gifts == null || members == null) {
                flowOf(GiftsUiState.Error)
            } else {
                combine(members.observeFamily(), gifts.observeVisibleLists(currentMemberId), today) { family, lists, day ->
                    giftsState(family, lists, currentMemberId, day, singleList)
                }
                    .flatMapLatest { state -> withPreviews(gifts, state) }
                    .let { states -> combine(states, moment) { state, chosen -> forMoment(state, chosen) } }
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

    /** « Noël 2026 », « Anniversaire de … », or null to see everyone (récit 9 ter). */
    fun selectMoment(chosen: GiftMoment?) {
        moment.value = chosen
    }

    /**
     * Adds what each « Envies » holds: one live read of its items, never more than the list screen
     * itself reads, but only the wishes their beneficiary wrote or sees: a surprise idea added by
     * someone else never shows on the overview (the tab is shown to the whole family, beneficiary
     * included). A read that fails leaves the card as it was, without the preview.
     */
    private fun withPreviews(repo: GiftRepository, state: GiftsUiState): Flow<GiftsUiState> {
        if (state !is GiftsUiState.Success) return flowOf(state)
        // Only « Mes envies »: the card of the ideas of the family says nothing of what they hold, not
        // even how many, since the tab may be shown in front of their person.
        val wishes = state.sections.mapNotNull { it.wishes }
        if (wishes.isEmpty()) return flowOf(state)
        val previews = wishes.map { list ->
            repo.observeItems(list.id, hiddenFromMe = true)
                .map<List<GiftItem>, Pair<String, List<GiftItem>>?> { items -> list.id to items }
                .onStart { emit(null) }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(null)
                }
        }
        return combine(previews) { all -> state.copy(listItems = all.filterNotNull().toMap()) }
    }
}

/**
 * An occasion to prepare in the Cadeaux tab (récit 9 ter): it replaces the lists for an occasion.
 * Christmas shows everyone; a birthday shows its person. A wish counts for it when it says so, or
 * when it says nothing (« Quand tu veux »).
 */
sealed interface GiftMoment {
    data class Christmas(val year: Int) : GiftMoment
    data class BirthdayOf(val member: Member, val date: LocalDate) : GiftMoment
}

/** The next Christmas, then the next birthdays shown at the top of the tab. */
internal fun giftMoments(today: LocalDate, birthdays: List<Birthday>): List<GiftMoment> {
    val christmasYear = if (today > LocalDate.of(today.year, 12, 25)) today.year + 1 else today.year
    return listOf(GiftMoment.Christmas(christmasYear)) + birthdays.map { GiftMoment.BirthdayOf(it.member, it.date) }
}

/** The tab for [chosen]: the people it is for, and what counts for it on their cards. */
internal fun forMoment(state: GiftsUiState, chosen: GiftMoment?): GiftsUiState {
    if (state !is GiftsUiState.Success) return state
    val occasions = if (state.singleList) giftMoments(state.today, state.birthdays) else emptyList()
    val moment = chosen?.takeIf { state.singleList && it in occasions }
    val kept = when (moment) {
        is GiftMoment.BirthdayOf -> state.sections.filter { it.member.id == moment.member.id }
        else -> state.sections
    }
    // Only « Mes envies », and only what its person sees, for the occasion: the ideas of the family
    // never get a preview, whatever was read.
    val wishesIds = state.sections.mapNotNull { it.wishes?.id }.toSet()
    val previews = state.listItems.filterKeys { it in wishesIds }.mapValues { (_, items) ->
        wishPreview(items.filter { counts(it, moment) })
    }
    return state.copy(sections = kept, wishPreviews = previews, occasions = occasions, moment = moment)
}

private fun counts(item: GiftItem, moment: GiftMoment?): Boolean = when (moment) {
    null -> true
    is GiftMoment.Christmas -> item.occasion == null || item.occasion == GiftOccasion.Christmas
    is GiftMoment.BirthdayOf -> item.occasion == null || item.occasion == GiftOccasion.Birthday
}

/** What a list of wishes holds: how many, and the first titles for the card. */
data class WishPreview(val count: Int, val titles: List<String>)

/** The preview of [items]: their number, and the first [WISH_PREVIEW_TITLES] titles in alphabetical order. */
internal fun wishPreview(items: List<GiftItem>): WishPreview =
    items.filter { it.visibleToBeneficiary }.let { shown ->
        WishPreview(shown.size, shown.map { it.title.trim() }.sortedBy { it.lowercase() }.take(WISH_PREVIEW_TITLES))
    }

private const val WISH_PREVIEW_TITLES = 3

/** Groups the visible lists by beneficiary. Pure, so the tests check it without Firestore. */
internal fun giftsState(family: Family, lists: List<GiftList>, meId: String, today: LocalDate, singleList: Boolean = false): GiftsUiState {
    val me = family.members.firstOrNull { it.id == meId } ?: return GiftsUiState.Error
    val byId = family.members.associateBy { it.id }
    // Lists of removed members are not shown. The ideas of the family only exist with the single list,
    // and with it, the lists for an occasion are gone from the screen (récit 9 ter).
    val known = lists.filter { it.beneficiaryId in byId }
        .filter { if (singleList) it.kind.isPermanent else it.kind != GiftListKind.Ideas }
    val active = known.filter { !it.revealed }
    val kept = keptMembers(me, family)
    val keptIds = kept.map { it.id }.toSet()
    // With the single list, everyone shows: the family can note an idea for anybody.
    val others = family.members
        .filter { it.id !in keptIds && (singleList || active.any { list -> list.beneficiaryId == it.id }) }
        .sortedBy { it.firstName.lowercase() }
    val sections = (kept + others).map { member ->
        val theirs = active.filter { it.beneficiaryId == member.id }
        GiftPersonSection(
            member = member,
            isMe = member.id == me.id,
            keptByMe = member.id in keptIds,
            wishes = theirs.firstOrNull { it.kind == GiftListKind.Wishes },
            lists = theirs.filter { !it.kind.isPermanent }
                .sortedWith(compareBy<GiftList>({ it.date ?: LocalDate.MAX }, { it.title.lowercase() })),
            ideas = theirs.firstOrNull { it.kind == GiftListKind.Ideas && member.id != me.id },
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
        singleList = singleList,
    )
}

/** How many birthdays the top of the tab shows; « Tous les anniversaires » opens the full list. */
private const val UPCOMING_BIRTHDAYS = 3
