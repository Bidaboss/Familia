package fr.familia.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.data.HelpRequestRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.PollRepository
import fr.familia.app.core.data.RsvpRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.Poll
import fr.familia.app.core.model.PollVote
import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.core.model.TripArrival
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
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
    /** Null while the polls module is off: no block « Sondages ». */
    private val polls: PollRepository? = null,
    private val clock: () -> Instant = Instant::now,
    /**
     * Switch « Liste unique » (récit 9 ter): the lists of an occasion are gone from the Cadeaux tab,
     * so the home screen no longer points to one. The birthdays stay on their own card.
     */
    private val singleList: Boolean = false,
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
                // Only the weeks the home screen shows, and only my own answers (lectures Firestore).
                val window = events.observeBetween(day, day.plusDays(HomeWindow.TODO_DAYS))
                combine(
                    shared,
                    window,
                    myAnswers(window),
                    bonus(helpRequests?.observeMine(currentMemberId), emptyList<HelpRequest>()),
                    combine(nextOccasion(day), followedTrips(shared, day), openPolls()) { o, t, p -> Triple(o, t, p) },
                ) { fam, list, answers, help, extras ->
                    val me = fam.members.firstOrNull { it.id == currentMemberId }
                    if (me == null) {
                        HomeUiState.Ready(day)
                    } else {
                        val next = extras.first?.let { (giftList, claims) -> occasion(giftList, me, fam, claims, day) }
                        val (asked, votes) = extras.third
                        val now = clock()
                        val toVote = pollsToVote(me, asked, votes, now, day)
                        homeState(me, fam, day, list, answers, help, next, extras.second, toVote, pollsVoted(me, asked, votes, now), pollsOn = polls != null)
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
        if (singleList) return flowOf(null)
        val next = bonus(repo.observeVisibleLists(currentMemberId), emptyList())
            .map { nextOccasionList(it, currentMemberId, day) }
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

    /** The open polls that ask me, with their votes: one listener per such poll, a handful at most. */
    private fun openPolls(): Flow<Pair<List<Poll>, Map<String, List<PollVote>>>> {
        val repo = polls ?: return flowOf(emptyList<Poll>() to emptyMap())
        val asked = bonus(repo.observeMine(currentMemberId), emptyList())
            .map { list -> list.filter { showsOnHome(it, clock()) && currentMemberId in it.askedIds } }
            .distinctUntilChanged()
        return asked.flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(list to emptyMap())
            } else {
                combine(list.map { poll -> bonus(repo.observeVotes(poll.id).map { poll.id to it }, poll.id to emptyList()) }) {
                    list to it.toMap()
                }
            }
        }
    }

    /** A card that cannot load shows nothing, and the others still show. */
    private val _answerFailed = MutableStateFlow(false)

    /** Set when an answer from the Accueil (« J'y vais » / « Non ») did not reach the server. */
    val answerFailed: StateFlow<Boolean> = _answerFailed.asStateFlow()

    /**
     * My own answer to an event, straight from « À faire » (R1 bis). The card leaves the list once
     * the answer is read back, as if it was given on the event.
     */
    fun answer(eventId: String, going: Boolean) {
        val repo = rsvps ?: return
        viewModelScope.launch {
            try {
                // Offline, the write waits in the cache and is sent later: not a failure.
                withTimeoutOrNull(ANSWER_TIMEOUT_MILLIS) {
                    repo.setRsvp(eventId, currentMemberId, if (going) RsvpStatus.Yes else RsvpStatus.No, answeredBy = currentMemberId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _answerFailed.value = true
            }
        }
    }

    fun answerFailureShown() {
        _answerFailed.value = false
    }

    /** My answers to the events of [window]; read again only when the list of events changes. */
    private fun myAnswers(window: Flow<List<Event>>): Flow<Map<String, List<Rsvp>>> {
        val repo = rsvps ?: return flowOf(emptyMap())
        return window.map { list -> list.map { it.id } }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                // Unreadable: every answer stays unknown rather than « à répondre » (note C de #317).
                repo.observeAnswersOf(currentMemberId, ids).catch { e ->
                    if (e is CancellationException) throw e
                    emit(ids.associateWith { emptyList<Rsvp>() })
                }
            }
            .onStart { emit(emptyMap()) }
    }

    private fun <T> bonus(source: Flow<T>?, empty: T): Flow<T> =
        (source ?: flowOf(empty))
            .onStart { emit(empty) }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(empty)
            }
}

private const val ANSWER_TIMEOUT_MILLIS = 10_000L
