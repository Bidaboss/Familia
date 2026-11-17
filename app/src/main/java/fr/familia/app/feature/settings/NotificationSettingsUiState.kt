package fr.familia.app.feature.settings

import fr.familia.app.core.model.NotificationPrefs

sealed interface NotificationSettingsUiState {
    data object Loading : NotificationSettingsUiState

    /** [saveFailed]: the last change was refused (shown under the switch). */
    data class Ready(val dayBeforeReminder: Boolean, val saveFailed: Boolean = false, val goodNews: Boolean = true, val weekly: Boolean = true, val souvenirs: Boolean = true, val tree: Boolean = true, val recap: Boolean = true, val birthdayWishes: Boolean = true, val prefs: NotificationPrefs = NotificationPrefs(), val packingReminder: Boolean = true) : NotificationSettingsUiState

    data object Error : NotificationSettingsUiState
}
