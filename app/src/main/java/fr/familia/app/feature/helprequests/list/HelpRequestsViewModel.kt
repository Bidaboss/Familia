package fr.familia.app.feature.helprequests.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.HelpRequestRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.HelpStatus
import fr.familia.app.feature.helprequests.isListed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** The « Coups de main » tab: the requests I asked for or was warned about (PRD, récit 15). */
@OptIn(ExperimentalCoroutinesApi::class)
class HelpRequestsViewModel(
    private val requests: HelpRequestRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val today: Flow<LocalDate> = currentDayFlow(),
) : ViewModel() {

    private val retries = MutableStateFlow(0)

    val uiState: StateFlow<HelpRequestsUiState> = retries
        .flatMapLatest { load() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HelpRequestsUiState.Loading)

    private fun load(): Flow<HelpRequestsUiState> {
        val requestRepo = requests ?: return flowOf(HelpRequestsUiState.Error)
        val memberRepo = members ?: return flowOf(HelpRequestsUiState.Error)
        return combine(requestRepo.observeMine(currentMemberId), memberRepo.observeFamily(), today) { list, family, day ->
            build(list, family, day)
        }.catch { emit(HelpRequestsUiState.Error) }
    }

    fun retry() {
        retries.value++
    }

    private fun build(list: List<HelpRequest>, family: Family, today: LocalDate): HelpRequestsUiState {
        val me = family.members.firstOrNull { it.id == currentMemberId } ?: return HelpRequestsUiState.Error
        val rows = list
            .filter { isListed(it, me, family, today) }
            .sortedWith(compareBy({ it.date }, { it.startTime != null }, { it.startTime }, { it.title.lowercase() }))
            .map { request ->
                HelpRequestRow(
                    request = request,
                    helpedName = request.forMemberId.takeIf { it != request.authorId }
                        ?.let { id -> family.members.firstOrNull { it.id == id }?.firstName },
                    isMine = request.authorId == me.id,
                    iAmVolunteer = request.volunteerId == me.id,
                    iDeclined = request.declinedBy == me.id,
                )
            }
        return HelpRequestsUiState.Success(
            open = rows.filter { it.request.status == HelpStatus.Open },
            taken = rows.filter { it.request.status == HelpStatus.Taken },
            today = today,
        )
    }
}
