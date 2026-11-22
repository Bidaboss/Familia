package fr.familia.app.feature.joinme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.JoinMeRepository
import fr.familia.app.core.model.JoinMe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

sealed interface JoinMeHomeUiState {
    data object Loading : JoinMeHomeUiState

    /** What I was sent and what I sent, still within their 2 h, newest first. */
    data class Ready(val received: List<JoinMe>, val sent: List<JoinMe>, val now: Instant) : JoinMeHomeUiState
}

/** The « Rejoignez-moi » card of the home screen (récit 33). */
class JoinMeHomeViewModel(
    private val shares: JoinMeRepository?,
    currentMemberId: String,
    private val clock: () -> Instant = Instant::now,
    /** A new tick each minute hides what expired while the screen stays open. */
    ticks: Flow<Instant> = flow {
        while (true) {
            emit(clock())
            delay(60_000)
        }
    },
) : ViewModel() {

    private val _stopFailed = MutableStateFlow(false)
    val stopFailed: StateFlow<Boolean> = _stopFailed.asStateFlow()

    val uiState: StateFlow<JoinMeHomeUiState> =
        if (shares == null) {
            flowOf(JoinMeHomeUiState.Ready(emptyList(), emptyList(), clock()))
        } else {
            combine(shares.observeReceived(currentMemberId), shares.observeSent(currentMemberId), ticks) { received, sent, now ->
                JoinMeHomeUiState.Ready(received.filter { it.isActive(now) }, sent.filter { it.isActive(now) }, now)
            }.catch { emit(JoinMeHomeUiState.Ready(emptyList(), emptyList(), clock())) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JoinMeHomeUiState.Loading)

    /** « Ne plus partager »: the recipients stop seeing it. */
    fun stop(shareId: String) {
        val repo = shares ?: return
        _stopFailed.value = false
        viewModelScope.launch {
            try {
                repo.delete(shareId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _stopFailed.value = true
            }
        }
    }
}
