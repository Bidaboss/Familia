package fr.familia.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.NotificationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Notification settings of the member signed in (PRD, récit 6 bis). Offline, a change lands in the
 * cache at once and reaches the server when the network is back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModel(
    private val notifications: NotificationRepository?,
    private val memberId: String,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)
    private val saveFailed = MutableStateFlow(false)

    val uiState: StateFlow<NotificationSettingsUiState> = attempt
        .flatMapLatest { n ->
            if (notifications == null) {
                flowOf(NotificationSettingsUiState.Ready(dayBeforeReminder = true)) // Previews, no Firebase.
            } else {
                combine<Boolean, Boolean, NotificationSettingsUiState>(
                    notifications.observeDayBeforeReminder(memberId),
                    saveFailed,
                ) { on, failed -> NotificationSettingsUiState.Ready(on, failed) }
                    .onStart { if (n > 0) emit(NotificationSettingsUiState.Loading) }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(NotificationSettingsUiState.Error)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationSettingsUiState.Loading)

    fun retry() {
        attempt.update { it + 1 }
    }

    fun setDayBeforeReminder(enabled: Boolean) {
        val repo = notifications ?: return
        saveFailed.value = false
        viewModelScope.launch {
            try {
                withTimeoutOrNull(writeTimeoutMillis) { repo.setDayBeforeReminder(memberId, enabled) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveFailed.value = true
            }
        }
    }
}
