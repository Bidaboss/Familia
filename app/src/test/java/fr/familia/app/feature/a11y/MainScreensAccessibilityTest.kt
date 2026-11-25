package fr.familia.app.feature.a11y

import androidx.compose.ui.test.junit4.createComposeRule
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.navigation.AccountActions
import fr.familia.app.core.navigation.AccountButton
import fr.familia.app.core.navigation.AccountSheet
import fr.familia.app.core.theme.ThemeChoice
import fr.familia.app.core.ui.checkAccessibility
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import fr.familia.app.feature.home.HomeR1bisActions
import fr.familia.app.feature.home.HomeR1bisScreen

/**
 * Police 200 % and TalkBack on the main screens (Accueil, Planning, an event, Administration,
 * Mon compte), with their preview states and the common check of [checkAccessibility]: at twice
 * the text size, everything one can touch is read by TalkBack and is at least 48 dp high.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2f)
class MainScreensAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val me = Member("a", "Camille", null, null, "h1", MemberProfile.Adult, false, emptyList(), null)

    @Test
    fun `the Accueil`() = compose.checkAccessibility { HomeR1bisScreen(previewHome(), HomeActions(onOpenWishes = {}), HomeR1bisActions()) }

    @Test
    fun `the Planning by month`() = compose.checkAccessibility {
        PlanningScreen(previewPlanning(), {}, null, {}, initialMode = PlanningMode.Month)
    }

    @Test
    fun `the Planning with Partager on a birthday`() = compose.checkAccessibility {
        PlanningScreen(previewPlanning(), {}, null, {}, onShareBirthday = {})
    }

    @Test
    fun `the Planning by week`() = compose.checkAccessibility { PlanningScreen(previewPlanning(), {}, null, {}) }

    @Test
    fun `an event, for who manages it`() = compose.checkAccessibility {
        EventDetailScreen(previewEventDetail(), { _, _ -> }, {}, {}, {}, onAddToCalendar = {}, onShare = {}, onOpenExpenses = {})
    }

    @Test
    fun `an event, for the others`() = compose.checkAccessibility {
        EventDetailScreen(previewEventDetail(canManage = false), { _, _ -> }, {}, {}, {})
    }

    @Test
    fun `the Administration`() = compose.checkAccessibility {
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
    }

    @Test
    fun `my photo, that opens Mon compte`() = compose.checkAccessibility { AccountButton(me) {} }

    @Test
    fun `Mon compte`() = compose.checkAccessibility {
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
    }

    @Test
    fun `the Notifications`() = compose.checkAccessibility {
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

    @Test
    fun `the Apparence`() = compose.checkAccessibility { AppearanceScreen(ThemeChoice.Season, {}, {}) }
}
