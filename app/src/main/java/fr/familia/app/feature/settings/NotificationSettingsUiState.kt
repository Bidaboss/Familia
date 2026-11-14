package fr.familia.app.feature.settings

sealed interface NotificationSettingsUiState {
    data object Loading : NotificationSettingsUiState

    /** [saveFailed]: the last change was refused (shown under the switch). */
    data class Ready(val dayBeforeReminder: Boolean, val saveFailed: Boolean = false) : NotificationSettingsUiState

    data object Error : NotificationSettingsUiState
}
