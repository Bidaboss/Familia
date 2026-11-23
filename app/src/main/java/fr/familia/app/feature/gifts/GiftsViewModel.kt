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
import fr.familia.app.core.model.isPermanent
import fr.familia.app.core.model.upcomingBirthdays
import fr.familia.app.core.model.householdGroups
import fr.familia.app.core.model.ideasListId
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
    /** « Idées de la famille » for them (récit 9 ter), null while nobody added one; never for oneself. */
    val ideas: GiftList? = null,
    /**
     * The household it is shown under (retour d'essai du 17/11 : « range les membres par foyers »), as in the
     * Famille tab; null for a member without a known household, under « Autres ». Unused for [keptByMe].
     */
    val householdName: String? = null,
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
        /**
         * With the single list (récit 9 ter): the people of [birthdays] whose ideas of the family hold
         * a surprise idea, kept from them (retour d'essai du 17/11). Never this member; the adults of
         * a child without the app see it, as the rules let them read the child's ideas.
         */
        val surprisePreparedFor: Set<String> = emptySet(),
        /** « Bouton surprise » (récit 63): one button to prepare a surprise, and the surprises in progress on top. */
        val surpriseButton: Boolean = false,
        /** The wishes added by someone else since this phone last opened the list, by list id. */
        val newWishes: Map<String, Int> = emptyMap(),
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
    /** When this phone last opened each list, from [GiftSeenPreferences]. */
    private val seen: Flow<Map<String, Long>> = flowOf(emptyMap()),
    /** The switch « Bouton surprise » (récit 63). */
    private val surpriseButton: Boolean = false,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<GiftsUiState> = attempt
        .flatMapLatest { n ->
            if (gifts == null || members == null) {
                flowOf(GiftsUiState.Error)
            } else {
                combine(members.observeFamily(), gifts.observeVisibleLists(currentMemberId), today) { family, lists, day ->
                    giftsState(family, lists, currentMemberId, day, surpriseButton)
                }
                    .flatMapLatest { state -> withPreviews(gifts, state) }
                    .let { states ->
                        combine(states, seen) { state, visits ->
                            withNewWishes(withWishPreviews(state), visits, currentMemberId)
                        }
                    }
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
     * itself reads, but only the wishes their beneficiary wrote or sees: a surprise idea added by
     * someone else never shows on the overview (the tab is shown to the whole family, beneficiary
     * included). A read that fails leaves the card as it was, without the preview.
     */
    private fun withPreviews(repo: GiftRepository, state: GiftsUiState): Flow<GiftsUiState> {
        if (state !is GiftsUiState.Success) return flowOf(state)
        // Only « Mes envies »: the card of the ideas of the family says nothing of what they hold, not
        // even how many, since the tab may be shown in front of their person.
        val wishes = state.sections.mapNotNull { it.wishes }
        val ideas = surpriseIdeasToRead(state)
        if (wishes.isEmpty() && ideas.isEmpty()) return flowOf(state)
        val previews = wishes.map { list ->
            repo.observeItems(list.id, hiddenFromMe = true)
                .map<List<GiftItem>, Pair<String, List<GiftItem>>?> { items -> list.id to items }
                .onStart { emit(null) }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(null)
                }
        }
        val read = if (previews.isEmpty()) flowOf(emptyMap()) else combine(previews) { all -> all.filterNotNull().toMap() }
        // Only whether one surprise idea is there, for the few birthdays shown: never how many.
        val prepared = ideas.map { memberId ->
            repo.observeItems(ideasListId(memberId), hiddenFromMe = false)
                .map<List<GiftItem>, String?> { items -> memberId.takeIf { items.any { !it.visibleToBeneficiary } } }
                .onStart { emit(null) }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(null)
                }
        }
        val preparedFor = if (prepared.isEmpty()) flowOf(emptySet()) else combine(prepared) { all -> all.filterNotNull().toSet() }
        return combine(read, preparedFor) { items, people -> state.copy(listItems = items, surprisePreparedFor = people) }
    }
}

/**
 * The people of the birthdays shown whose ideas of the family to read for a surprise in progress:
 * never this member (« Voir comme » them included: they are « me » then).
 */
internal fun surpriseIdeasToRead(state: GiftsUiState.Success): List<String> {
    // « Surprises en préparation » (récit 63) names everyone with a surprise, not only the next birthdays.
    val people = if (state.surpriseButton) state.sections.map { it.member.id } else state.birthdays.map { it.member.id }
    return people.distinct().filter { id -> state.sections.none { it.member.id == id && it.isMe } }
}

/**
 * The « nouvelles » of each list of wishes of someone else: the wishes its person sees, added since
 * this phone last opened it, by someone other than [meId]. Never for a list not opened yet.
 */
internal fun withNewWishes(state: GiftsUiState, seen: Map<String, Long>, meId: String): GiftsUiState {
    if (state !is GiftsUiState.Success) return state
    val others = state.sections.filter { !it.isMe }.mapNotNull { it.wishes?.id }.toSet()
    val counts = state.listItems.filterKeys { it in others }.mapNotNull { (id, items) ->
        val since = seen[id] ?: return@mapNotNull null
        val fresh = items.count { it.visibleToBeneficiary && it.authorId != meId && (it.addedAt?.toEpochMilli() ?: 0L) > since }
        if (fresh > 0) id to fresh else null
    }.toMap()
    return state.copy(newWishes = counts)
}

/**
 * What each « Mes envies » shows on the tab, from the items read: only what its person sees. The
 * ideas of the family never get a preview, whatever was read.
 */
internal fun withWishPreviews(state: GiftsUiState): GiftsUiState {
    if (state !is GiftsUiState.Success) return state
    val wishesIds = state.sections.mapNotNull { it.wishes?.id }.toSet()
    return state.copy(wishPreviews = state.listItems.filterKeys { it in wishesIds }.mapValues { (_, items) -> wishPreview(items) })
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
internal fun giftsState(
    family: Family,
    lists: List<GiftList>,
    meId: String,
    today: LocalDate,
    surpriseButton: Boolean = false,
): GiftsUiState {
    val me = family.members.firstOrNull { it.id == meId } ?: return GiftsUiState.Error
    val byId = family.members.associateBy { it.id }
    // Lists of removed members are not shown, and only « Mes envies » and the ideas of the family
    // (récit 9 ter): no list for an occasion any more.
    val known = lists.filter { it.beneficiaryId in byId && it.kind.isPermanent }
    val active = known.filter { !it.revealed }
    val kept = keptMembers(me, family)
    val keptIds = kept.map { it.id }.toSet()
    // Everyone shows: the family can note an idea for anybody.
    // The others by household, in the order of the Famille tab: households by name, adults then children.
    val others = householdGroups(family).flatMap { group -> group.members.filter { it.id !in keptIds } }
    val householdNames = family.households.associate { it.id to it.name }
    val sections = (kept + others).map { member ->
        val theirs = active.filter { it.beneficiaryId == member.id }
        GiftPersonSection(
            member = member,
            isMe = member.id == me.id,
            keptByMe = member.id in keptIds,
            wishes = theirs.firstOrNull { it.kind == GiftListKind.Wishes },
            ideas = theirs.firstOrNull { it.kind == GiftListKind.Ideas && member.id != me.id },
            householdName = householdNames[member.householdId],
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
        surpriseButton = surpriseButton,
    )
}

/** How many birthdays the top of the tab shows; « Tous les anniversaires » opens the full list. */
private const val UPCOMING_BIRTHDAYS = 3
