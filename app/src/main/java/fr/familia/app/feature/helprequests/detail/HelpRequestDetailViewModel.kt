package fr.familia.app.feature.helprequests.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.AlreadyTakenException
import fr.familia.app.core.data.HelpRequestRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.HelpStatus
import fr.familia.app.feature.helprequests.canDecline
import fr.familia.app.feature.helprequests.canManageHelp
import fr.familia.app.feature.helprequests.canThank
import fr.familia.app.feature.helprequests.canVolunteer
import fr.familia.app.feature.helprequests.canWithdraw
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** One request, with « Je m'en occupe », « Je ne peux pas », « Me retirer », « Merci » and « Annuler » (récits 16 and 17). */
class HelpRequestDetailViewModel(
    private val requests: HelpRequestRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val requestId: String,
    today: Flow<LocalDate> = currentDayFlow(),
    private val takeTimeoutMillis: Long = 10_000,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private data class Action(val busy: Boolean = false, val error: HelpActionError? = null)

    private val action = MutableStateFlow(Action())

    val uiState: StateFlow<HelpRequestDetailUiState> = run {
        val requestRepo = requests
        val memberRepo = members
        if (requestRepo == null || memberRepo == null) {
            flowOf<HelpRequestDetailUiState>(HelpRequestDetailUiState.Missing)
        } else {
            combine(requestRepo.observeRequest(requestId), memberRepo.observeFamily(), today, action) { request, family, day, act ->
                build(request, family, day, act)
            }.catch { emit(HelpRequestDetailUiState.Missing) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HelpRequestDetailUiState.Loading)

    private fun build(request: HelpRequest?, family: Family, today: LocalDate, act: Action): HelpRequestDetailUiState {
        val me = family.members.firstOrNull { it.id == currentMemberId }
        if (request == null || me == null || request.status == HelpStatus.Cancelled) return HelpRequestDetailUiState.Missing
        fun name(id: String) = family.members.firstOrNull { it.id == id }?.firstName
        return HelpRequestDetailUiState.Success(
            request = request,
            today = today,
            helpedName = request.forMemberId.takeIf { it != request.authorId }?.let(::name),
            isMine = request.authorId == me.id,
            myFirstName = me.firstName,
            warnedNames = request.notifyIds.mapNotNull(::name).sortedBy { it.lowercase() },
            declinedName = request.declinedBy?.let(::name),
            canVolunteer = canVolunteer(request, me) && request.date >= today,
            canDecline = canDecline(request, me) && request.date >= today,
            canWithdraw = canWithdraw(request, me),
            canManage = canManageHelp(request, me, family) && request.status != HelpStatus.Cancelled,
            canThank = canThank(request, me, family, today),
            isBusy = act.busy,
            error = act.error,
        )
    }

    /** « Je m'en occupe »: only online, in a transaction (spec, cas limites). */
    fun take() {
        val state = uiState.value as? HelpRequestDetailUiState.Success ?: return
        val repo = requests ?: return
        if (!state.canVolunteer || action.value.busy) return
        viewModelScope.launch {
            action.value = Action(busy = true)
            action.value = try {
                val done = withTimeoutOrNull(takeTimeoutMillis) { repo.take(requestId, currentMemberId, state.myFirstName) }
                if (done == null) Action(error = HelpActionError.NeedsNetwork) else Action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AlreadyTakenException) {
                Action(error = HelpActionError.AlreadyTaken(e.volunteerName))
            } catch (e: Exception) {
                // Offline, the transaction fails at once.
                Action(error = HelpActionError.NeedsNetwork)
            }
        }
    }

    fun withdraw() = write { requests?.withdraw(requestId) }

    fun decline() = write { requests?.decline(requestId, currentMemberId) }

    fun thank() = write { requests?.thank(requestId) }

    fun cancel() = write { requests?.cancel(requestId) }

    fun dismissError() {
        action.value = action.value.copy(error = null)
    }

    /** Offline, the write lands in the cache at once and Firestore sends it when the network is back. */
    private fun write(block: suspend () -> Unit) {
        if (action.value.busy) return
        viewModelScope.launch {
            action.value = Action(busy = true)
            action.value = try {
                withTimeoutOrNull(writeTimeoutMillis) { block() }
                Action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Action(error = HelpActionError.Failed)
            }
        }
    }
}
