package fr.familia.app.feature.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.upcomingBirthdays
import fr.familia.app.feature.events.authorDisplayName
import fr.familia.app.feature.events.canManage
import fr.familia.app.feature.events.edit.TargetChoice
import fr.familia.app.feature.events.targetName
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
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    val uiState: StateFlow<PlanningUiState> = combine(attempt, today) { n, day -> n to day }
        .flatMapLatest { (n, day) ->
            if (events == null || members == null) {
                flowOf(PlanningUiState.Success(emptyList(), day)) // Firebase not configured (previews).
            } else {
                combine<List<Event>, Family, PlanningUiState>(
                    events.observeUpcoming(day),
                    members.observeFamily(),
                ) { list, family -> PlanningUiState.Success(days(list, family, day), day) }
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

    private fun days(list: List<Event>, family: Family, today: LocalDate): List<PlanningDay> {
        val me = family.members.firstOrNull { it.id == currentMemberId }
        val eventsByDay = list.groupBy { it.date }
        val birthdaysByDay = upcomingBirthdays(family.members, today).groupBy { it.date }
        return (eventsByDay.keys + birthdaysByDay.keys).sorted().map { date ->
            PlanningDay(
                date = date,
                birthdays = birthdaysByDay[date].orEmpty(),
                items = eventsByDay[date].orEmpty().map { event ->
                    val isMe = event.target is EventTarget.ForSelf && event.target.memberId == currentMemberId
                    PlanningItem(
                        event = event,
                        target = TargetChoice(event.target, if (isMe) null else targetName(event.target, family)),
                        authorName = authorDisplayName(event, family),
                        canManage = me != null && canManage(event, me, family),
                    )
                },
            )
        }
    }
}
