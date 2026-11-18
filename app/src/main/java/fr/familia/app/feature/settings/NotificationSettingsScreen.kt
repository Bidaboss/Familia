package fr.familia.app.feature.settings

import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.EveningKind
import fr.familia.app.core.model.FeatureFlags
import fr.familia.app.core.model.NotificationFamily
import fr.familia.app.core.model.NotificationLevel
import fr.familia.app.core.model.NotificationPrefs
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
    onSouvenirsChange: (Boolean) -> Unit = {},
    onTreeChange: (Boolean) -> Unit = {},
    onRecapChange: (Boolean) -> Unit = {},
    onBirthdayWishesChange: (Boolean) -> Unit = {},
    onPackingReminderChange: (Boolean) -> Unit = {},
    onLevelChange: (NotificationLevel) -> Unit = {},
    onFamilyChange: (NotificationFamily, Boolean) -> Unit = { _, _ -> },
    onQuietHoursChange: (Boolean) -> Unit = {},
    /** Résumé du soir (récit 70): a kind in the 19 h résumé (true) or at once (false). */
    onEveningChange: (EveningKind, Boolean) -> Unit = { _, _ -> },
    /** « Rappels quand je viens » (récit 56), shown only with its switch on. */
    defaultReminders: (@Composable () -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                    val flags = LocalFeatureFlags.current
                    // Récit 6 quinquies: a level sets every family at once; the switches stay free.
                    SectionTitle(stringResource(R.string.notifications_level_title))
                    Column(Modifier.selectableGroup()) {
                        NotificationLevel.entries.forEach { level ->
                            LevelRow(level, selected = state.prefs.level == level, onSelect = { onLevelChange(level) })
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SectionTitle(stringResource(R.string.notifications_families_title))
                    NotificationFamily.entries.filter { familyShown(it, flags) }.forEach { family ->
                        SettingSwitch(
                            title = stringResource(familyTitle(family)),
                            help = stringResource(familyHelp(family)),
                            checked = state.prefs.isOn(family),
                            onChange = { onFamilyChange(family, it) },
                        )
                    }
                    SettingSwitch(
                        title = stringResource(R.string.notifications_quiet_hours),
                        help = stringResource(R.string.notifications_quiet_hours_help),
                        checked = state.prefs.quietHours,
                        onChange = onQuietHoursChange,
                    )
                    // Résumé du soir (récit 70): shown only with its module on, as the server reads it.
                    val eveningKinds = eveningKindsShown(state.prefs, flags)
                    if (eveningKinds.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        SectionTitle(stringResource(R.string.notifications_evening_title))
                        Text(
                            stringResource(R.string.notifications_evening_help),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        eveningKinds.forEach { kind ->
                            val inSummary = kind in state.prefs.evening
                            SettingSwitch(
                                title = stringResource(eveningTitle(kind)),
                                help = stringResource(
                                    if (inSummary) R.string.notifications_evening_in_summary else R.string.notifications_evening_immediate,
                                ),
                                checked = inSummary,
                                onChange = { onEveningChange(kind, it) },
                            )
                        }
                    }
                    if (defaultReminders != null) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        defaultReminders()
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SectionTitle(stringResource(R.string.notifications_details_title))
                    // The finer switches inside a family, as before.
                    if (state.prefs.isOn(NotificationFamily.Reminders)) {
                        SettingSwitch(
                            title = stringResource(R.string.notifications_reminder),
                            help = stringResource(R.string.notifications_reminder_help),
                            checked = state.dayBeforeReminder,
                            onChange = onDayBeforeReminderChange,
                        )
                    }
                    if (state.prefs.isOn(NotificationFamily.Weekly)) {
                        if (flags.isOn(Feature.Souvenirs)) {
                            SettingSwitch(
                                title = stringResource(R.string.notifications_souvenirs),
                                help = stringResource(R.string.notifications_souvenirs_help),
                                checked = state.souvenirs,
                                onChange = onSouvenirsChange,
                            )
                        }
                        if (flags.isOn(Feature.Tree)) {
                            SettingSwitch(
                                title = stringResource(R.string.notifications_tree),
                                help = stringResource(R.string.notifications_tree_help),
                                checked = state.tree,
                                onChange = onTreeChange,
                            )
                        }
                        if (flags.isOn(Feature.Recap)) {
                            SettingSwitch(
                                title = stringResource(R.string.notifications_recap),
                                help = stringResource(R.string.notifications_recap_help),
                                checked = state.recap,
                                onChange = onRecapChange,
                            )
                        }
                        if (flags.isOn(Feature.BirthdayWishes)) {
                            SettingSwitch(
                                title = stringResource(R.string.notifications_birthday_wishes),
                                help = stringResource(R.string.notifications_birthday_wishes_help),
                                checked = state.birthdayWishes,
                                onChange = onBirthdayWishesChange,
                            )
                        }
                    }
                    // « Ma valise » (récit 62): a trips notice, so it follows the switch of the trips.
                    if (flags.isOn(Feature.PackingList) && state.prefs.isOn(NotificationFamily.Trips)) {
                        SettingSwitch(
                            title = stringResource(R.string.notifications_packing),
                            help = stringResource(R.string.notifications_packing_help),
                            checked = state.packingReminder,
                            onChange = onPackingReminderChange,
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

/**
 * The kinds of the résumé du soir to choose from: none while its module is off for the family (read
 * with isOnForFamily, as the server does), and only those whose family the member keeps on.
 */
internal fun eveningKindsShown(prefs: NotificationPrefs, flags: FeatureFlags): List<EveningKind> =
    if (!flags.isOnForFamily(Feature.EveningDigest)) {
        emptyList()
    } else {
        EveningKind.entries.filter { prefs.isOn(it.family) && familyShown(it.family, flags) }
    }

private fun eveningTitle(kind: EveningKind): Int = when (kind) {
    EveningKind.Reactions -> R.string.notifications_evening_reactions
    EveningKind.Comments -> R.string.notifications_evening_comments
    EveningKind.Weekly -> R.string.notifications_evening_weekly
    EveningKind.Souvenirs -> R.string.notifications_evening_souvenirs
}

/** A family shows once its module is on; the planning and the reminders always. */
internal fun familyShown(family: NotificationFamily, flags: FeatureFlags): Boolean = when (family) {
    NotificationFamily.Planning, NotificationFamily.Reminders -> true
    NotificationFamily.Gifts -> flags.isOn(Feature.Gifts)
    NotificationFamily.GoodNews -> flags.isOn(Feature.GoodNews)
    NotificationFamily.Comments -> flags.isOn(Feature.Comments) || flags.isOn(Feature.GoodNews)
    NotificationFamily.HelpRequests -> flags.isOn(Feature.HelpRequests)
    NotificationFamily.Trips -> flags.isOn(Feature.Trips)
    NotificationFamily.Weekly -> listOf(Feature.WeeklyQuestion, Feature.Souvenirs, Feature.Tree, Feature.Recap, Feature.BirthdayWishes).any(flags::isOn)
}

private fun familyTitle(family: NotificationFamily): Int = when (family) {
    NotificationFamily.Planning -> R.string.notifications_family_planning
    NotificationFamily.Reminders -> R.string.notifications_family_reminders
    NotificationFamily.Gifts -> R.string.notifications_family_gifts
    NotificationFamily.GoodNews -> R.string.notifications_good_news
    NotificationFamily.Comments -> R.string.notifications_family_comments
    NotificationFamily.HelpRequests -> R.string.notifications_family_help
    NotificationFamily.Trips -> R.string.notifications_family_trips
    NotificationFamily.Weekly -> R.string.notifications_family_weekly
}

private fun familyHelp(family: NotificationFamily): Int = when (family) {
    NotificationFamily.Planning -> R.string.notifications_family_planning_help
    NotificationFamily.Reminders -> R.string.notifications_family_reminders_help
    NotificationFamily.Gifts -> R.string.notifications_family_gifts_help
    NotificationFamily.GoodNews -> R.string.notifications_good_news_help
    NotificationFamily.Comments -> R.string.notifications_family_comments_help
    NotificationFamily.HelpRequests -> R.string.notifications_family_help_help
    NotificationFamily.Trips -> R.string.notifications_family_trips_help
    NotificationFamily.Weekly -> R.string.notifications_family_weekly_help
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
    )
}

@Composable
private fun LevelRow(level: NotificationLevel, selected: Boolean, onSelect: () -> Unit) {
    val (title, help) = when (level) {
        NotificationLevel.All -> R.string.notifications_level_all to R.string.notifications_level_all_help
        NotificationLevel.Essential -> R.string.notifications_level_essential to R.string.notifications_level_essential_help
        NotificationLevel.Minimal -> R.string.notifications_level_minimal to R.string.notifications_level_minimal_help
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
