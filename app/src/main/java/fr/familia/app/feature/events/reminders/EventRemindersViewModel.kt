package fr.familia.app.feature.events.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.EventReminderRepository
import fr.familia.app.core.model.EventReminders
import fr.familia.app.core.model.ReminderOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime

data class EventRemindersUiState(
    val reminders: EventReminders = EventReminders(),
    /** The five choices, less those that cannot ring for this event (an all-day event…). */
    val choices: List<ReminderOffset> = ReminderOffset.entries,
    /** I said « Je viens »: the default reminders count. */
    val coming: Boolean = false,
    /** An occurrence of a series: « Cette fois seulement » or « Toutes les fois ». */
    val inSeries: Boolean = false,
    val isLoading: Boolean = true,
    val failed: Boolean = false,
) {
    val effective: Set<ReminderOffset> get() = reminders.effective(coming)
    val byDefault: Boolean get() = reminders.byDefault(coming)

    /** What rings comes from the series: « Toutes les fois » is chosen first in the dialog. */
    val fromSeries: Boolean get() = inSeries && reminders.own == null && reminders.series != null
}

/**
 * « Me le rappeler » on an event (PRD rév. 248, récit 56): private, for the member signed in only.
 * Offline, a change lands in the cache at once and reaches the server when the network is back.
 */
class EventRemindersViewModel(
    private val repository: EventReminderRepository?,
    private val memberId: String,
    private val eventId: String,
    private val seriesId: String?,
    time: LocalTime?,
    coming: Boolean,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val failed = MutableStateFlow(false)
    private val base = EventRemindersUiState(choices = ReminderOffset.choicesFor(time), coming = coming, inSeries = seriesId != null)

    val uiState: StateFlow<EventRemindersUiState> = run {
        val repo = repository
        if (repo == null) {
            flowOf(base.copy(isLoading = false))
        } else {
            combine(repo.observe(memberId, eventId, seriesId), failed) { reminders, fail ->
                base.copy(reminders = reminders, isLoading = false, failed = fail)
            }.catch { e ->
                if (e is CancellationException) throw e
                emit(base.copy(isLoading = false, failed = true))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), base)

    /** [everyTime]: on every occurrence of the series, this one included. */
    fun save(offsets: Set<ReminderOffset>, everyTime: Boolean) {
        val repo = repository ?: return
        failed.value = false
        viewModelScope.launch {
            try {
                withTimeoutOrNull(writeTimeoutMillis) {
                    if (everyTime && seriesId != null) repo.setForSeries(memberId, seriesId, eventId, offsets) else repo.setForEvent(memberId, eventId, offsets)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed.value = true
            }
        }
    }

    fun dismissError() {
        failed.value = false
    }
}

/** « Rappels quand je viens », in Notifications. */
class DefaultRemindersViewModel(
    private val repository: EventReminderRepository?,
    private val memberId: String,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val failed = MutableStateFlow(false)

    val defaults: StateFlow<Set<ReminderOffset>> = (repository?.observeDefaults(memberId) ?: flowOf(emptySet()))
        .catch { e -> if (e is CancellationException) throw e else emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val saveFailed: StateFlow<Boolean> = failed

    fun toggle(offset: ReminderOffset, on: Boolean) {
        val repo = repository ?: return
        val next = if (on) defaults.value + offset else defaults.value - offset
        failed.value = false
        viewModelScope.launch {
            try {
                withTimeoutOrNull(writeTimeoutMillis) { repo.setDefaults(memberId, next) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed.value = true
            }
        }
    }
}
