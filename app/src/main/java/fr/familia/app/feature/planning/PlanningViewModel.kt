package fr.familia.app.feature.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.RsvpRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.concerns
import fr.familia.app.core.model.upcomingBirthdays
import fr.familia.app.feature.events.authorDisplayName
import fr.familia.app.feature.events.canManage
import fr.familia.app.feature.events.edit.TargetChoice
import fr.familia.app.feature.events.targetName
import fr.familia.app.feature.trips.firstNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * Planning tab: upcoming events and the members' next birthdays, day by day (PRD, récit 3).
 * Birthdays are computed from the birth dates, so they come back every year with nothing to type.
 * The list works offline from the Firestore cache, and moves on by itself at midnight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanningViewModel(
    private val events: EventRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val today: Flow<LocalDate> = currentDayFlow(),
    private val rsvps: RsvpRepository? = null,
    initialScope: PlanningScope = PlanningScope.Family,
    /** Keeps the filter on the phone (PlanningScopePreferences). */
    private val saveScope: (PlanningScope) -> Unit = {},
    private val trips: TripRepository? = null,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)
    private val scope = MutableStateFlow(initialScope)

    val uiState: StateFlow<PlanningUiState> = combine(attempt, today) { n, day -> n to day }
        .flatMapLatest { (n, day) ->
            if (events == null || members == null) {
                scope.map { PlanningUiState.Success(emptyList(), day, it) } // Firebase not configured (previews).
            } else {
                combine<List<Event>, Family, Map<String, List<Rsvp>>, PlanningScope, List<Trip>, PlanningUiState>(
                    events.observeUpcoming(day),
                    members.observeFamily(),
                    answers(),
                    scope,
                    followedTrips(),
                ) { list, family, answers, scope, trips ->
                    val upcoming = trips.filter { it.endDate >= day }
                    PlanningUiState.Success(
                        days = days(list, family, answers, day, scope, upcoming),
                        today = day,
                        scope = scope,
                        tripDays = tripDays(upcoming, day),
                    )
                }
                    .onStart { if (n > 0) emit(PlanningUiState.Loading) }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(PlanningUiState.Error)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanningUiState.Loading)

    fun retry() {
        attempt.update { it + 1 }
    }

    /** « Toute la famille » or « Mon foyer »; the choice is kept on the phone. */
    fun setScope(value: PlanningScope) {
        if (scope.value == value) return
        scope.value = value
        saveScope(value)
    }

    /** The counters are a bonus: if they cannot load, the planning still shows, without them. */
    private fun answers(): Flow<Map<String, List<Rsvp>>> =
        (rsvps?.observeAllRsvps() ?: flowOf(emptyMap()))
            .onStart { emit(emptyMap()) }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(emptyMap())
            }

    /** Trips are a bonus too: if they cannot load, the planning still shows, without them. */
    private fun followedTrips(): Flow<List<Trip>> =
        (trips?.observeFollowedTrips(currentMemberId) ?: flowOf(emptyList()))
            .onStart { emit(emptyList()) }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(emptyList())
            }

    /** Every day of [trips] from [today] on, at most a year ahead (the span of the month view). */
    private fun tripDays(trips: List<Trip>, today: LocalDate): Set<LocalDate> {
        val last = today.plusYears(1)
        return trips.flatMap { trip ->
            generateSequence(maxOf(trip.startDate, today)) { it.plusDays(1) }
                .takeWhile { it <= trip.endDate && it <= last }
                .toList()
        }.toSet()
    }

    private fun days(
        list: List<Event>,
        family: Family,
        answers: Map<String, List<Rsvp>>,
        today: LocalDate,
        scope: PlanningScope,
        trips: List<Trip> = emptyList(),
    ): List<PlanningDay> {
        val me = family.members.firstOrNull { it.id == currentMemberId }
        // An answer from a member who has left the family no longer counts.
        val memberIds = family.members.map { it.id }.toSet()
        // « Mon foyer » keeps what concerns me, and every birthday (PRD, carte AVIS-8).
        val shown = if (scope == PlanningScope.Household && me != null) list.filter { concerns(it.target, me, family) } else list
        val eventsByDay = shown.groupBy { it.date }
        val birthdaysByDay = upcomingBirthdays(family.members, today).groupBy { it.date }
        // A trip shows on its first day, or today while it is ongoing; the month view marks every day.
        val tripsByDay = trips.groupBy { maxOf(it.startDate, today) }
        return (eventsByDay.keys + birthdaysByDay.keys + tripsByDay.keys).sorted().map { date ->
            PlanningDay(
                date = date,
                birthdays = birthdaysByDay[date].orEmpty(),
                trips = tripsByDay[date].orEmpty().map {
                    PlanningTrip(it, firstNames(it.participantIds, family), ongoing = it.startDate <= today)
                },
                items = eventsByDay[date].orEmpty().map { event ->
                    val isMe = event.target is EventTarget.ForSelf && event.target.memberId == currentMemberId
                    PlanningItem(
                        event = event,
                        target = TargetChoice(event.target, if (isMe) null else targetName(event.target, family)),
                        authorName = authorDisplayName(event, family),
                        canManage = me != null && canManage(event, me, family),
                        coming = answers[event.id].orEmpty().count { it.memberId in memberIds && it.status == RsvpStatus.Yes },
                        maybe = answers[event.id].orEmpty().count { it.memberId in memberIds && it.status == RsvpStatus.Maybe },
                    )
                },
            )
        }
    }
}
