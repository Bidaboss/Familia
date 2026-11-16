package fr.familia.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.notifications.openNotificationSettings
import fr.familia.app.core.notifications.rememberNotificationPermission

/** Connects the Notifications screen to its ViewModel and to the Android permission. */
@Composable
fun NotificationSettingsRoute(container: AppContainer?, currentMember: Member, onBack: () -> Unit) {
    val viewModel: NotificationSettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { NotificationSettingsViewModel(container?.notificationRepository, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberNotificationPermission()
    val context = LocalContext.current
    NotificationSettingsScreen(
        state = state,
        notificationsAllowed = permission.allowed,
        onAllowNotifications = permission.ask,
        onDayBeforeReminderChange = viewModel::setDayBeforeReminder,
        onOpenSystemSettings = { openNotificationSettings(context) },
        onRetry = viewModel::retry,
        onBack = onBack,
        onGoodNewsChange = viewModel::setGoodNews,
        onWeeklyChange = viewModel::setWeekly,
    )
}
