package fr.familia.app.feature.a11y

import androidx.compose.runtime.Composable
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.navigation.AccountActions
import fr.familia.app.core.navigation.AccountSheet
import fr.familia.app.core.theme.FamiliaThemeId
import fr.familia.app.feature.events.detail.EventDetailScreen
import fr.familia.app.feature.events.detail.previewEventDetail
import fr.familia.app.feature.home.HomeActions
import fr.familia.app.feature.home.previewHome
import fr.familia.app.feature.members.admin.AdministrationScreen
import fr.familia.app.feature.members.admin.previewAdministrationState
import fr.familia.app.feature.planning.PlanningMode
import fr.familia.app.feature.planning.PlanningScreen
import fr.familia.app.feature.planning.previewPlanning
import fr.familia.app.feature.settings.NotificationSettingsScreen
import fr.familia.app.feature.settings.NotificationSettingsUiState
import fr.familia.app.feature.settings.appearance.AppearanceScreen
import fr.familia.app.feature.home.HomeR1bisActions
import fr.familia.app.feature.home.HomeR1bisScreen

/** The screens of the demo of the 21, in their preview states, for the contrast pass. Fictional family only. */
internal object DemoScreens {

    private val me = Member("a", "Camille", null, null, "h1", MemberProfile.Adult, false, emptyList(), null)

    private fun screen(name: String, content: @Composable () -> Unit) = name to content

    val all: List<Pair<String, @Composable () -> Unit>> = listOf(
        screen("l'Accueil") { HomeR1bisScreen(previewHome(), HomeActions(onOpenWishes = {}), HomeR1bisActions()) },
        screen("le Planning par mois") {
            PlanningScreen(previewPlanning(), {}, {}, {}, initialMode = PlanningMode.Month, onScopeChange = {})
        },
        screen("le Planning en liste") {
            PlanningScreen(previewPlanning(), {}, {}, {}, initialMode = PlanningMode.List, onShareBirthday = {})
        },
        screen("le Planning par semaine") { PlanningScreen(previewPlanning(), {}, {}, {}, r1 = true) },
        screen("un événement, pour qui le gère") {
            EventDetailScreen(previewEventDetail(), { _, _ -> }, {}, {}, {}, onAddToCalendar = {}, onShare = {}, onOpenExpenses = {})
        },
        screen("un événement, pour les autres") { EventDetailScreen(previewEventDetail(canManage = false), { _, _ -> }, {}, {}, {}) },
        screen("l'Administration") {
            AdministrationScreen(
                previewAdministrationState(),
                onBack = {},
                onRetry = {},
                onSetChef = { _, _ -> },
                onAddHousehold = {},
                onDeleteHousehold = {},
                onOpenExport = {},
                onOpenUsage = {},
                features = mapOf("gifts" to true),
                onSetFeature = { _, _ -> },
                onRenameHousehold = { _, _ -> },
            )
        },
        // A sheet, in a window of its own: the pass judges that window.
        screen("Mon compte") {
            AccountSheet(
                container = null,
                member = me,
                actions = AccountActions(
                    onOpenProfile = {},
                    onOpenAppearance = {},
                    onOpenNotifications = {},
                    onOpenHelp = {},
                    onOpenWhatsNew = {},
                    onOpenUpdates = {},
                    onOpenFeedback = {},
                    onShareApp = {},
                    onOpenChildrenUsage = {},
                    onOpenAdministration = {},
                    onOpenCalendarFeed = {},
                ),
                onDismiss = {},
            )
        },
        screen("les Notifications") {
            NotificationSettingsScreen(
                state = NotificationSettingsUiState.Ready(dayBeforeReminder = true),
                notificationsAllowed = false,
                onAllowNotifications = {},
                onDayBeforeReminderChange = {},
                onOpenSystemSettings = {},
                onRetry = {},
                onBack = {},
            )
        },
        screen("l'Apparence") { AppearanceScreen(FamiliaThemeId.Spain, {}, {}) },
    )
}
