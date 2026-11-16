package fr.familia.app.feature.settings

import fr.familia.app.core.model.Feature
import fr.familia.app.core.ui.LocalFeatureFlags
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews

/**
 * Notifications, opened from the Famille tab. [notificationsAllowed] is false when Android blocks
 * them for the app (Android 13 and later ask first); the button then asks again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    state: NotificationSettingsUiState,
    notificationsAllowed: Boolean,
    onAllowNotifications: () -> Unit,
    onDayBeforeReminderChange: (Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onGoodNewsChange: (Boolean) -> Unit = {},
    onWeeklyChange: (Boolean) -> Unit = {},
    onSouvenirsChange: (Boolean) -> Unit = {},
    onTreeChange: (Boolean) -> Unit = {},
    onRecapChange: (Boolean) -> Unit = {},
    onBirthdayWishesChange: (Boolean) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                NotificationSettingsUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                NotificationSettingsUiState.Error -> EmptyState(
                    icon = Icons.Outlined.NotificationsOff,
                    title = stringResource(R.string.notifications_error_title),
                    body = stringResource(R.string.notifications_error_body),
                    action = {
                        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.action_retry))
                        }
                    },
                )
                is NotificationSettingsUiState.Ready -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!notificationsAllowed) BlockedCard(onAllowNotifications)
                    Text(
                        stringResource(R.string.notifications_events_help),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SettingSwitch(
                        title = stringResource(R.string.notifications_reminder),
                        help = stringResource(R.string.notifications_reminder_help),
                        checked = state.dayBeforeReminder,
                        onChange = onDayBeforeReminderChange,
                    )
                    // Shown once the Bonnes nouvelles module is switched on (or to the admin principal).
                    if (LocalFeatureFlags.current.isOn(Feature.GoodNews)) {
                        SettingSwitch(
                            title = stringResource(R.string.notifications_good_news),
                            help = stringResource(R.string.notifications_good_news_help),
                            checked = state.goodNews,
                            onChange = onGoodNewsChange,
                        )
                    }
                    if (LocalFeatureFlags.current.isOn(Feature.WeeklyQuestion)) {
                        SettingSwitch(
                            title = stringResource(R.string.notifications_weekly),
                            help = stringResource(R.string.notifications_weekly_help),
                            checked = state.weekly,
                            onChange = onWeeklyChange,
                        )
                    }
                    if (LocalFeatureFlags.current.isOn(Feature.Souvenirs)) {
                        SettingSwitch(
                            title = stringResource(R.string.notifications_souvenirs),
                            help = stringResource(R.string.notifications_souvenirs_help),
                            checked = state.souvenirs,
                            onChange = onSouvenirsChange,
                        )
                    }
                    if (LocalFeatureFlags.current.isOn(Feature.Tree)) {
                        SettingSwitch(
                            title = stringResource(R.string.notifications_tree),
                            help = stringResource(R.string.notifications_tree_help),
                            checked = state.tree,
                            onChange = onTreeChange,
                        )
                    }
                    if (LocalFeatureFlags.current.isOn(Feature.Recap)) {
                        SettingSwitch(
                            title = stringResource(R.string.notifications_recap),
                            help = stringResource(R.string.notifications_recap_help),
                            checked = state.recap,
                            onChange = onRecapChange,
                        )
                    }
                    if (LocalFeatureFlags.current.isOn(Feature.BirthdayWishes)) {
                        SettingSwitch(
                            title = stringResource(R.string.notifications_birthday_wishes),
                            help = stringResource(R.string.notifications_birthday_wishes_help),
                            checked = state.birthdayWishes,
                            onChange = onBirthdayWishesChange,
                        )
                    }
                    if (state.saveFailed) {
                        Text(
                            stringResource(R.string.notifications_save_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    OutlinedButton(
                        onClick = onOpenSystemSettings,
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text(stringResource(R.string.notifications_system_settings))
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedCard(onAllow: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.notifications_blocked),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.notifications_allow))
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                help,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@FamiliaPreviews
@Composable
private fun NotificationSettingsPreview() {
    FamiliaTheme {
        Surface {
            NotificationSettingsScreen(
                state = NotificationSettingsUiState.Ready(dayBeforeReminder = true),
                notificationsAllowed = false,
                onAllowNotifications = {},
                onDayBeforeReminderChange = {},
                onOpenSystemSettings = {},
                onRetry = {},
                onBack = {},
            )
        }
    }
}
