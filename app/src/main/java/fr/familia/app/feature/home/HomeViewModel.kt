package fr.familia.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.data.HelpRequestRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.RsvpRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.TripArrival
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * The Accueil tab (PRD, récit 29): what I have to do, the coming week, birthdays, the next occasion
 * and the trips I follow. Each module only feeds its card while it is switched on (a null
 * repository), and a card that cannot load stays hidden rather than blocking the others.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val members: MemberRepository?,
    private val events: EventRepository?,
    private val currentMemberId: String,
    private val rsvps: RsvpRepository? = null,
    private val helpRequests: HelpRequestRepository? = null,
    private val gifts: GiftRepository? = null,
    private val trips: TripRepository? = null,
    private val today: Flow<LocalDate> = currentDayFlow(),
) : ViewModel() {

    /** Read once and shared by the cards, so no card adds its own listener on the family. */
    private val family: Flow<Family>? =
        members?.observeFamily()?.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val uiState: StateFlow<HomeUiState> = today
        .flatMapLatest { day ->
            val shared = family
            if (shared == null || events == null) {
                flowOf(HomeUiState.Ready(day)) // Firebase not configured (previews).
            } else {
                combine(
                    shared,
                    events.observeUpcoming(day),
                    bonus(rsvps?.observeAllRsvps(), emptyMap<String, List<Rsvp>>()),
                    bonus(helpRequests?.observeMine(currentMemberId), emptyList<HelpRequest>()),
                    combine(nextOccasion(day), followedTrips(shared, day)) { o, t -> o to t },
                ) { fam, list, answers, help, extras ->
                    val me = fam.members.firstOrNull { it.id == currentMemberId }
                    if (me == null) {
                        HomeUiState.Ready(day)
                    } else {
                        val next = extras.first?.let { (giftList, claims) -> occasion(giftList, me, fam, claims, day) }
                        homeState(me, fam, day, list, answers, help, next, extras.second)
                    }
                }.catch { e ->
                    if (e is CancellationException) throw e
                    emit(HomeUiState.Ready(day))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** The next occasion list and how many of its gifts I reserved: one listener, for that list only. */
    private fun nextOccasion(day: LocalDate): Flow<Pair<GiftList, Int>?> {
        val repo = gifts ?: return flowOf(null)
        val next = bonus(repo.observeVisibleLists(currentMemberId), emptyList())
            .map { nextOccasionList(it, day) }
            .distinctUntilChanged()
        return next.flatMapLatest<GiftList?, Pair<GiftList, Int>?> { list ->
            when {
                list == null -> flowOf(null)
                // What is reserved for me never shows, so my own list needs no reading of the claims.
                list.beneficiaryId == currentMemberId -> flowOf(list to 0)
                else -> bonus(repo.observeClaims(list.id).map { claims -> claims.count { it.holderId == currentMemberId } }, 0)
                    .map { list to it }
            }
        }
    }

    /** The trips I follow that are not over, with the last « Bien arrivé » of the ongoing ones. */
    private fun followedTrips(family: Flow<Family>, day: LocalDate): Flow<List<HomeTrip>> {
        val repo = trips ?: return flowOf(emptyList())
        val shown = bonus(repo.observeFollowedTrips(currentMemberId), emptyList()).map { shownTrips(it, day) }.distinctUntilChanged()
        return shown.flatMapLatest { list ->
            val ongoing = list.filter { it.startDate <= day }
            val arrivals: Flow<Map<String, TripArrival?>> = if (ongoing.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(ongoing.map { trip -> bonus(repo.observeSteps(trip.id).map { trip.id to lastArrival(it) }, trip.id to null) }) {
                    it.toMap()
                }
            }
            combine(arrivals, family) { found, fam -> homeTrips(list, found, fam, day) }
        }
    }

    /** A card that cannot load shows nothing, and the others still show. */
    private fun <T> bonus(source: Flow<T>?, empty: T): Flow<T> =
        (source ?: flowOf(empty))
            .onStart { emit(empty) }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(empty)
            }
}
