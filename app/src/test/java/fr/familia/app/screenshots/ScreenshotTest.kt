package fr.familia.app.screenshots

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.navigation.FamiliaApp
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.auth.SessionUiState
import fr.familia.app.feature.auth.SignInScreen
import fr.familia.app.feature.birthdays.BirthdaysScreen
import fr.familia.app.feature.events.detail.EventDetailScreen
import fr.familia.app.feature.events.detail.previewEventDetail
import fr.familia.app.feature.birthdays.previewBirthdays
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.feature.members.MembersScreen
import fr.familia.app.feature.settings.NotificationSettingsScreen
import fr.familia.app.feature.export.ExportScreen
import fr.familia.app.feature.update.AppUpdate
import fr.familia.app.feature.help.HelpScreen
import fr.familia.app.feature.help.HelpSheetContent
import fr.familia.app.feature.help.HelpTopic
import fr.familia.app.feature.help.HelpUiState
import fr.familia.app.feature.help.ScreenHelp
import fr.familia.app.feature.help.helpTopics
import fr.familia.app.feature.onboarding.OnboardingScreen
import fr.familia.app.feature.onboarding.onboardingPages
import fr.familia.app.feature.update.UpdateBanner
import fr.familia.app.feature.update.UpdateCheckScreen
import fr.familia.app.feature.update.UpdateCheckUiState
import fr.familia.app.feature.export.ExportUiState
import fr.familia.app.feature.settings.NotificationSettingsUiState
import fr.familia.app.feature.members.MembersUiState
import fr.familia.app.feature.members.previewGroups
import fr.familia.app.feature.events.edit.EventEditScreen
import fr.familia.app.feature.events.edit.EventFormError
import fr.familia.app.feature.events.edit.previewEventState
import fr.familia.app.feature.planning.PlanningMode
import fr.familia.app.feature.planning.PlanningScreen
import fr.familia.app.feature.planning.previewPlanning
import fr.familia.app.feature.members.edit.FormError
import fr.familia.app.feature.members.edit.MemberEditScreen
import fr.familia.app.feature.members.edit.previewEditState
import fr.familia.app.feature.members.admin.AdministrationScreen
import fr.familia.app.feature.members.admin.previewAdministrationState
import fr.familia.app.feature.members.edit.previewExistingState
import fr.familia.app.feature.members.edit.MemberEditUiState
import java.time.LocalDate
import org.junit.Test
import fr.familia.app.core.ui.ClayCardSamples
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the screens on the JVM and saves PNG files, so each pull request carries light and dark
 * screenshots without a phone. CI runs `recordRoborazziDebug` and uploads build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class ScreenshotTest {

    @Test
    fun planningLight() = capture("planning_clair") {
        FamiliaApp(container = null, currentMember = sampleMember)
    }

    @Test
    @Config(qualifiers = "+night")
    fun planningDark() = capture("planning_sombre", darkTheme = true) {
        FamiliaApp(container = null, currentMember = sampleMember)
    }

    @Test
    @Config(fontScale = 2f)
    fun planningFontScale200() = capture("planning_police_200") {
        FamiliaApp(container = null, currentMember = sampleMember)
    }

    @Test
    fun clayCardsLight() = capture("argile_cartes_clair") { ClayCardSamples() }

    @Test
    @Config(qualifiers = "+night")
    fun clayCardsDark() = capture("argile_cartes_sombre", darkTheme = true) { ClayCardSamples() }

    @Test
    @Config(fontScale = 2f)
    fun clayCardsFontScale200() = capture("argile_cartes_police_200") { ClayCardSamples() }

    @Test
    fun birthdaysLight() = capture("anniversaires_clair") { Surface { BirthdaysScreen(previewBirthdays(), onRetry = {}) } }

    @Test
    @Config(qualifiers = "+night")
    fun birthdaysDark() = capture("anniversaires_sombre", darkTheme = true) {
        Surface { BirthdaysScreen(previewBirthdays(), onRetry = {}) }
    }

    @Test
    @Config(fontScale = 2f)
    fun birthdaysFontScale200() = capture("anniversaires_police_200") {
        Surface { BirthdaysScreen(previewBirthdays(), onRetry = {}) }
    }

    @Test
    fun membersLight() = capture("famille_clair") {
        Surface { MembersScreen(MembersUiState.Success(previewGroups(), "a"), onRetry = {}, versionName = "0.1.0") }
    }

    @Test
    @Config(qualifiers = "+night")
    fun membersDark() = capture("famille_sombre", darkTheme = true) {
        Surface { MembersScreen(MembersUiState.Success(previewGroups(), "a"), onRetry = {}, versionName = "0.1.0") }
    }

    @Test
    fun signInLight() = capture("connexion_clair") { Surface { SignInScreen(SessionUiState.SignedOut(), {}, {}) } }

    @Test
    @Config(qualifiers = "+night")
    fun signInDark() = capture("connexion_sombre", darkTheme = true) {
        Surface { SignInScreen(SessionUiState.SignedOut(), {}, {}) }
    }

    @Test
    @Config(fontScale = 2f)
    fun signInNotAMemberFontScale200() = capture("connexion_refusee_police_200") {
        Surface { SignInScreen(SessionUiState.NotAMember("prenom.nom@gmail.com"), {}, {}) }
    }

    @Test
    @Config(fontScale = 2f)
    fun membersFontScale200() = capture("famille_police_200") {
        Surface { MembersScreen(MembersUiState.Success(previewGroups(), "a"), onRetry = {}, versionName = "0.1.0") }
    }

    @Test
    fun membersErrorLight() = capture("famille_erreur_clair") {
        Surface { MembersScreen(MembersUiState.Error, onRetry = {}, versionName = "0.1.0") }
    }

    @Test
    @Config(fontScale = 2f)
    fun offlineFontScale200() = capture("hors_ligne_police_200") {
        FamiliaApp(container = null, currentMember = sampleMember, isOffline = true)
    }

    @Test
    fun memberFormLight() = capture("fiche_membre_clair") {
        MemberEditScreen(previewEditState(), {}, {}, {}, {}, today = LocalDate.of(2026, 11, 14))
    }

    @Test
    @Config(qualifiers = "+night")
    fun memberFormErrorsDark() = capture("fiche_membre_erreurs_sombre", darkTheme = true) {
        MemberEditScreen(
            previewEditState(errors = setOf(FormError.EmailTaken, FormError.ParentsMissing)),
            {}, {}, {}, {},
            today = LocalDate.of(2026, 11, 14),
        )
    }

    @Test
    @Config(fontScale = 2f)
    fun memberFormFontScale200() = capture("fiche_membre_police_200") {
        MemberEditScreen(previewEditState(), {}, {}, {}, {}, today = LocalDate.of(2026, 11, 14))
    }

    @Test
    @Config(fontScale = 2f)
    fun memberFormExistingFontScale200() = capture("fiche_membre_existant_police_200") {
        MemberEditScreen(previewExistingState(), {}, {}, {}, {}, today = LocalDate.of(2026, 11, 14))
    }

    @Test
    fun memberAddedLight() = capture("membre_ajoute_clair") {
        MemberEditScreen(MemberEditUiState.Added("Lou", "lou@example.com"), {}, {}, {}, {})
    }

    @Test
    fun planningListLight() = capture("planning_liste_clair") {
        Surface { PlanningScreen(previewPlanning(), {}, {}, {}) }
    }

    @Test
    @Config(qualifiers = "+night")
    fun planningListDark() = capture("planning_liste_sombre", darkTheme = true) {
        Surface { PlanningScreen(previewPlanning(), {}, {}, {}) }
    }

    @Test
    @Config(fontScale = 2f)
    fun planningListFontScale200() = capture("planning_liste_police_200") {
        Surface { PlanningScreen(previewPlanning(), {}, {}, {}) }
    }

    @Test
    fun eventDetailLight() = capture("evenement_detail_clair") {
        EventDetailScreen(previewEventDetail(), { _, _ -> }, {}, {}, {})
    }

    @Test
    @Config(qualifiers = "+night")
    fun eventDetailDark() = capture("evenement_detail_sombre", darkTheme = true) {
        EventDetailScreen(previewEventDetail(canManage = false), { _, _ -> }, {}, {}, {})
    }

    @Test
    @Config(fontScale = 2f)
    fun eventDetailFontScale200() = capture("evenement_detail_police_200") {
        EventDetailScreen(previewEventDetail(), { _, _ -> }, {}, {}, {})
    }

    @Test
    fun planningMonthLight() = capture("planning_mois_clair") {
        Surface { PlanningScreen(previewPlanning(), {}, {}, {}, initialMode = PlanningMode.Month) }
    }

    @Test
    @Config(qualifiers = "+night")
    fun planningMonthDark() = capture("planning_mois_sombre", darkTheme = true) {
        Surface { PlanningScreen(previewPlanning(), {}, {}, {}, initialMode = PlanningMode.Month) }
    }

    @Test
    @Config(fontScale = 2f)
    fun planningMonthFontScale200() = capture("planning_mois_police_200") {
        Surface { PlanningScreen(previewPlanning(), {}, {}, {}, initialMode = PlanningMode.Month) }
    }

    @Test
    fun eventFormLight() = capture("evenement_clair") {
        EventEditScreen(previewEventState(isNew = false), {}, {}, {}, {})
    }

    @Test
    @Config(fontScale = 2f)
    fun eventFormErrorsFontScale200() = capture("evenement_erreurs_police_200") {
        EventEditScreen(
            previewEventState(errors = setOf(EventFormError.TitleMissing, EventFormError.DateMissing)).let {
                it.copy(form = it.form.copy(title = "", date = null))
            },
            {}, {}, {}, {},
        )
    }

    @Test
    fun membersAdminLight() = capture("famille_admin_clair") {
        Surface {
            MembersScreen(
                MembersUiState.Success(
                    previewGroups(),
                    "a",
                    ownerId = "a",
                    isOwner = true,
                    canAddMembers = true,
                    editableMemberIds = setOf("a", "b", "c", "d", "e", "f"),
                    renamableHouseholdIds = setOf("h1", "h2"),
                ),
                onRetry = {},
                versionName = "0.1.0",
                onEditMember = {},
                onOpenNotifications = {},
                onOpenAdministration = {},
            )
        }
    }

    @Test
    fun membersChefLight() = capture("famille_chef_clair") {
        Surface {
            MembersScreen(
                MembersUiState.Success(
                    previewGroups(),
                    "b",
                    ownerId = "a",
                    canAddMembers = true,
                    editableMemberIds = setOf("b", "c"),
                    renamableHouseholdIds = setOf("h1"),
                ),
                onRetry = {},
                versionName = "0.1.0",
                onEditMember = {},
                onOpenNotifications = {},
                onOpenAdministration = {},
            )
        }
    }

    @Test
    fun administrationLight() = capture("administration_clair") {
        Surface { AdministrationScreen(previewAdministrationState(), {}, {}, { _, _ -> }, {}, {}, {}) }
    }

    @Test
    @Config(fontScale = 2f)
    fun administrationFontScale200() = capture("administration_police_200") {
        Surface { AdministrationScreen(previewAdministrationState(), {}, {}, { _, _ -> }, {}, {}, {}) }
    }

    @Test
    fun updateBannerLight() = capture("mise_a_jour_clair") { UpdateBanner(AppUpdate("0.2.0", "https://example.com"), {}, {}) }

    @Test
    @Config(fontScale = 2f)
    fun updateBannerFontScale200() = capture("mise_a_jour_police_200") {
        UpdateBanner(AppUpdate("0.2.0", "https://example.com"), {}, {})
    }

    @Test
    fun updateCheckLight() = capture("verifier_mises_a_jour_clair") { updateCheck(UpdateCheckUiState.UpToDate) }

    @Test
    @Config(qualifiers = "+night")
    fun updateCheckDark() = capture("verifier_mises_a_jour_sombre", darkTheme = true) {
        updateCheck(UpdateCheckUiState.Available(AppUpdate("0.2.0", "https://example.com")))
    }

    @Test
    @Config(fontScale = 2f)
    fun updateCheckFontScale200() = capture("verifier_mises_a_jour_police_200") { updateCheck(UpdateCheckUiState.Offline) }

    @Composable
    private fun updateCheck(state: UpdateCheckUiState) {
        Surface { UpdateCheckScreen(state, installedVersion = "0.1.0", onCheck = {}, onUpdate = {}, onBack = {}) }
    }

    @Test
    fun onboardingLight() = capture("tuto_clair") { onboarding(answersForChildren = true, index = 0) }

    @Test
    @Config(qualifiers = "+night")
    fun onboardingChildDark() = capture("tuto_enfant_sombre", darkTheme = true) { onboarding(answersForChildren = false, index = 3) }

    @Test
    @Config(fontScale = 2f)
    fun onboardingNotificationsFontScale200() = capture("tuto_notifications_police_200") {
        onboarding(answersForChildren = true, index = 4)
    }

    @Composable
    private fun onboarding(answersForChildren: Boolean, index: Int) {
        OnboardingScreen(onboardingPages(answersForChildren), index, notificationsAllowed = false, {}, {}, {}, {})
    }

    @Test
    fun helpLight() = capture("aide_clair") {
        Surface { HelpScreen(HelpUiState(helpTopics(isChef = true, isOwner = true), "Camille ou Inès"), {}, {}, initiallyOpen = HelpTopic.AddEvent) }
    }

    @Test
    @Config(qualifiers = "+night")
    fun helpSheetDark() = capture("aide_planning_sombre", darkTheme = true) {
        Surface { HelpSheetContent(ScreenHelp.Planning, onOpenAllHelp = {}) }
    }

    @Test
    @Config(fontScale = 2f)
    fun helpFontScale200() = capture("aide_police_200") {
        Surface { HelpScreen(HelpUiState(helpTopics(isChef = false), null), {}, {}, initiallyOpen = HelpTopic.Notifications) }
    }

    @Test
    fun exportLight() = capture("sauvegarde_clair") { Surface { ExportScreen(ExportUiState.Done, onExport = {}, onBack = {}) } }

    @Test
    @Config(qualifiers = "+night")
    fun exportDark() = capture("sauvegarde_sombre", darkTheme = true) {
        Surface { ExportScreen(ExportUiState.Failed, onExport = {}, onBack = {}) }
    }

    @Test
    @Config(fontScale = 2f)
    fun exportFontScale200() = capture("sauvegarde_police_200") {
        Surface { ExportScreen(ExportUiState.Exporting, onExport = {}, onBack = {}) }
    }

    @Test
    fun notificationsLight() = capture("notifications_clair") { notifications(allowed = false) }

    @Test
    @Config(qualifiers = "+night")
    fun notificationsDark() = capture("notifications_sombre", darkTheme = true) { notifications(allowed = true) }

    @Test
    @Config(fontScale = 2f)
    fun notificationsFontScale200() = capture("notifications_police_200") { notifications(allowed = false) }

    @Composable
    private fun notifications(allowed: Boolean) = Surface {
        NotificationSettingsScreen(
            state = NotificationSettingsUiState.Ready(dayBeforeReminder = true),
            notificationsAllowed = allowed,
            onAllowNotifications = {},
            onDayBeforeReminderChange = {},
            onOpenSystemSettings = {},
            onRetry = {},
            onBack = {},
        )
    }

    private val sampleMember = Member("a", "Camille", null, null, "h1", MemberProfile.Adult, true, emptyList(), null)

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = darkTheme) { content() }
        }
    }
}
