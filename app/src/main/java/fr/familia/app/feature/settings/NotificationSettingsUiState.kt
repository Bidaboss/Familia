package fr.familia.app.feature.settings

sealed interface NotificationSettingsUiState {
    data object Loading : NotificationSettingsUiState

    /** [saveFailed]: the last change was refused (shown under the switch). */
    data class Ready(val dayBeforeReminder: Boolean, val saveFailed: Boolean = false, val goodNews: Boolean = true, val weekly: Boolean = true, val souvenirs: Boolean = true) : NotificationSettingsUiState

    data object Error : NotificationSettingsUiState
}
