package fr.familia.app.feature.trips.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.AlertTooSoonException
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.Member
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Where « Prévenir ma famille » is. */
enum class AlertState { Idle, Sending, Sent, TooSoon, Offline, Failed }

/**
 * « Prévenir ma famille » (récit 105, règle 43). The server tells everyone who follows the trip;
 * the screen only says whether it went. Offline, Firestore keeps the message and sends it once the
 * network is back: the screen says so instead of claiming it went.
 */
class TripSafetyViewModel(
    private val trips: TripRepository?,
    private val tripId: String,
    private val me: Member,
    private val timeoutMillis: Long = 10_000,
) : ViewModel() {
    private val _alert = MutableStateFlow(AlertState.Idle)
    val alert: StateFlow<AlertState> = _alert.asStateFlow()

    fun alertFamily() {
        val repo = trips ?: return
        if (_alert.value == AlertState.Sending) return
        _alert.value = AlertState.Sending
        viewModelScope.launch {
            _alert.value = try {
                withTimeout(timeoutMillis) { repo.alertFamily(tripId, me) }
                AlertState.Sent
            } catch (e: TimeoutCancellationException) {
                AlertState.Offline
            } catch (e: CancellationException) {
                throw e
            } catch (e: AlertTooSoonException) {
                AlertState.TooSoon
            } catch (e: Exception) {
                AlertState.Failed
            }
        }
    }

    /** The message of the result was read: the button is back. */
    fun dismiss() {
        if (_alert.value != AlertState.Sending) _alert.value = AlertState.Idle
    }
}
