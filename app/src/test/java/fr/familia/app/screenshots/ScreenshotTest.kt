package fr.familia.app.screenshots

import fr.familia.app.core.model.GenealogyTree
import fr.familia.app.core.model.Kin
import fr.familia.app.core.model.KinLink
import fr.familia.app.feature.genealogy.GenealogyScreen
import fr.familia.app.feature.genealogy.GenealogyUiState
import fr.familia.app.feature.genealogy.PersonCard
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.navigation.FamiliaApp
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.FamiliaThemeId
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.feature.settings.appearance.AppIcon
import fr.familia.app.feature.usage.UsageReportScreen
import fr.familia.app.feature.usage.previewUsageState
import fr.familia.app.feature.settings.appearance.AppearanceScreen
import fr.familia.app.feature.settings.appearance.IconSection
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
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
import fr.familia.app.feature.home.HomeActions
import fr.familia.app.feature.home.previewHome
import fr.familia.app.feature.planning.previewPlanning
import fr.familia.app.feature.members.edit.FormError
import fr.familia.app.feature.members.edit.MemberEditScreen
import fr.familia.app.feature.members.edit.previewEditState
import fr.familia.app.feature.members.admin.AdministrationScreen
import fr.familia.app.feature.members.admin.previewAdministrationState
import fr.familia.app.feature.members.admin.CapsuleLeftoversSection
import fr.familia.app.feature.members.admin.ConsumptionCard
import fr.familia.app.feature.members.admin.ConsumptionSection
import fr.familia.app.feature.members.admin.previewConsumption
import fr.familia.app.core.model.CapsuleLeftover
import fr.familia.app.core.model.Consumption
import fr.familia.app.feature.members.edit.previewExistingState
import fr.familia.app.feature.members.edit.MemberEditUiState
import java.time.LocalDate
import org.junit.Test
import fr.familia.app.feature.helprequests.detail.HelpRequestDetailScreen
import fr.familia.app.feature.helprequests.detail.previewHelpRequestDetail
import fr.familia.app.feature.helprequests.edit.HelpFormError
import fr.familia.app.feature.helprequests.edit.HelpRequestEditScreen
import fr.familia.app.feature.helprequests.edit.previewHelpRequestEdit
import fr.familia.app.feature.helprequests.list.HelpRequestsScreen
import fr.familia.app.feature.helprequests.list.previewHelpRequests
import fr.familia.app.core.ui.ClayCardSamples
import fr.familia.app.feature.gifts.GiftsScreen
import fr.familia.app.feature.gifts.previewGifts
import fr.familia.app.feature.gifts.detail.GiftListActions
import fr.familia.app.feature.gifts.detail.GiftListScreen
import fr.familia.app.feature.gifts.detail.ItemSheetContent
import fr.familia.app.feature.gifts.detail.previewGiftList
import fr.familia.app.feature.gifts.edit.GiftListEditScreen
import fr.familia.app.feature.gifts.edit.previewGiftListForm
import fr.familia.app.feature.gifts.item.GiftItemEditScreen
import fr.familia.app.feature.gifts.item.previewGiftItemForm
import fr.familia.app.feature.gifts.item.previewSharedWishForm
import fr.familia.app.feature.gifts.notebook.NotebookActions
import fr.familia.app.feature.gifts.notebook.NotebookIdeaScreen
import fr.familia.app.feature.gifts.notebook.NotebookScreen
import fr.familia.app.feature.gifts.notebook.ProposeIdeaActions
import fr.familia.app.feature.gifts.notebook.ProposeIdeaScreen
import fr.familia.app.feature.gifts.notebook.StatusSheetContent
import fr.familia.app.feature.gifts.notebook.previewNotebook
import fr.familia.app.feature.gifts.notebook.previewNotebookIdea
import fr.familia.app.feature.gifts.notebook.previewProposeIdea
import fr.familia.app.feature.gifts.received.ReceivedGiftsScreen
import fr.familia.app.feature.gifts.received.previewReceivedGifts
import fr.familia.app.core.ui.LocalMemberPhotos
import fr.familia.app.feature.members.photo.PhotoCropContent
import fr.familia.app.feature.members.photo.previewPicture
import fr.familia.app.feature.goodnews.detail.GoodNewsDetailScreen
import fr.familia.app.feature.goodnews.detail.GoodNewsDetailUiState
import fr.familia.app.feature.goodnews.edit.GoodNewsEditScreen
import fr.familia.app.feature.goodnews.edit.GoodNewsFormError
import fr.familia.app.feature.goodnews.edit.previewGoodNewsEdit
import fr.familia.app.feature.goodnews.feed.GoodNewsFeedScreen
import fr.familia.app.feature.goodnews.feed.GoodNewsFeedUiState
import fr.familia.app.feature.goodnews.home.GoodNewsHomeContent
import fr.familia.app.feature.goodnews.home.GoodNewsHomeUiState
import fr.familia.app.feature.goodnews.previewGoodNewsItems
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import fr.familia.app.feature.home.HomeR1bisActions
import fr.familia.app.feature.home.HomeR1bisScreen

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
    @Config(qualifiers = "+night")
    fun clayCardsDarkInList() = capture("argile_cartes_sombre_liste", darkTheme = true) { ClayCardSamples(lazy = true) }

    @Test
    @Config(fontScale = 2f)
    fun clayCardsFontScale200() = capture("argile_cartes_police_200") { ClayCardSamples() }

    @Test
    fun birthdaysLight() = capture("anniversaires_clair") { Surface { BirthdaysScreen(previewBirthdays(), onRetry = {}, onAddToCalendar = {}, onShare = {}) } }

    @Test
    @Config(qualifiers = "+night")
    fun birthdaysDark() = capture("anniversaires_sombre", darkTheme = true) {
        Surface { BirthdaysScreen(previewBirthdays(), onRetry = {}, onAddToCalendar = {}) }
    }

    @Test
    @Config(fontScale = 2f)
    fun birthdaysFontScale200() = capture("anniversaires_police_200") {
        Surface { BirthdaysScreen(previewBirthdays(), onRetry = {}) }
    }

    @Test
    fun membersLight() = capture("famille_clair") {
        Surface { MembersScreen(MembersUiState.Success(previewGroups(), "a"), onRetry = {}, onOpenWishes = {}) }
    }

    @Test
    @Config(qualifiers = "+night")
    fun membersDark() = capture("famille_sombre", darkTheme = true) {
        Surface { MembersScreen(MembersUiState.Success(previewGroups(), "a"), onRetry = {}, onOpenWishes = {}) }
    }

    @Test
    fun membersWithPhotosLight() = capture("famille_photos_clair") { MembersWithPhotos() }

    @Test
    @Config(qualifiers = "+night")
    fun membersWithPhotosDark() = capture("famille_photos_sombre", darkTheme = true) { MembersWithPhotos() }

    @Test
    fun photoCropLight() = capture("photo_cadrage_clair") {
        Surface { PhotoCropContent(previewPicture(), saving = false, onConfirm = {}, onDismiss = {}) }
    }

    @Test
    @Config(qualifiers = "+night")
    fun photoCropDark() = capture("photo_cadrage_sombre", darkTheme = true) {
        Surface { PhotoCropContent(previewPicture(), saving = false, onConfirm = {}, onDismiss = {}) }
    }

    @Test
    @Config(fontScale = 2f)
    fun photoCropFontScale200() = capture("photo_cadrage_police_200") {
        Surface { PhotoCropContent(previewPicture(), saving = false, onConfirm = {}, onDismiss = {}) }
    }

    /** Camille has a photo; Camille and Noé get the photo button (fictional family, gradient picture). */
    @Composable
    private fun MembersWithPhotos() {
        CompositionLocalProvider(LocalMemberPhotos provides mapOf("a" to previewPicture())) {
            Surface {
                MembersScreen(
                    MembersUiState.Success(previewGroups(), "a", photoEditableMemberIds = setOf("a", "c")),
                    onRetry = {},
                    onSavePhoto = { _, _ -> },
                )
            }
        }
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
        Surface { MembersScreen(MembersUiState.Success(previewGroups(), "a"), onRetry = {}, onOpenWishes = {}) }
    }

    @Test
    fun membersErrorLight() = capture("famille_erreur_clair") {
        Surface { MembersScreen(MembersUiState.Error, onRetry = {}) }
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
        Surface { PlanningScreen(previewPlanning(), {}, null, {}) }
    }

    @Test
    @Config(qualifiers = "+night")
    fun planningListDark() = capture("planning_liste_sombre", darkTheme = true) {
        Surface { PlanningScreen(previewPlanning(), {}, null, {}) }
    }

    @Test
    @Config(fontScale = 2f)
    fun planningListFontScale200() = capture("planning_liste_police_200") {
        Surface { PlanningScreen(previewPlanning(), {}, null, {}) }
    }

    @Test
    fun eventDetailLight() = capture("evenement_detail_clair") {
        EventDetailScreen(previewEventDetail(), { _, _ -> }, {}, {}, {}, onAddToCalendar = {})
    }

    @Test
    @Config(qualifiers = "+night")
    fun eventDetailDark() = capture("evenement_detail_sombre", darkTheme = true) {
        EventDetailScreen(previewEventDetail(canManage = false), { _, _ -> }, {}, {}, {}, onAddToCalendar = {})
    }

    @Test
    @Config(fontScale = 2f)
    fun eventDetailFontScale200() = capture("evenement_detail_police_200") {
        EventDetailScreen(previewEventDetail(), { _, _ -> }, {}, {}, {})
    }

    @Test
    fun appearanceLight() = capture("apparence_clair") {
        AppearanceScreen(FamiliaThemeId.Spain, {}, {})
    }

    @Test
    @Config(fontScale = 2f)
    fun appearanceFontScale200() = capture("apparence_police_200") {
        AppearanceScreen(FamiliaThemeId.Classic, {}, {})
    }

    /** « Utilisation » of the admin principal (PRD, récit 1 nonies), with a fictional family. */
    @Test
    fun usageReport() = capture("utilisation") {
        val today = LocalDate.of(2026, 11, 30)
        UsageReportScreen(previewUsageState(today), onRetry = {}, onBack = {}, today = today)
    }

    /** « Icône de l'appli », with the message shown once the icon changed. */
    @Test
    fun appIconChoice() = capture("apparence_icone") {
        Surface { IconSection(AppIcon.Terre, changed = true, onChoose = {}) }
    }

    /** The Accueil in each theme, as on the board of the themes (DESIGN-7). */
    @Test
    fun homeInEveryTheme() {
        FamiliaThemeId.entries.drop(1).forEach { theme ->
            captureRoboImage("build/outputs/roborazzi/accueil_theme_${theme.key}.png") {
                FamiliaTheme(theme = theme) {
                    Box(Modifier.themeBackground()) { HomeR1bisScreen(previewHome(), HomeActions(onOpenWishes = {}), HomeR1bisActions()) }
                }
            }
        }
    }

    @Test
    fun homeLight() = capture("accueil_clair") {
        Surface { HomeR1bisScreen(previewHome(), HomeActions(onOpenWishes = {}), HomeR1bisActions()) }
    }

    @Test
    @Config(qualifiers = "+night")
    fun homeDark() = capture("accueil_sombre", darkTheme = true) {
        Surface { HomeR1bisScreen(previewHome(), HomeActions(onOpenWishes = {}), HomeR1bisActions()) }
    }

    @Test
    @Config(fontScale = 2f)
    fun homeFontScale200() = capture("accueil_police_200") {
        Surface { HomeR1bisScreen(previewHome(), HomeActions(onOpenWishes = {}), HomeR1bisActions()) }
    }

    @Test
    fun planningMonthLight() = capture("planning_mois_clair") {
        Surface { PlanningScreen(previewPlanning(), {}, null, {}, initialMode = PlanningMode.Month) }
    }

    @Test
    @Config(qualifiers = "+night")
    fun planningMonthDark() = capture("planning_mois_sombre", darkTheme = true) {
        Surface { PlanningScreen(previewPlanning(), {}, null, {}, initialMode = PlanningMode.Month) }
    }

    @Test
    @Config(fontScale = 2f)
    fun planningMonthFontScale200() = capture("planning_mois_police_200") {
        Surface { PlanningScreen(previewPlanning(), {}, null, {}, initialMode = PlanningMode.Month) }
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
                onEditMember = {},
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
                onEditMember = {},
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

    private fun previewGenealogy(): GenealogyUiState.Ready {
        fun m(id: String, name: String) = Member(id, name, null, null, "h1", MemberProfile.Adult, false, emptyList(), null)
        val jeanne = Kin("jeanne", "Jeanne", "Martin", living = false, born = "1921", died = "1999", note = "Elle faisait les meilleures crêpes.", householdId = "h1")
        val tree = GenealogyTree(
            listOf(m("camille", "Camille"), m("ines", "Inès"), m("noe", "Noé"), m("lou", "Lou")),
            listOf(jeanne),
            listOf(
                KinLink.parent("jeanne", "camille"),
                KinLink.partners("camille", "ines"),
                KinLink.parent("camille", "noe"),
                KinLink.parent("ines", "noe"),
                KinLink.parent("camille", "lou"),
            ),
        )
        return GenealogyUiState.Ready(tree, mapOf("jeanne" to "Arrière-grand-mère", "ines" to "Bonus-maman"), "noe")
    }

    @Test
    fun genealogyLight() = capture("arbre_genealogique_clair") { GenealogyScreen(previewGenealogy(), onBack = {}) }

    @Test
    @Config(qualifiers = "+night")
    fun genealogyPersonDark() = capture("arbre_fiche_sombre", darkTheme = true) {
        val state = previewGenealogy()
        Surface { PersonCard(state.tree.people.getValue("jeanne"), state.tree, state.words, onOpenProfile = null, onClose = {}) }
    }

    @Test
    fun genealogyEmpty() = capture("arbre_vide") {
        GenealogyScreen(GenealogyUiState.Ready(GenealogyTree(emptyList(), emptyList(), emptyList()), emptyMap(), "noe"), onBack = {})
    }

    @Test
    fun capsuleLeftoversLight() = capture("capsules_anciens_membres_clair") {
        Surface {
            CapsuleLeftoversSection(
                listOf(CapsuleLeftover("m1", "Jules", 2, deleteAsked = false), CapsuleLeftover("m2", "Nina", 1, deleteAsked = true)),
                onDelete = {},
            )
        }
    }

    @Test
    fun consumptionLight() = capture("consommation_clair") { Surface { ConsumptionSection(previewConsumption()) } }

    @Test
    @Config(qualifiers = "+night")
    fun consumptionFailedDark() = capture("consommation_erreur_sombre", darkTheme = true) {
        Surface { ConsumptionSection(ConsumptionCard.Ready(Consumption(LocalDate.of(2026, 11, 20), failed = true))) }
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
    fun onboardingChildDark() = capture("tuto_enfant_sombre", darkTheme = true) { onboarding(answersForChildren = false, index = 2) }

    @Test
    @Config(fontScale = 2f)
    fun onboardingNotificationsFontScale200() = capture("tuto_notifications_police_200") {
        onboarding(answersForChildren = true, index = 3)
    }

    @Composable
    private fun onboarding(answersForChildren: Boolean, index: Int) {
        OnboardingScreen(onboardingPages(answersForChildren), index, notificationsAllowed = false, {}, {}, {}, {})
    }

    @Test
    fun helpLight() = capture("aide_clair") {
        Surface { HelpScreen(HelpUiState(helpTopics(isChef = true, isOwner = true), "Camille ou Inès"), {}, {}, initiallyOpen = HelpTopic.AddEvent, versionName = "0.1.0") }
    }

    @Test
    @Config(qualifiers = "+night")
    fun helpSheetDark() = capture("aide_planning_sombre", darkTheme = true) {
        Surface { HelpSheetContent(ScreenHelp.Planning, onOpenAllHelp = {}) }
    }

    @Test
    @Config(fontScale = 2f)
    fun helpFontScale200() = capture("aide_police_200") {
        Surface { HelpScreen(HelpUiState(helpTopics(isChef = false), null), {}, {}, initiallyOpen = HelpTopic.Notifications, versionName = "0.1.0") }
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

    // --- Listes et cadeaux (V2) ---

    @Test
    fun giftsLight() = capture("cadeaux_clair") { Surface { GiftsScreen(previewGifts(), {}, {}, {}) } }

    @Test
    @Config(qualifiers = "+night")
    fun giftsDark() = capture("cadeaux_sombre", darkTheme = true) { Surface { GiftsScreen(previewGifts(), {}, {}, {}) } }

    @Test
    @Config(fontScale = 2f)
    fun giftsFontScale200() = capture("cadeaux_police_200") { Surface { GiftsScreen(previewGifts(), {}, {}, {}) } }

    @Test
    fun giftListLight() = capture("liste_cadeaux_clair") { GiftListScreen(previewGiftList(), GiftListActions()) }

    @Test
    @Config(qualifiers = "+night")
    fun giftListMineDark() = capture("liste_cadeaux_beneficiaire_sombre", darkTheme = true) {
        GiftListScreen(previewGiftList(keptFromMe = true), GiftListActions())
    }

    @Test
    @Config(fontScale = 2f)
    fun giftListFontScale200() = capture("liste_cadeaux_police_200") { GiftListScreen(previewGiftList(), GiftListActions()) }

    @Test
    @Config(qualifiers = "+night")
    fun giftItemSheetDark() = capture("liste_cadeaux_detail_sombre", darkTheme = true) {
        val state = previewGiftList()
        Surface { ItemSheetContent(state.items[1], state, GiftListActions(), onEdit = {}) }
    }

    @Test
    @Config(fontScale = 2f)
    fun giftItemSheetFontScale200() = capture("liste_cadeaux_detail_police_200") {
        val state = previewGiftList()
        Surface { ItemSheetContent(state.items[0], state, GiftListActions(), onEdit = {}) }
    }

    @Test
    fun notebookLight() = capture("carnet_cadeaux_clair") { NotebookScreen(previewNotebook(), NotebookActions()) }

    @Test
    @Config(qualifiers = "+night")
    fun notebookStatusDark() = capture("carnet_cadeaux_etat_sombre", darkTheme = true) {
        Surface { StatusSheetContent(previewNotebook().people.first().entries[1], {}, {}) }
    }

    @Test
    @Config(fontScale = 2f)
    fun notebookFontScale200() = capture("carnet_cadeaux_police_200") { NotebookScreen(previewNotebook(), NotebookActions()) }

    @Test
    @Config(fontScale = 2f)
    fun notebookIdeaFontScale200() = capture("carnet_cadeaux_idee_police_200") { NotebookIdeaScreen(previewNotebookIdea(), {}, {}, {}, {}) }

    @Test
    @Config(fontScale = 2f)
    fun proposeIdeaFontScale200() = capture("carnet_cadeaux_proposer_police_200") {
        ProposeIdeaScreen(previewProposeIdea(), ProposeIdeaActions(), today = LocalDate.of(2026, 11, 16))
    }

    @Test
    fun receivedGiftsLight() = capture("on_m_a_offert_clair") { ReceivedGiftsScreen(previewReceivedGifts()) }

    @Test
    @Config(fontScale = 2f)
    fun receivedGiftsFontScale200() = capture("on_m_a_offert_police_200") { ReceivedGiftsScreen(previewReceivedGifts()) }

    @Test
    fun giftListFormLight() = capture("liste_cadeaux_formulaire_clair") { GiftListEditScreen(previewGiftListForm(), {}, {}, {}, {}) }

    @Test
    @Config(fontScale = 2f)
    fun giftItemFormFontScale200() = capture("article_formulaire_police_200") { GiftItemEditScreen(previewGiftItemForm(), {}, {}, {}, {}) }

    @Test
    fun sharedWishForm() = capture("article_partage_une_idee_pour") { GiftItemEditScreen(previewSharedWishForm(), {}, {}, {}, {}) }

    // --- Coups de main (fil [DEV] coups de main) ---------------------------------------------------

    @Test
    fun helpRequestsLight() = capture("entraide_liste_clair") { Surface { HelpRequestsScreen(previewHelpRequests(), {}, {}, {}) } }

    @Test
    @Config(qualifiers = "+night")
    fun helpRequestsDark() = capture("entraide_liste_sombre", darkTheme = true) {
        Surface { HelpRequestsScreen(previewHelpRequests(), {}, {}, {}) }
    }

    @Test
    @Config(fontScale = 2f)
    fun helpRequestsFontScale200() = capture("entraide_liste_police_200") {
        Surface { HelpRequestsScreen(previewHelpRequests(), {}, {}, {}) }
    }

    @Test
    fun helpRequestEditLight() = capture("entraide_demande_clair") {
        HelpRequestEditScreen(previewHelpRequestEdit(), {}, {}, {}, {}, {})
    }

    @Test
    @Config(qualifiers = "+night")
    fun helpRequestEditDark() = capture("entraide_demande_sombre", darkTheme = true) {
        HelpRequestEditScreen(previewHelpRequestEdit(errors = setOf(HelpFormError.TitleMissing)), {}, {}, {}, {}, {})
    }

    @Test
    @Config(fontScale = 2f)
    fun helpRequestEditFontScale200() = capture("entraide_demande_police_200") {
        HelpRequestEditScreen(previewHelpRequestEdit(), {}, {}, {}, {}, {})
    }

    @Test
    fun helpRequestDetailLight() = capture("entraide_detail_clair") {
        HelpRequestDetailScreen(previewHelpRequestDetail(), {}, {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    @Config(qualifiers = "+night")
    fun helpRequestDetailDark() = capture("entraide_detail_sombre", darkTheme = true) {
        HelpRequestDetailScreen(previewHelpRequestDetail(isMine = true), {}, {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    @Config(fontScale = 2f)
    fun helpRequestDetailFontScale200() = capture("entraide_detail_police_200") {
        HelpRequestDetailScreen(previewHelpRequestDetail(), {}, {}, {}, {}, {}, {}, {}, {}, {})
    }

    // --- Bonnes nouvelles (récit 32) -------------------------------------------------------------

    private val goodNewsNow = Instant.parse("2026-11-21T16:00:00Z")

    private fun goodNewsFeed() = GoodNewsFeedUiState.Success(previewGoodNewsItems(), canLoadMore = true, now = goodNewsNow)

    @Test
    fun goodNewsFeedLight() = capture("bonnes_nouvelles_fil_clair") {
        GoodNewsFeedScreen(goodNewsFeed(), {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})
    }

    @Test
    @Config(qualifiers = "+night")
    fun goodNewsFeedDark() = capture("bonnes_nouvelles_fil_sombre", darkTheme = true) {
        GoodNewsFeedScreen(goodNewsFeed(), {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})
    }

    @Test
    @Config(fontScale = 2f)
    fun goodNewsFeedFontScale200() = capture("bonnes_nouvelles_fil_police_200") {
        GoodNewsFeedScreen(goodNewsFeed(), {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun goodNewsDetailLight() = capture("bonnes_nouvelles_detail_clair") {
        GoodNewsDetailScreen(GoodNewsDetailUiState.Success(previewGoodNewsItems()[1], goodNewsNow), {}, {}, {}, {}, {}, {})
    }

    @Test
    fun goodNewsEditLight() = capture("bonnes_nouvelles_publier_clair") {
        GoodNewsEditScreen(previewGoodNewsEdit(), {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    @Config(fontScale = 2f)
    fun goodNewsEditFontScale200() = capture("bonnes_nouvelles_publier_police_200") {
        GoodNewsEditScreen(previewGoodNewsEdit(setOf(GoodNewsFormError.NoPhoto)), {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun goodNewsHomeCardLight() = capture("bonnes_nouvelles_accueil_clair") {
        Surface { GoodNewsHomeContent(GoodNewsHomeUiState.Ready(previewGoodNewsItems(), goodNewsNow), {}, {}, {}) }
    }

    @Test
    @Config(qualifiers = "+night")
    fun goodNewsHomeCardEmptyDark() = capture("bonnes_nouvelles_accueil_vide_sombre", darkTheme = true) {
        Surface { GoodNewsHomeContent(GoodNewsHomeUiState.Ready(emptyList(), goodNewsNow), {}, {}, {}) }
    }

    private val sampleMember = Member("a", "Camille", null, null, "h1", MemberProfile.Adult, true, emptyList(), null)

    private fun capture(
        name: String,
        darkTheme: Boolean = false,
        theme: FamiliaThemeId = FamiliaThemeId.Classic,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = darkTheme, theme = theme) { content() }
        }
    }
}
