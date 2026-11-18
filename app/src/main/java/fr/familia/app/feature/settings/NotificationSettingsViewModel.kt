package fr.familia.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.NotificationRepository
import fr.familia.app.core.model.EveningKind
import fr.familia.app.core.model.NotificationFamily
import fr.familia.app.core.model.NotificationLevel
import fr.familia.app.core.model.NotificationPrefs
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
                combine<Pair<Boolean, NotificationPrefs>, Boolean, Boolean, List<Boolean>, Boolean, NotificationSettingsUiState>(
                    combine(notifications.observeDayBeforeReminder(memberId), notifications.observePrefs(memberId), ::Pair),
                    notifications.observeGoodNews(memberId),
                    notifications.observeWeekly(memberId),
                    combine(
                        notifications.observeSouvenirs(memberId),
                        notifications.observeTree(memberId),
                        notifications.observeRecap(memberId),
                        notifications.observeBirthdayWishes(memberId),
                        notifications.observePackingReminder(memberId),
                    ) { s, t, r, b, p -> listOf(s, t, r, b, p) },
                    saveFailed,
                ) { (on, prefs), goodNews, weekly, (souvenirs, tree, recap, wishes, packing), failed ->
                    NotificationSettingsUiState.Ready(on, failed, goodNews, weekly, souvenirs, tree, recap, wishes, prefs, packing)
                }
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

    fun setDayBeforeReminder(enabled: Boolean) = save { it.setDayBeforeReminder(memberId, enabled) }

    /** Bonnes nouvelles (récit 32). Never mutes the notice to the parents when a child publishes. */
    fun setGoodNews(enabled: Boolean) = save { it.setGoodNews(memberId, enabled) }

    /** Question de la semaine (récit 37): only the Monday notice, the question stays on the home screen. */
    fun setWeekly(enabled: Boolean) = save { it.setWeekly(memberId, enabled) }

    /** Souvenirs (récit 34): only the « Nouveau souvenir » notice; the souvenirs are kept. */
    fun setSouvenirs(enabled: Boolean) = save { it.setSouvenirs(memberId, enabled) }

    /** L'arbre (récit 35): only the « Défi réussi » notice; the tree keeps growing. */
    fun setTree(enabled: Boolean) = save { it.setTree(memberId, enabled) }

    /** « La semaine de la famille » (récit 36): the Sunday notice; the screen stays in the app. */
    fun setRecap(enabled: Boolean) = save { it.setRecap(memberId, enabled) }

    /** Vœux d'anniversaire (récit 36): the notices of the day; the cards stay on the member's page. */
    fun setBirthdayWishes(enabled: Boolean) = save { it.setBirthdayWishes(memberId, enabled) }

    /** « Ma valise » (récit 62): the reminder of the day before a trip; the bag stays. */
    fun setPackingReminder(enabled: Boolean) = save { it.setPackingReminder(memberId, enabled) }

    /** « Tout », « L'essentiel » or « Presque rien » (récit 6 quinquies): sets every family at once. */
    fun setLevel(level: NotificationLevel) = save { repo -> repo.setFamilies(memberId, NotificationFamily.entries.associateWith { it in level.on }) }

    /** One family, whatever the level: the switch always counts. */
    fun setFamily(family: NotificationFamily, enabled: Boolean) = save { it.setFamilies(memberId, mapOf(family to enabled)) }

    /** From 22 h to 8 h, notices wait until 8 h, grouped. */
    fun setQuietHours(enabled: Boolean) = save { it.setQuietHours(memberId, enabled) }

    /** Résumé du soir (récit 70): this kind in the 19 h résumé, or at once. */
    fun setEvening(kind: EveningKind, inSummary: Boolean) = save { it.setEvening(memberId, kind, inSummary) }

    private fun save(write: suspend (NotificationRepository) -> Unit) {
        val repo = notifications ?: return
        saveFailed.value = false
        viewModelScope.launch {
            try {
                withTimeoutOrNull(writeTimeoutMillis) { write(repo) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveFailed.value = true
            }
        }
    }
}
