package fr.familia.app.core.navigation

import fr.familia.app.core.mention.LocalMentionFamily
import fr.familia.app.core.mention.LocalOpenMember
import fr.familia.app.core.power.LocalBatterySaving
import fr.familia.app.core.power.watchBatterySaving
import fr.familia.app.core.welcome.OliveMascot
import fr.familia.app.core.welcome.LocalMascotFruits
import fr.familia.app.core.welcome.welcomeFruits
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.data.familyToday
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.ui.graphics.vector.ImageVector
import fr.familia.app.feature.albums.FeedPhotosDestination
import fr.familia.app.feature.albums.FeedPhotosRoute
import fr.familia.app.feature.all.AllFamiliaScreen
import fr.familia.app.feature.all.AllModule
import fr.familia.app.feature.all.ModuleFamily
import fr.familia.app.feature.feed.FeedOpenRequest
import fr.familia.app.feature.home.HomeAddOptions
import fr.familia.app.feature.home.HomeAddSheet
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import fr.familia.app.BuildConfig
import fr.familia.app.R
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.core.whatsnew.WhatsNewDestination
import fr.familia.app.core.crash.CrashCatcher
import fr.familia.app.core.crash.PendingCrashStore
import fr.familia.app.core.crash.crashScreenName
import fr.familia.app.core.crash.sendPendingCrashes
import fr.familia.app.feature.announcements.AnnouncementDestination
import fr.familia.app.feature.announcements.AnnouncementRoute
import fr.familia.app.feature.announcements.AnnouncementsDestination
import fr.familia.app.feature.announcements.AnnouncementsRoute
import fr.familia.app.feature.crashes.CrashesDestination
import fr.familia.app.feature.crashes.CrashesRoute
import fr.familia.app.core.whatsnew.WhatsNewHost
import fr.familia.app.core.whatsnew.WhatsNewRoute
import fr.familia.app.core.whatsnew.WhatsNewTarget
import fr.familia.app.feature.update.UpdateBanner
import fr.familia.app.feature.update.UpdatePreferences
import fr.familia.app.feature.update.UpdateSheet
import fr.familia.app.feature.update.shouldShowUpdateSheet
import fr.familia.app.feature.update.availableUpdate
import fr.familia.app.feature.update.InAppUpdateDialogs
import fr.familia.app.feature.update.LocalInAppUpdater
import fr.familia.app.feature.update.rememberInAppUpdater
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.memberInitials
import fr.familia.app.core.model.GoodDealKind
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.notifications.NotificationChannels
import fr.familia.app.core.model.FeatureFlags
import fr.familia.app.core.model.UsageScreen
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.feature.update.UpdateCheckRoute
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.theme.LocalClayColors
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import fr.familia.app.core.ui.Confirmation
import fr.familia.app.core.ui.LocalSnackbarHost
import fr.familia.app.core.ui.showConfirmation
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import fr.familia.app.feature.capsules.CapsuleDetailDestination
import fr.familia.app.feature.recap.RecapRoute
import fr.familia.app.feature.capsules.CapsuleDetailRoute
import fr.familia.app.feature.capsules.CapsuleEditDestination
import fr.familia.app.feature.capsules.CapsuleEditRoute
import fr.familia.app.feature.capsules.CapsulesDestination
import fr.familia.app.feature.capsules.CapsulesRoute
import fr.familia.app.feature.genealogy.GenealogyDestination
import fr.familia.app.feature.genealogy.GenealogyRoute
import fr.familia.app.feature.genealogy.poster.TreePosterDestination
import fr.familia.app.feature.genealogy.poster.TreePosterRoute
import fr.familia.app.core.album.book.PhotoBookDestination
import fr.familia.app.feature.albums.book.PhotoBookRoute
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.HelpRoute
import fr.familia.app.feature.gooddeals.EntraideTab
import fr.familia.app.feature.gooddeals.EntraideTabs
import fr.familia.app.feature.gooddeals.GoodDealDetailRoute
import fr.familia.app.feature.gooddeals.GoodDealEditRoute
import fr.familia.app.feature.gooddeals.GoodDealsRoute
import fr.familia.app.feature.lending.LendItemDetailRoute
import fr.familia.app.feature.lending.LendItemEditRoute
import fr.familia.app.feature.lending.LendItemsRoute
import fr.familia.app.feature.goodnews.detail.GoodNewsDetailRoute
import fr.familia.app.feature.goodnews.edit.GoodNewsEditRoute
import fr.familia.app.feature.goodnews.feed.GoodNewsFeedRoute
import fr.familia.app.feature.members.profile.MemberProfileRoute
import fr.familia.app.feature.members.profile.ProfileGiftActions
import fr.familia.app.feature.gifts.surprise.SurprisePickRoute
import fr.familia.app.feature.weeklyquestion.WeeklyArchiveRoute
import fr.familia.app.feature.weeklyquestion.WeeklyQuestionRoute
import fr.familia.app.feature.help.LocalOpenHelp
import fr.familia.app.feature.help.ScreenHelp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import fr.familia.app.feature.members.edit.MemberEditRoute
import fr.familia.app.feature.birthdays.BirthdaysRoute
import fr.familia.app.feature.events.detail.EventDetailRoute
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.photo.LocalPhotoStorage
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.ideasListId
import fr.familia.app.core.model.NotebookOccasion
import fr.familia.app.core.model.wishesListId
import fr.familia.app.core.ui.LocalMemberInitials
import fr.familia.app.core.ui.LocalMemberPhotos
import fr.familia.app.core.ui.OfflineBanner
import fr.familia.app.core.ui.rememberMemberPhotos
import fr.familia.app.feature.members.MembersRoute
import fr.familia.app.feature.events.edit.EventEditRoute
import fr.familia.app.feature.planning.PlanningRoute
import fr.familia.app.feature.polls.detail.PollDetailRoute
import fr.familia.app.feature.joinme.JoinMeRoute
import fr.familia.app.feature.polls.edit.PollEditRoute
import fr.familia.app.feature.polls.list.PollsRoute
import fr.familia.app.feature.home.HomeActions
import fr.familia.app.feature.home.HomePreferences
import fr.familia.app.feature.settings.appearance.AppIconSwitcher
import fr.familia.app.feature.gifts.share.SharedWish
import fr.familia.app.feature.gifts.share.ShareEntry
import fr.familia.app.feature.gifts.share.ShareTargetSwitch
import fr.familia.app.feature.gifts.share.shareEntryFor
import fr.familia.app.feature.usage.UsageRoute
import fr.familia.app.feature.calendarfeed.CalendarFeedRoute
import fr.familia.app.feature.feedback.FeedbackFormDestination
import fr.familia.app.feature.feedback.FeedbackFormRoute
import fr.familia.app.feature.feedback.FeedbackListDestination
import fr.familia.app.feature.feedback.FeedbackListRoute
import fr.familia.app.feature.feedback.MyFeedbackDestination
import fr.familia.app.feature.feedback.MyFeedbackRoute
import fr.familia.app.core.model.FeedbackOrigin
import fr.familia.app.feature.tree.HostChallengeRoute
import fr.familia.app.feature.tree.challenges.ChallengesRoute
import fr.familia.app.feature.albums.AlbumEntry
import fr.familia.app.feature.albums.AlbumsDestination
import fr.familia.app.feature.albums.AlbumsRoute
import fr.familia.app.feature.settings.appearance.AppearanceScreen
import fr.familia.app.core.welcome.OpeningStyle
import fr.familia.app.core.welcome.WelcomeHost
import fr.familia.app.core.welcome.WELCOME_OFF_WAIT_MILLIS
import fr.familia.app.core.welcome.WELCOME_WAIT_MILLIS
import fr.familia.app.core.welcome.WelcomePreferences
import fr.familia.app.feature.settings.appearance.ThemePreferences
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.FamiliaThemeId
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.theme.TitleFont
import fr.familia.app.core.theme.BodyFont
import fr.familia.app.core.theme.LocalClayStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.graphics.Color
import fr.familia.app.feature.home.HomeRoute as HomeTab
import fr.familia.app.feature.discover.Discover
import fr.familia.app.feature.discover.DiscoverModule
import fr.familia.app.feature.discover.LocalDiscoverOrder
import fr.familia.app.feature.polls.PollsSummary
import fr.familia.app.feature.polls.canAskPoll
import fr.familia.app.feature.polls.rememberPollsSummary
import fr.familia.app.core.share.shareApp
import androidx.compose.ui.semantics.contentDescription
import fr.familia.app.feature.settings.NotificationSettingsRoute
import fr.familia.app.feature.export.ExportRoute
import fr.familia.app.feature.members.admin.AdministrationRoute
import fr.familia.app.feature.onboarding.OnboardingPreferences
import fr.familia.app.feature.onboarding.OnboardingRoute
import kotlinx.coroutines.CancellationException
import fr.familia.app.feature.gifts.AllBirthdaysScreen
import fr.familia.app.feature.gifts.GiftsRoute
import fr.familia.app.feature.gifts.detail.GiftListRoute
import fr.familia.app.feature.gifts.edit.GiftListEditRoute
import fr.familia.app.feature.gifts.item.GiftItemEditRoute
import fr.familia.app.feature.gifts.notebook.NotebookIdeaRoute
import fr.familia.app.feature.gifts.notebook.NotebookRoute
import fr.familia.app.feature.gifts.notebook.ProposeIdeaRoute
import fr.familia.app.feature.events.answerableMembers
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.feature.helprequests.badgeCount
import fr.familia.app.feature.helprequests.seenToForget
import fr.familia.app.feature.helprequests.detail.HelpRequestDetailRoute
import fr.familia.app.feature.helprequests.edit.HelpRequestEditRoute
import fr.familia.app.feature.helprequests.list.HelpRequestsRoute
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import kotlinx.coroutines.flow.combine
import fr.familia.app.feature.events.album.EventAlbumDestination
import fr.familia.app.feature.events.album.EventAlbumRoute
import fr.familia.app.feature.month.MonthSlideshowDestination
import fr.familia.app.feature.month.MonthSlideshowRoute
import fr.familia.app.feature.yearinreview.YearInReviewDestination
import fr.familia.app.feature.yearinreview.openableReviewYear
import fr.familia.app.feature.yearinreview.YearInReviewRoute
import fr.familia.app.feature.month.slideshowMonthOf
import fr.familia.app.feature.recipes.RecipeDetailDestination
import fr.familia.app.feature.recipes.RecipeDetailRoute
import fr.familia.app.feature.recipes.RecipeEditDestination
import fr.familia.app.feature.recipes.RecipeEditRoute
import fr.familia.app.feature.recipes.RecipesDestination
import fr.familia.app.feature.recipes.book.RecipeBookDestination
import fr.familia.app.feature.recipes.book.RecipeBookRoute
import fr.familia.app.feature.recipes.RecipesRoute
import fr.familia.app.feature.trips.TripDetailDestination
import fr.familia.app.feature.expenses.ExpenseEditRoute
import fr.familia.app.feature.expenses.ExpenseHistoryRoute
import fr.familia.app.feature.expenses.ExpensesRoute
import fr.familia.app.feature.expenses.EventExpenseEditDestination
import fr.familia.app.feature.expenses.EventExpenseEditRoute
import fr.familia.app.feature.expenses.EventExpenseHistoryDestination
import fr.familia.app.feature.expenses.EventExpenseHistoryRoute
import fr.familia.app.feature.expenses.EventExpensesDestination
import fr.familia.app.feature.expenses.EventExpensesRoute
import fr.familia.app.feature.expenses.TripExpenseEditDestination
import fr.familia.app.feature.expenses.TripExpenseHistoryDestination
import fr.familia.app.feature.expenses.TripExpensesDestination
import fr.familia.app.feature.trips.TripEditDestination
import fr.familia.app.feature.trips.TripPhotosDestination
import fr.familia.app.feature.yearago.MemoryAlbum
import fr.familia.app.feature.trips.TripGlobeDestination
import fr.familia.app.feature.trips.TripMapDestination
import fr.familia.app.feature.trips.map.TripGlobeRoute
import fr.familia.app.feature.trips.TripPackingDestination
import fr.familia.app.feature.trips.TripPhrasesDestination
import fr.familia.app.feature.trips.TripSafetyDestination
import fr.familia.app.feature.trips.TripMoneyDestination
import fr.familia.app.feature.trips.TripDocumentsDestination
import fr.familia.app.feature.trips.TripEnrichDestination
import fr.familia.app.feature.trips.TripPlaceAddDestination
import fr.familia.app.feature.trips.TripPlaceDestination
import fr.familia.app.feature.trips.place.PlaceEditRoute
import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.feature.trips.phrases.TripPhrasesRoute
import fr.familia.app.feature.trips.safety.TripSafetyRoute
import fr.familia.app.feature.trips.money.TripMoneyRoute
import fr.familia.app.feature.trips.documents.TripDocumentsRoute
import fr.familia.app.feature.trips.enrich.TripEnrichRoute
import fr.familia.app.feature.trips.packing.TripPackingRoute
import fr.familia.app.feature.trips.TripWordDestination
import fr.familia.app.feature.trips.word.StepWordRoute
import fr.familia.app.feature.trips.TripStepEditDestination
import fr.familia.app.feature.trips.map.TripMapRoute
import fr.familia.app.feature.trips.map.TripScreenOverMap
import fr.familia.app.feature.trips.TripsDestination
import fr.familia.app.feature.trips.carnet.CarnetRoute
import fr.familia.app.feature.trips.carnet.TripCarnetDestination
import fr.familia.app.feature.trips.list.TripPart
import fr.familia.app.feature.trips.detail.TripDetailRoute
import fr.familia.app.feature.trips.edit.TripEditRoute
import fr.familia.app.feature.trips.list.TripsRoute
import fr.familia.app.feature.trips.photos.TripPhotosRoute
import fr.familia.app.feature.trips.step.StepEditRoute
import androidx.compose.material.icons.outlined.CardTravel
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import fr.familia.app.feature.whoisit.WhoIsItDestination
import fr.familia.app.feature.whoisit.WhoIsItEditDestination
import fr.familia.app.feature.whoisit.WhoIsItEditRoute
import fr.familia.app.feature.whoisit.WhoIsItListRoute
import fr.familia.app.feature.whoisit.WhoIsItPostDestination
import fr.familia.app.feature.whoisit.WhoIsItPostRoute
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth


/** Root of the UI: bottom navigation bar plus the single NavHost of the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamiliaApp(
    container: AppContainer?,
    currentMember: Member,
    isOffline: Boolean = false,
    /** The event of a tapped notification, to open once. */
    openEventId: String? = null,
    onEventOpened: () -> Unit = {},
    /** The « coup de main » of a tapped notification, to open once. */
    openHelpRequestId: String? = null,
    onHelpRequestOpened: () -> Unit = {},
    /** The gift list of a tapped notification, to open once (V2). */
    openListId: String? = null,
    onListOpened: () -> Unit = {},
    /** The good news of a tapped notification, to open once. */
    openGoodNewsId: String? = null,
    onGoodNewsOpened: () -> Unit = {},
    /** The trip of a tapped « bien arrivé » notification, to open once (V3). */
    openTripId: String? = null,
    onTripOpened: () -> Unit = {},
    /** The question of the week of a tapped notification, to open once (récit 37). */
    openWeeklyQuestionId: String? = null,
    onWeeklyQuestionOpened: () -> Unit = {},
    openSouvenir: Boolean = false,
    onSouvenirOpened: () -> Unit = {},
    /** The poll of a tapped notification, to open once its module shows. */
    openPollId: String? = null,
    onPollOpened: () -> Unit = {},
    openJoinMe: Boolean = false,
    onJoinMeOpened: () -> Unit = {},
    /** The time capsule of a tapped notification, to open once its module shows (récit 39). */
    openCapsuleId: String? = null,
    onCapsuleOpened: () -> Unit = {},
    /** « La semaine de la famille » of a tapped notification, to open once its module shows (récit 36). */
    /** The Sunday (AAAA-MM-JJ) of the recap notice that was touched, or null. */
    openRecap: String? = null,
    onRecapOpened: () -> Unit = {},
    /** « Une idée ? Un souci ? » of a tapped notification: "list" (Retours) or "mine" (récit 6 sexies). */
    openFeedback: String? = null,
    onFeedbackOpened: () -> Unit = {},
    /** The summary of the new bons plans was touched: Entraide, on its Bons plans (récit 45). */
    openGoodDeals: Boolean = false,
    onGoodDealsOpened: () -> Unit = {},
    /** A thing to lend whose ask, answer or reminder was touched (récit 51). */
    openLendItemId: String? = null,
    onLendItemOpened: () -> Unit = {},
    /** A message of the admin principal whose notification was touched (récit 69). */
    openAnnouncementId: String? = null,
    onAnnouncementOpened: () -> Unit = {},
    /** A link shared from another app, to add to « Mes envies » once (récit 8 bis). */
    sharedWish: SharedWish? = null,
    onSharedWishOpened: () -> Unit = {},
    /** The member of a shared birthday link (récit 31): their page opens once. */
    openMemberId: String? = null,
    onMemberOpened: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    // Each time the app opens, this phone is (re)registered for the notifications of this member;
    // never in « Voir comme », where the phone stays the admin's.
    val viewingAs = LocalViewAs.current.viewed != null
    LaunchedEffect(currentMember.id, viewingAs) {
        if (viewingAs) return@LaunchedEffect
        try {
            container?.notificationRepository?.registerThisDevice(currentMember.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline or no Play services: the next opening tries again.
        }
    }
    // First launch on this phone: the tutorial comes first, and asks for the notifications on its
    // last screen (spec « tuto et aide », récit 25).
    val context = LocalContext.current
    val onboarding = remember(context) { OnboardingPreferences(context.applicationContext) }
    val homePrefs = remember(context) { HomePreferences(context.applicationContext) }
    val themePrefs = remember(context) { ThemePreferences(context.applicationContext) }
    var chosenTheme by remember { mutableStateOf(themePrefs.theme) }
    var chosenTitleFont by remember { mutableStateOf(themePrefs.titleFont) }
    var chosenBodyFont by remember { mutableStateOf(themePrefs.bodyFont) }
    val iconSwitcher = remember(context) { AppIconSwitcher(context.applicationContext) }
    var showOnboarding by rememberSaveable { mutableStateOf(!onboarding.seen) }
    // The welcome plays at each opening while « Longue » is chosen in Apparence, the default
    // (Kévin, 17/11), before the tutorial and before « Quoi de neuf ? »; never in « Voir comme ».
    val welcome = remember(context) { WelcomePreferences(context.applicationContext) }
    var openingStyle by remember { mutableStateOf(welcome.style) }
    var showWelcome by rememberSaveable { mutableStateOf(welcome.style == OpeningStyle.Long) }
    if (showWelcome && !viewingAs) {
        val welcomeFamily = remember(container) { container?.memberRepository?.observeFamily() }
        val welcomeConfig = remember(container) { container?.appConfigRepository?.observeAppConfig() }
        // The profile photos already on the phone go in the fruits; the others keep their letters.
        val welcomePhotos by rememberMemberPhotos(container?.memberPhotoRepository)
        WelcomeHost(
            family = welcomeFamily,
            config = welcomeConfig,
            me = currentMember,
            onPlayed = {
                welcome.seen = true
                welcome.knownOff = false
            },
            onDone = { showWelcome = false },
            photos = welcomePhotos,
            waitMillis = if (welcome.knownOff) WELCOME_OFF_WAIT_MILLIS else WELCOME_WAIT_MILLIS,
            onModuleOff = { welcome.knownOff = true },
        )
        return
    }
    // « Quoi de neuf ? » stays away on a new install: the tutorial shows everything (récit 6 septies).
    val freshInstall = rememberSaveable { !onboarding.seen }
    if (showOnboarding) {
        // The 4th screen mentions answering for one's children only to those who have some.
        val familyFlow = remember(container) { container?.memberRepository?.observeFamily() ?: flowOf(null) }
        val family by familyFlow.collectAsStateWithLifecycle(initialValue = null)
        val answersForChildren = family?.let { answerableMembers(currentMember, it).size > 1 } ?: false
        // Cadeaux and Entraide get a screen only while their module shows (AVIS-18).
        val tutoConfigs = remember(container) {
            container?.appConfigRepository?.observeAppConfig()?.catch { } ?: flowOf(null)
        }
        val tutoConfig by tutoConfigs.collectAsStateWithLifecycle(initialValue = null)
        val tutoFlags = FeatureFlags(
            tutoConfig?.features.orEmpty(),
            isOwner = family?.ownerId != null && family?.ownerId == currentMember.id,
        )
        // Read during the tutorial, so a new phone opens on the Accueil right after it.
        LaunchedEffect(tutoConfig, family) {
            if (tutoConfig != null && family != null) homePrefs.homeOn = tutoFlags.isOn(Feature.Home)
        }
        OnboardingRoute(
            answersForChildren = answersForChildren,
            gifts = tutoFlags.isOn(Feature.Gifts),
            helpRequests = tutoFlags.isOn(Feature.HelpRequests),
            // Switched on for the family, not just seen by the admin principal: only then is time counted.
            usage = tutoFlags.isOnForFamily(Feature.Usage),
        ) {
            onboarding.markSeen()
            showOnboarding = false
        }
        return
    }
    LaunchedEffect(openEventId) {
        if (openEventId != null) {
            navController.navigate(EventDetailDestination(openEventId))
            onEventOpened()
        }
    }

    // Lot 10: a newer version in config/app is offered above the tabs. The check runs each time the
    // app comes back on screen, even if it stayed open in the background: collection stops in the
    // background and starts again, with a fresh read, when the app is shown. "Plus tard" lasts
    // until then.
    val configs = remember(container) {
        container?.appConfigRepository?.observeAppConfig()
            ?.catch { /* Offline or no config yet: nothing to offer, every flag off. */ }
            ?: flowOf(null)
    }
    val appConfig by configs.collectAsStateWithLifecycle(initialValue = null)
    val update = appConfig?.let { availableUpdate(it, BuildConfig.VERSION_CODE, BuildConfig.DOWNLOAD_URL) }
    val updater = rememberInAppUpdater()
    WhatsNewHost(appConfig, currentMember, BuildConfig.VERSION_CODE.toLong(), freshInstall, onOpen = navController::openWhatsNew)

    // Modules still in test are shown to the admin principal only, until switched on in
    // Administration (config/app.features). The owner is read from the live family.
    val liveFamilyFlow = remember(container) {
        container?.memberRepository?.observeFamily()?.catch { emit(Family(emptyList(), emptyList())) } ?: flowOf(null)
    }
    val liveFamily by liveFamilyFlow.collectAsStateWithLifecycle(initialValue = null)
    // While the update downloads, the family turns around the tree (DESIGN, 18/11).
    InAppUpdateDialogs(updater, update, liveFamily, currentMember, container?.memberPhotoRepository)
    val ownerId = liveFamily?.ownerId
    val features = FeatureFlags(appConfig?.features.orEmpty(), isOwner = ownerId != null && ownerId == currentMember.id)

    // « Partager » to Familia (récits 8 bis and 50): in the share menu only with its module, and a
    // shared link opens a new wish once the modules are known. Module off: the link is dropped.
    val shareEntry = shareEntryFor(
        gifts = features.isOn(Feature.Gifts),
        shareToWishes = features.isOn(Feature.ShareToWishes),
        wishFromShare = features.isOn(Feature.WishFromShare),
    )
    val shareSwitch = remember(context) { ShareTargetSwitch(context.applicationContext) }
    LaunchedEffect(appConfig, ownerId) {
        if (appConfig != null && ownerId != null) shareSwitch.set(shareEntry)
    }
    LaunchedEffect(sharedWish, appConfig, ownerId) {
        if (sharedWish != null && appConfig != null && ownerId != null) {
            if (shareEntry != null) {
                navController.navigate(
                    GiftItemEditDestination(
                        wishesListId(currentMember.id),
                        sharedUrl = sharedWish.url,
                        sharedTitle = sharedWish.title,
                        fromShare = shareEntry == ShareEntry.AddToWishes,
                    ),
                )
            }
            onSharedWishOpened()
        }
    }

    // The Accueil (récit 29) is the first tab and the start screen once its module shows. The start
    // is read from the phone at once, and kept for this opening even if the module changes.
    val startTab = TopLevelDestination.START
    LaunchedEffect(appConfig, ownerId) {
        if (appConfig != null && ownerId != null) homePrefs.homeOn = features.isOn(Feature.Home)
    }

    // The Entraide tab and its entry points stay hidden while the module is off (PRD rév. 120).
    val helpRequestsOn = features.isOn(Feature.HelpRequests)
    // The Planning is a page since DESIGN-55 (récit 82): the bar holds the others, « Tout Familia » at its center.
    val tabs = TopLevelDestination.visible(features)
    // A list or a trip opens only once its module shows (démo du 21: no trace of a module off).
    val giftsOn = features.isOn(Feature.Gifts)
    val tripsOn = features.isOn(Feature.Trips)
    LaunchedEffect(openListId, giftsOn) {
        if (openListId != null && giftsOn) {
            navController.navigate(GiftListDestination(openListId))
            onListOpened()
        }
    }
    LaunchedEffect(openTripId, tripsOn) {
        if (openTripId != null && tripsOn) {
            navController.navigate(TripDetailDestination(openTripId))
            onTripOpened()
        }
    }
    // The notification channels of a module appear in the Android settings once it shows.
    LaunchedEffect(features) { NotificationChannels.create(context.applicationContext, features) }
    // A notification of a coup de main opens it only once the module shows; otherwise it is dropped.
    LaunchedEffect(openHelpRequestId, helpRequestsOn) {
        if (openHelpRequestId != null && helpRequestsOn) {
            navController.navigate(HelpRequestDetailDestination(openHelpRequestId))
            onHelpRequestOpened()
        }
    }
    // Same for a good news (PRD, récit 32), while its module is off.
    val goodNewsOn = features.isOn(Feature.GoodNews)
    LaunchedEffect(openGoodNewsId, goodNewsOn) {
        if (openGoodNewsId != null && goodNewsOn) {
            navController.navigate(GoodNewsDetailDestination(openGoodNewsId))
            onGoodNewsOpened()
        }
    }
    // And for the question of the week (récit 37).
    val weeklyOn = features.isOn(Feature.WeeklyQuestion)
    val souvenirsOn = features.isOn(Feature.Souvenirs)
    LaunchedEffect(openSouvenir, souvenirsOn) {
        if (openSouvenir && souvenirsOn) {
            navController.navigate(MemberProfileDestination(currentMember.id))
            onSouvenirOpened()
        }
    }
    LaunchedEffect(openWeeklyQuestionId, weeklyOn) {
        if (openWeeklyQuestionId != null && weeklyOn) {
            navController.navigate(WeeklyQuestionDestination(openWeeklyQuestionId))
            onWeeklyQuestionOpened()
        }
    }

    // And for a poll (récits 2 bis et 2 ter).
    val pollsOn = features.isOn(Feature.Polls)
    LaunchedEffect(openMemberId) {
        if (openMemberId != null) {
            navController.navigate(MemberProfileDestination(openMemberId))
            onMemberOpened()
        }
    }
    LaunchedEffect(openPollId, pollsOn) {
        if (openPollId != null && pollsOn) {
            navController.navigate(PollDetailDestination(openPollId))
            onPollOpened()
        }
    }

    // And for a « Rejoignez-moi » (récit 33): its card is on the Accueil.
    val placesOn = features.isOn(Feature.Places) && features.isOn(Feature.Home)
    LaunchedEffect(openJoinMe, placesOn) {
        if (openJoinMe && placesOn) {
            navController.navigateToTab(TopLevelDestination.Home)
            onJoinMeOpened()
        }
    }

    // And for a time capsule that opened today (récit 39).
    val capsulesOn = features.isOn(Feature.Capsules)
    LaunchedEffect(openCapsuleId, capsulesOn) {
        if (openCapsuleId != null && capsulesOn) {
            navController.navigate(CapsuleDetailDestination(openCapsuleId))
            onCapsuleOpened()
        }
    }

    // And for « La semaine de la famille » (récit 36). The notice of the last Sunday of the month,
    // with the slideshow on, opens the slideshow of that month instead (récit 42), whenever touched.
    val recapOn = features.isOn(Feature.Recap)
    val slideshowOn = features.isOn(Feature.MonthSlideshow)
    LaunchedEffect(openRecap, recapOn, slideshowOn) {
        if (openRecap != null && recapOn) {
            val month = if (slideshowOn) slideshowMonthOf(openRecap) else null
            if (month != null) {
                navController.navigate(MonthSlideshowDestination(month.year, month.monthValue)) { launchSingleTop = true }
            } else {
                navController.navigate(RecapDestination) { launchSingleTop = true }
            }
            onRecapOpened()
        }
    }

    // And for an idea or a problem: Retours for the admin principal, « Mes demandes » for its author.
    // « Plantages » (récit 6 sexies, rév. 220): the screen on display goes with a crash report, by
    // the name of its destination only (never an id), and the crashes kept on the phone leave now.
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { CrashCatcher.screen = crashScreenName(it.destination.route) }
    }
    LaunchedEffect(container, currentMember.id) {
        val crashes = container?.crashRepository ?: return@LaunchedEffect
        val store = PendingCrashStore(context.applicationContext)
        sendPendingCrashes(store.all(), currentMember.id, crashes, store::remove)
    }
    LaunchedEffect(openFeedback) {
        when (openFeedback) {
            "list" -> navController.navigate(FeedbackListDestination) { launchSingleTop = true }
            "mine" -> navController.navigate(MyFeedbackDestination) { launchSingleTop = true }
            "crashes" -> navController.navigate(CrashesDestination) { launchSingleTop = true }
        }
        if (openFeedback != null) onFeedbackOpened()
    }
    // The bons plans of the day (récit 45): Entraide, turned to its Bons plans, once the modules
    // are known. Module off: Entraide alone, and the tap is spent either way, so switching the
    // module on later does not jump to Bons plans.
    var entraideOnGoodDeals by remember { mutableStateOf(0) }
    // A sub-tab of Entraide asked from « Tout Familia » (PRODUIT-118), spent once Entraide shows it.
    var entraideTab by remember { mutableStateOf<EntraideTab?>(null) }
    val goodDealsSwitchOn = features.isOn(Feature.GoodDeals)
    LaunchedEffect(openGoodDeals, appConfig, ownerId) {
        if (openGoodDeals && appConfig != null && ownerId != null) {
            if (goodDealsSwitchOn) entraideOnGoodDeals++
            if (helpRequestsOn) navController.navigateToTab(TopLevelDestination.HelpRequests)
            onGoodDealsOpened()
        }
    }

    // On se prête (récit 51): the thing itself, once the modules are known; module off, the tap is spent.
    LaunchedEffect(openLendItemId, appConfig, ownerId) {
        if (openLendItemId != null && appConfig != null && ownerId != null) {
            if (features.isOn(Feature.Lending)) navController.navigate(LendItemDetailDestination(openLendItemId))
            onLendItemOpened()
        }
    }


    // Annonces de l'admin (récit 69): the whole message, whatever the module says now (it was sent).
    LaunchedEffect(openAnnouncementId) {
        if (openAnnouncementId != null) {
            navController.navigate(AnnouncementDestination(openAnnouncementId)) { launchSingleTop = true }
            onAnnouncementOpened()
        }
    }

    var updateDismissed by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { updateDismissed = false }
    // DESIGN-47 (F): the sheet comes once per version, then the dark card stays until the update.
    val updateContext = LocalContext.current
    val updatePrefs = remember(updateContext) { UpdatePreferences(updateContext) }
    var updateSheetSeen by remember(updatePrefs) { mutableStateOf(updatePrefs.sheetSeen) }

    // Récit 15: the Entraide tab counts the open requests where I was warned and can still help,
    // and that I have not seen yet in the « Coups de main » tab (retour [BUG] du 18/11).
    val helpBadges = remember(container, currentMember) {
        val seen = container?.seenHelpRequests(currentMember.id)
        container?.helpRequestRepository?.observeMine(currentMember.id)
            ?.combine(currentDayFlow()) { requests, today -> requests to today }
            ?.combine(seen?.seen ?: flowOf(emptySet<String>())) { (requests, today), seenIds ->
                seen?.forget(seenToForget(requests, currentMember, today, seenIds))
                badgeCount(requests, currentMember, today, seenIds)
            }
            ?.catch { emit(0) }
            ?: flowOf(0)
    }
    val helpBadge by helpBadges.collectAsStateWithLifecycle(initialValue = 0)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // The bar only shows on the tabs, not on screens opened from them.
    val onTab = TopLevelDestination.entries.any { currentDestination.isOn(it) }
    val onPlanningPage = currentDestination.isOn(TopLevelDestination.Planning)

    // Profile photos (PRD, récit 3 bis): read once here, shown by every avatar of the app.
    val photos by rememberMemberPhotos(container?.memberPhotoRepository)

    // « C'est enregistré », at the bottom, once a form closes (audit DESIGN du 14/11).
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val confirm: (Confirmation) -> Unit = { confirmation ->
        scope.launch { snackbar.showConfirmation(context.getString(confirmation.textRes)) }
    }
    // A « Je m'en occupe » sent offline, refused at the return of the network: said gently, never as an error.
    LaunchedEffect(container?.helpRequestRepository) {
        container?.helpRequestRepository?.lateRefusals?.collect { name ->
            snackbar.showConfirmation(
                name?.let { context.getString(R.string.helprequest_late_taken_by, it) } ?: context.getString(R.string.helprequest_late_taken),
            )
        }
    }

    // Time spent in the app (PRD, récit 1 nonies): counted on this phone only once the module is on
    // for the family, the admin principal included, then sent once a day.
    val usageTracker = container?.usageTracker
    if (usageTracker != null && features.isOnForFamily(Feature.Usage)) {
        val usageScreen = UsageScreen.forRoute(currentDestination?.route)
        LifecycleStartEffect(usageTracker, currentMember.id) {
            usageTracker.start(currentMember.id)
            onStopOrDispose { usageTracker.stop() }
        }
        LaunchedEffect(usageTracker, usageScreen) { usageTracker.show(usageScreen) }
    }

    // The theme chosen on this phone shows once the Thèmes module is on; before, Classique (DESIGN-7).
    val themesOn = features.isOn(Feature.Themes)
    FamiliaTheme(
        theme = if (themesOn) chosenTheme else FamiliaThemeId.Classic,
        seeds = true,
        titleFont = if (themesOn) chosenTitleFont else TitleFont.Round,
        bodyFont = if (themesOn) chosenBodyFont else BodyFont.Normal,
    ) {
    CompositionLocalProvider(
        LocalOpenHelp provides { navController.navigate(HelpDestination) { launchSingleTop = true } },
        LocalMemberPhotos provides photos,
        LocalMemberInitials provides remember(liveFamily?.members) {
            memberInitials(liveFamily?.members.orEmpty())
        },
        // The fruits of the olive tree mascot (DESIGN-58): the family, my household first.
        LocalMascotFruits provides remember(liveFamily, currentMember) {
            liveFamily?.let { welcomeFruits(it, currentMember) }.orEmpty()
        },
        LocalFeatureFlags provides features,
        // « Économie de batterie » (récit 112, règle 46): one receiver for the whole app.
        LocalBatterySaving provides watchBatterySaving(),
        // « @Prénom » in the texts and comments (Kévin, 19/11).
        LocalMentionFamily provides liveFamily,
        LocalOpenMember provides { id -> navController.navigate(MemberProfileDestination(id)) },
        LocalDiscoverOrder provides remember(appConfig?.discoverOrder) { Discover.order(appConfig?.discoverOrder.orEmpty()) },
        LocalInAppUpdater provides updater,
        LocalSnackbarHost provides snackbar,
        LocalPhotoStorage provides container?.photoStorage,
    ) {
        // Pinned: the title bar stays, and takes the surface color once the content scrolls under it
        // (audit DESIGN n°4), so the theme's pattern no longer mixes with the title.
        val topBarScroll = TopAppBarDefaults.pinnedScrollBehavior()
        // The sheet of my photo (DESIGN-12): my profile and the settings that were under Famille.
        var accountOpen by rememberSaveable { mutableStateOf(false) }
        // A tablet of 7 inches or more, in portrait (600 dp) or landscape: the rail instead of the bar.
        val useRail = LocalConfiguration.current.screenWidthDp >= 600
        var addOpen by rememberSaveable { mutableStateOf(false) }
        // The « + » of the Accueil: what can be added, each only with its module.
        if (addOpen) {
            HomeAddSheet(
                options = HomeAddOptions(
                    // « Dire où je suis » first (DESIGN, 17/11): it left the Accueil for « + ».
                    onWhereAmI = if (features.isOn(Feature.Places)) ({ navController.navigate(JoinMeDestination) { launchSingleTop = true } }) else null,
                    onEvent = { navController.navigate(EventEditDestination()) },
                    // « Une photo » chooses its album (DESIGN, 17/11).
                    onPhoto = if (features.isOn(Feature.Trips) || features.isOn(Feature.EventAlbums)) ({ navController.navigate(AlbumsDestination) }) else null,
                    onGoodNews = if (features.isOn(Feature.GoodNews)) ({ navController.navigate(GoodNewsEditDestination()) }) else null,
                    // « Ajouter une envie » only on the screen of the wishes (retour de Kévin, 18/11).
                    onHelp = if (features.isOn(Feature.HelpRequests)) ({ navController.navigate(HelpRequestEditDestination()) }) else null,
                ),
                onDismiss = { addOpen = false },
            )
        }
        if (accountOpen) {
            val shareContext = LocalContext.current
            val inViewAs = LocalViewAs.current.viewed != null
            AccountSheet(
                container = container,
                member = currentMember,
                actions = AccountActions(
                    onOpenProfile = if (souvenirsOn) ({ navController.navigate(MemberProfileDestination(currentMember.id)) }) else null,
                    onOpenAppearance = if (themesOn) ({ navController.navigate(AppearanceDestination) }) else null,
                    onOpenNotifications = { navController.navigate(NotificationSettingsDestination) },
                    onOpenHelp = { navController.navigate(HelpDestination) { launchSingleTop = true } },
                    onOpenWhatsNew = { navController.navigate(WhatsNewDestination) { launchSingleTop = true } },
                    onOpenUpdates = { navController.navigate(UpdateCheckDestination) },
                    // Nothing leaves the app while the admin looks through a member's eyes.
                    onOpenFeedback = if (inViewAs) null else ({ navController.navigate(FeedbackFormDestination(FeedbackOrigin.Family.key)) }),
                    onShareApp = if (inViewAs) null else ({ owner -> shareApp(shareContext, owner) }),
                    onOpenChildrenUsage = if (features.isOn(Feature.Usage)) ({ navController.navigate(UsageDestination) }) else null,
                    onOpenAdministration = { navController.navigate(AdministrationDestination) },
                    onOpenCalendarFeed = if (features.isOn(Feature.CalendarFeed) && !inViewAs) ({ navController.navigate(CalendarFeedDestination) }) else null,
                ),
                onDismiss = { accountOpen = false },
            )
        }
        Scaffold(
            // The theme's background and its pattern show behind the tabs.
            modifier = Modifier.themeBackground().nestedScroll(topBarScroll.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                // The tabs get a title bar, with the « ? » of their screen (spec « tuto et aide », récit 26).
                val tab = TopLevelDestination.entries.firstOrNull { currentDestination.isOn(it) }
                // The Bonnes nouvelles, the question of the week and the trips have their card on the
                // Accueil: the Planning bar keeps them only while the Accueil is off, so its title is
                // never cut (audit DESIGN n°4).
                val viaPlanning = !features.isOn(Feature.Home)
                val onGoodNews = if (goodNewsOn && viaPlanning) ({ navController.navigate(GoodNewsFeedDestination) }) else null
                val onWeekly = if (weeklyOn && viaPlanning) ({ navController.navigate(WeeklyQuestionDestination()) }) else null
                val onTrips = if (features.isOn(Feature.Trips) && viaPlanning) ({ navController.navigate(TripsDestination) { launchSingleTop = true } }) else null
                // The Voyages list draws its own sky-colored bar.
                if (tab != null && tab != TopLevelDestination.Trips) {
                    TabTopBar(
                        tab, currentMember,
                        scrollBehavior = topBarScroll,
                        onOpenTrips = onTrips,
                        onGoodNews = onGoodNews,
                        onWeeklyQuestion = onWeekly,
                        onAccount = { accountOpen = true },
                        greeting = if (tab == TopLevelDestination.Home) stringResource(R.string.home_r1_greeting, currentMember.firstName) else null,
                        // The Planning is a page (récit 82), and Entraide once Voyages is in the bar: the round « Retour » goes back to the Accueil.
                        onBack = if (TopLevelDestination.isPage(tab, features)) ({ if (!navController.popBackStack()) navController.navigateToTab(TopLevelDestination.Home) }) else null,
                        onAdd = when {
                            tab == TopLevelDestination.Home -> ({ addOpen = true })
                            else -> null
                        },
                    )
                }
            },
            bottomBar = {
                // The round rises ALL_ROUND_RISE above the bar (récit 82). That rise belongs to the bar's
                // height: the screens then stop above it, so the round never hides the end of a list, and
                // its top half is touchable too (note C de #431).
                if (onTab && !useRail) Box {
                Column {
                Spacer(Modifier.height(ALL_ROUND_RISE))
                NavigationBar {
                    tabs.forEachIndexed { index, destination ->
                        // « Tout Familia » takes the center: 2 tabs on each side (DESIGN-55, variante A).
                        if (index == tabs.size / 2) Box(Modifier.weight(1f))
                        // The Planning opens as a page of the Accueil, which stays lit.
                        val selected = currentDestination.isOn(destination) || (destination == TopLevelDestination.Home && onPlanningPage)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(destination) },
                            // R1 bis: the tab on shows in a yellow pill, its icon dark on it.
                            colors = NavigationBarItemDefaults.colors(indicatorColor = LocalClayStyle.current.tabPill, selectedIconColor = Color(0xFF1B1B1B)),
                            icon = {
                                val badge = if (destination == TopLevelDestination.HelpRequests) helpBadge else 0
                                BadgedBox(badge = { if (badge > 0) Badge { Text(badge.toString()) } }) {
                                    Icon(
                                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = null, // The label below already names the tab.
                                    )
                                }
                            },
                            label = { TabLabel(stringResource(destination.labelFor(currentMember))) },
                        )
                    }
                }
                }
                AllFamiliaRound(
                    news = helpBadge,
                    onClick = { navController.navigate(AllFamiliaDestination) { launchSingleTop = true } },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                }
            },
        ) { innerPadding ->
            // Consumed here, so the inner screens with their own Scaffold do not add the system bars again.
            // R1 bis on a tablet (§5): the tabs go to a rail on the left, with « + » at its top on the Accueil.
            Row(Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)) {
            if (onTab && useRail) {
                NavigationRail(
                    header = {
                        if (currentDestination.isOn(TopLevelDestination.Home)) {
                            FloatingActionButton(onClick = { addOpen = true }) {
                                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.home_r1_add))
                            }
                        }
                    },
                    modifier = Modifier.width(88.dp),
                ) {
                    tabs.forEach { destination ->
                        val selected = currentDestination.isOn(destination) || (destination == TopLevelDestination.Home && onPlanningPage)
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(destination) },
                            colors = NavigationRailItemDefaults.colors(indicatorColor = LocalClayStyle.current.tabPill, selectedIconColor = Color(0xFF1B1B1B)),
                            icon = {
                                val badge = if (destination == TopLevelDestination.HelpRequests) helpBadge else 0
                                BadgedBox(badge = { if (badge > 0) Badge { Text(badge.toString()) } }) {
                                    Icon(
                                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = null,
                                    )
                                }
                            },
                            label = { TabLabel(stringResource(destination.labelFor(currentMember))) },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    AllFamiliaRound(news = helpBadge, onClick = { navController.navigate(AllFamiliaDestination) { launchSingleTop = true } })
                }
            }
            Column(Modifier.weight(1f)) {
                AnimatedVisibility(visible = isOffline) { OfflineBanner() }
                // A double tap opens one screen, not two (démo du 21).
                Box(Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = startTab.route,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable<HomeRoute> {
                        HomeTab(
                            container = container,
                            currentMember = currentMember,
                            actions = HomeActions(
                                onOpenEvent = { id -> navController.navigate(EventDetailDestination(id)) },
                                onOpenPlanning = { navController.navigateToTab(TopLevelDestination.Planning) },
                                onOpenHelpRequest = { id -> navController.navigate(HelpRequestDetailDestination(id)) },
                                onOpenWishes = if (features.isOn(Feature.Gifts)) {
                                    { id -> navController.navigate(GiftListDestination(wishesListId(id))) }
                                } else {
                                    null
                                },
                                onOpenList = { id -> navController.navigate(GiftListDestination(id)) },
                                onOpenTrip = { id -> navController.navigate(TripDetailDestination(id)) },
                                onOpenPacking = { id -> navController.navigate(TripPackingDestination(id)) },
                                onOpenWord = { tripId, stepId -> navController.navigate(TripWordDestination(tripId, stepId)) },
                                onOpenAlbumPhoto = { album, photoId ->
                                    when (album.kind) {
                                        MemoryAlbum.Kind.Trip -> navController.navigate(TripPhotosDestination(album.id, photoId))
                                        MemoryAlbum.Kind.Event -> navController.navigate(EventAlbumDestination(album.id, photoId))
                                    }
                                },
                                onOpenPoll = { id -> navController.navigate(PollDetailDestination(id)) },
                                onOpenPolls = { navController.navigate(PollsDestination) { launchSingleTop = true } },
                                onNewPoll = { navController.navigate(PollEditDestination) },
                                onNewTrip = { navController.navigate(TripEditDestination()) },
                                onOpenTrips = if (features.isOn(Feature.Trips)) {
                                    { navController.navigate(TripsDestination) { launchSingleTop = true } }
                                } else {
                                    null
                                },
                            ),
                            onOpenGoodNewsFeed = { navController.navigate(GoodNewsFeedDestination) },
                            onOpenGoodNews = { id -> navController.navigate(GoodNewsDetailDestination(id)) },
                            onPublishGoodNews = { navController.navigate(GoodNewsEditDestination()) },
                            onOpenWeeklyQuestion = { navController.navigate(WeeklyQuestionDestination()) },
                            onOpenMonth = { month -> navController.navigate(MonthSlideshowDestination(month.year, month.monthValue)) },
                            onJoinMe = { navController.navigate(JoinMeDestination) { launchSingleTop = true } },
                            onJoinChallenge = { navController.navigate(GoodNewsEditDestination(forChallenge = true)) },
                            onPrepareChallenge = { month ->
                                navController.navigate(HostChallengeDestination(month.year, month.monthValue)) { launchSingleTop = true }
                            },
                            onOpenRecipe = { id -> navController.navigate(RecipeDetailDestination(id)) },
                            onOpenAll = { navController.navigate(AllFamiliaDestination) { launchSingleTop = true } },
                            allBadge = helpBadge,
                            onOpenChallenges = { navController.navigate(ChallengesDestination) { launchSingleTop = true } },
                            onOpenGoodDeal = { id -> navController.navigate(GoodDealDetailDestination(id)) },
                            onOpenYear = { year -> navController.navigate(YearInReviewDestination(year)) { launchSingleTop = true } },
                            onOpenMyProfile = { navController.navigate(MemberProfileDestination(currentMember.id)) { launchSingleTop = true } },
                            onOpenMember = { id -> navController.navigate(MemberProfileDestination(id)) },
                            onOpenCapsule = { id -> navController.navigate(CapsuleDetailDestination(id)) },
                            onOpenRecap = { navController.navigate(RecapDestination) { launchSingleTop = true } },
                            onOpenDiscover = { module ->
                                when (module) {
                                    DiscoverModule.WeeklyQuestion -> navController.navigate(WeeklyQuestionDestination())
                                    DiscoverModule.HelpRequests -> navController.navigateToTab(TopLevelDestination.HelpRequests)
                                    DiscoverModule.Polls -> navController.navigate(PollsDestination) { launchSingleTop = true }
                                    DiscoverModule.Recipes -> navController.navigate(RecipesDestination())
                                    DiscoverModule.Trips -> navController.navigate(TripsDestination) { launchSingleTop = true }
                                    DiscoverModule.Capsules -> navController.navigate(CapsulesDestination) { launchSingleTop = true }
                                    DiscoverModule.Genealogy -> navController.navigate(GenealogyDestination) { launchSingleTop = true }
                                }
                            },
                        )
                    }
                    composable<AllFamiliaDestination> {
                        // Se souvenir en un seul lieu (récit 71): only with the fil on; off, nothing moves.
                        // No green note about the memories that moved (Kévin le 18/11, 23 h 47).
                        val memoriesInFeed = features.isOnForFamily(Feature.NewsFeed)
                        AllFamiliaScreen(
                            modules = allModules(
                                features,
                                helpBadge,
                                navController,
                                currentMember.id,
                                reviewYear = openableReviewYear(LocalDate.now(), forCard = memoriesInFeed),
                                memoriesInFeed = memoriesInFeed,
                                onEntraideTab = { tab ->
                                    entraideTab = tab
                                    navController.leaveAllFamiliaFor(TopLevelDestination.HelpRequests)
                                },
                            ),
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<RecapDestination> {
                        RecapRoute(
                            container = container,
                            currentMember = currentMember,
                            onBack = { navController.popBackStack() },
                            onOpenGoodNews = { id -> navController.navigate(GoodNewsDetailDestination(id)) },
                            onOpenTrip = { id -> navController.navigate(TripDetailDestination(id)) },
                        )
                    }
                    composable<AlbumsDestination> {
                        AlbumsRoute(
                            container = container,
                            currentMember = currentMember,
                            onBack = { navController.popBackStack() },
                            onOpenAlbum = { album ->
                                when (album.kind) {
                                    AlbumEntry.Kind.Trip -> navController.navigate(TripPhotosDestination(album.id))
                                    AlbumEntry.Kind.Event -> navController.navigate(EventAlbumDestination(album.id))
                                }
                            },
                            onOpenMonth = { month -> navController.navigate(MonthSlideshowDestination(month.year, month.monthValue)) },
                            onOpenTrips = if (features.isOn(Feature.Trips)) ({ navController.navigate(TripsDestination) { launchSingleTop = true } }) else null,
                            onOpenFeedPhotos = { month -> navController.navigate(FeedPhotosDestination(month.year, month.monthValue)) },
                        )
                    }
                    composable<FeedPhotosDestination> { entry ->
                        val route = entry.toRoute<FeedPhotosDestination>()
                        FeedPhotosRoute(
                            container = container,
                            currentMember = currentMember,
                            month = YearMonth.of(route.year, route.month),
                            onBack = { navController.popBackStack() },
                            onOpenNews = { id -> navController.navigate(GoodNewsDetailDestination(id)) },
                        )
                    }
                    composable<ChallengesDestination> {
                        ChallengesRoute(
                            container = container,
                            currentMember = currentMember,
                            onBack = { navController.popBackStack() },
                            onJoinChallenge = { navController.navigate(GoodNewsEditDestination(forChallenge = true)) },
                            onPrepareChallenge = { month ->
                                navController.navigate(HostChallengeDestination(month.year, month.monthValue)) { launchSingleTop = true }
                            },
                            onOpenWeekly = { navController.navigate(WeeklyQuestionDestination()) },
                            onShareGoodNews = if (goodNewsOn) ({ navController.navigate(GoodNewsEditDestination()) }) else null,
                        )
                    }
                    composable<HostChallengeDestination> { entry ->
                        val route = entry.toRoute<HostChallengeDestination>()
                        HostChallengeRoute(
                            container = container,
                            currentMember = currentMember,
                            month = YearMonth.of(route.year, route.month),
                            onClose = { navController.popBackStack() },
                        )
                    }
                    composable<PlanningRoute> {
                        PlanningRoute(
                            container = container,
                            currentMember = currentMember,
                            onAddEvent = { navController.navigate(EventEditDestination()) },
                            onOpenEvent = { id -> navController.navigate(EventDetailDestination(id)) },
                            onOpenTrip = { id -> navController.navigate(TripDetailDestination(id)) },
                            // « + Ajouter » and the card « Sondages » replace the buttons of the bar (récit 75).
                            onAddPoll = if (pollsOn && canAskPoll(currentMember)) ({ navController.navigate(PollEditDestination) }) else null,
                            onOpenPolls = if (pollsOn) ({ navController.navigate(PollsDestination) { launchSingleTop = true } }) else null,
                            polls = if (pollsOn) rememberPollsSummary(container, currentMember.id) else PollsSummary(),
                        )
                    }
                    // --- Sondages (récits 2 bis et 2 ter), from the Planning bar ---
                    composable<PollsDestination> {
                        PollsRoute(
                            container = container,
                            currentMember = currentMember,
                            onNew = { navController.navigate(PollEditDestination) },
                            onOpen = { id -> navController.navigate(PollDetailDestination(id)) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<PollDetailDestination> { entry ->
                        PollDetailRoute(
                            container = container,
                            currentMember = currentMember,
                            pollId = entry.toRoute<PollDetailDestination>().pollId,
                            onOpenEvent = { id -> navController.navigate(EventDetailDestination(id)) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<PollEditDestination> {
                        PollEditRoute(container = container, currentMember = currentMember, onDone = { navController.popBackStack() })
                    }
                    composable<EventPollEditDestination> { entry ->
                        PollEditRoute(
                            container = container,
                            currentMember = currentMember,
                            onDone = { navController.popBackStack() },
                            eventId = entry.toRoute<EventPollEditDestination>().eventId,
                        )
                    }
                    composable<JoinMeDestination> {
                        JoinMeRoute(container = container, currentMember = currentMember, onDone = { navController.popBackStack() })
                    }
                    // Trips (V3): no tab (carte DESIGN-4), they open from the planning.
                    composable<TripsDestination> {
                        TripsRoute(
                            container = container,
                            currentMember = currentMember,
                            globeToken = BuildConfig.MAPBOX_PUBLIC_TOKEN,
                            onOpenTrip = { id -> navController.navigate(TripDetailDestination(id)) },
                            onAddTrip = { navController.navigate(TripEditDestination()) },
                            onOpenCarnet = { navController.navigate(TripCarnetDestination) { launchSingleTop = true } },
                            // A tab of the bar when its module is on: no « Retour » then.
                            onBack = if (TopLevelDestination.Trips in tabs) null else ({ navController.popBackStack() }),
                            onOpenAlbums = if (features.isOn(Feature.Trips) || features.isOn(Feature.EventAlbums)) ({ navController.navigate(AlbumsDestination) { launchSingleTop = true } }) else null,
                            // Étapes, À prévoir, Comptes of the next trip (récit 80): « Comptes » opens over the trip, so back leads to it.
                            onOpenPart = { id, part ->
                                if (part == TripPart.Expenses) {
                                    navController.navigate(TripDetailDestination(id))
                                    navController.navigate(TripExpensesDestination(id))
                                } else if (part == TripPart.ToBring) {
                                    // « À prévoir » opens « Valise » over the trip (récit 98), so back leads to it.
                                    navController.navigate(TripDetailDestination(id))
                                    navController.navigate(TripPackingDestination(id))
                                } else {
                                    navController.navigate(TripDetailDestination(id, part.name))
                                }
                            },
                            expensesOn = features.isOn(Feature.Expenses) && currentMember.profile == MemberProfile.Adult,
                        )
                    }
                    composable<TripDetailDestination> { entry ->
                        val tripId = entry.toRoute<TripDetailDestination>().tripId
                        TripDetailRoute(
                            container = container,
                            currentMember = currentMember,
                            tripId = tripId,
                            openAt = entry.toRoute<TripDetailDestination>().open?.let { name -> TripPart.entries.firstOrNull { it.name == name } },
                            onEditTrip = { navController.navigate(TripEditDestination(tripId)) },
                            onAddStep = { navController.navigate(TripStepEditDestination(tripId)) },
                            onEditStep = { stepId -> navController.navigate(TripStepEditDestination(tripId, stepId)) },
                            onSeePhotos = { navController.navigate(TripPhotosDestination(tripId)) },
                            onOpenPhoto = { photoId -> navController.navigate(TripPhotosDestination(tripId, photoId)) },
                            onOpenMap = { navController.navigate(TripMapDestination(tripId)) },
                            onOpenPacking = { navController.navigate(TripPackingDestination(tripId)) },
                            onOpenDocuments = { navController.navigate(TripDocumentsDestination(tripId)) },
                            onOpenEnrich = { navController.navigate(TripEnrichDestination(tripId)) },
                            onOpenWord = { stepId -> navController.navigate(TripWordDestination(tripId, stepId)) },
                            onOpenPlace = { placeId -> navController.navigate(TripPlaceDestination(tripId, placeId)) },
                            onAddPlace = { stepId, kind -> navController.navigate(TripPlaceAddDestination(tripId, stepId, kind = kind.name)) },
                            onInsertStep = { kind, day -> navController.navigate(TripStepEditDestination(tripId, kind = kind.name, day = day.toString())) },
                            onBack = { navController.popBackStack() },
                            // The accounts are for the adults who take part (récit 16 bis); the trip view checks the participant.
                            onOpenExpenses = if (features.isOn(Feature.Expenses) && currentMember.profile == MemberProfile.Adult) {
                                { navController.navigate(TripExpensesDestination(tripId)) }
                            } else {
                                null
                            },
                        )
                    }
                    composable<TripPackingDestination> { entry ->
                        val route = entry.toRoute<TripPackingDestination>()
                        // Règle 8 (récit 111): the screens of a trip pull up over its map, like its home.
                        TripScreenOverMap(container, route.tripId) {
                            TripPackingRoute(container = container, currentMember = currentMember, tripId = route.tripId, open = route.open, onBack = { navController.popBackStack() })
                        }
                    }
                    composable<TripPhrasesDestination> { entry ->
                        val route = entry.toRoute<TripPhrasesDestination>()
                        TripScreenOverMap(container, route.tripId) {
                            TripPhrasesRoute(container = container, currentMember = currentMember, tripId = route.tripId, onBack = { navController.popBackStack() }, country = route.country)
                        }
                    }
                    composable<TripSafetyDestination> { entry ->
                        val route = entry.toRoute<TripSafetyDestination>()
                        TripScreenOverMap(container, route.tripId) {
                            TripSafetyRoute(container = container, currentMember = currentMember, tripId = route.tripId, onBack = { navController.popBackStack() }, country = route.country)
                        }
                    }
                    composable<TripMoneyDestination> { entry ->
                        val tripId = entry.toRoute<TripMoneyDestination>().tripId
                        TripScreenOverMap(container, tripId) {
                            TripMoneyRoute(container = container, currentMember = currentMember, tripId = tripId, onBack = { navController.popBackStack() })
                        }
                    }
                    // Règle 8: « Enrichir avec une IA » pulls up over the map too, like the other screens of the trip.
                    composable<TripEnrichDestination> { entry ->
                        val tripId = entry.toRoute<TripEnrichDestination>().tripId
                        TripScreenOverMap(container, tripId) {
                            TripEnrichRoute(container = container, currentMember = currentMember, tripId = tripId, onBack = { navController.popBackStack() })
                        }
                    }
                    // Récit 103: the page of an accommodation, a restaurant or a spot of a destination.
                    composable<TripPlaceDestination> { entry ->
                        val route = entry.toRoute<TripPlaceDestination>()
                        TripScreenOverMap(container, route.tripId) {
                            PlaceEditRoute(container, currentMember, route.tripId, route.placeId, onDone = { navController.popBackStack() })
                        }
                    }
                    composable<TripPlaceAddDestination> { entry ->
                        val route = entry.toRoute<TripPlaceAddDestination>()
                        TripScreenOverMap(container, route.tripId, focusId = route.stepId) {
                            PlaceEditRoute(
                                container = container,
                                currentMember = currentMember,
                                tripId = route.tripId,
                                placeId = null,
                                onDone = { navController.popBackStack() },
                                stepId = route.stepId,
                                kind = TripPlaceKind.entries.firstOrNull { it.name == route.kind },
                                presetName = route.name,
                            )
                        }
                    }
                    composable<TripCarnetDestination> {
                        CarnetRoute(container = container, currentMember = currentMember, onBack = { navController.popBackStack() })
                    }
                    composable<TripDocumentsDestination> { entry ->
                        val tripId = entry.toRoute<TripDocumentsDestination>().tripId
                        TripScreenOverMap(container, tripId) {
                            TripDocumentsRoute(container = container, currentMember = currentMember, tripId = tripId, onBack = { navController.popBackStack() })
                        }
                    }
                    composable<TripMapDestination> { entry ->
                        val tripId = entry.toRoute<TripMapDestination>().tripId
                        TripMapRoute(
                            container = container,
                            currentMember = currentMember,
                            tripId = tripId,
                            onOpenStep = { stepId -> navController.navigate(TripWordDestination(tripId, stepId)) },
                            onEditStep = { stepId -> navController.navigate(TripStepEditDestination(tripId, stepId)) },
                            onLaunch = { navController.navigate(TripGlobeDestination(tripId)) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<TripGlobeDestination> { entry ->
                        val tripId = entry.toRoute<TripGlobeDestination>().tripId
                        TripGlobeRoute(container, currentMember, tripId, BuildConfig.MAPBOX_PUBLIC_TOKEN, onBack = { navController.popBackStack() })
                    }
                    composable<TripWordDestination> { entry ->
                        val route = entry.toRoute<TripWordDestination>()
                        TripScreenOverMap(container, route.tripId, focusId = route.stepId) {
                            StepWordRoute(
                                container = container,
                                currentMember = currentMember,
                                tripId = route.tripId,
                                stepId = route.stepId,
                                onEditStep = { navController.navigate(TripStepEditDestination(route.tripId, route.stepId)) },
                                onBack = { navController.popBackStack() },
                                onOpenPhrases = { country -> navController.navigate(TripPhrasesDestination(route.tripId, country)) },
                                onOpenSafety = { country -> navController.navigate(TripSafetyDestination(route.tripId, country)) },
                                onOpenMoney = { navController.navigate(TripMoneyDestination(route.tripId)) },
                            )
                        }
                    }
                    composable<TripExpensesDestination> { entry ->
                        val tripId = entry.toRoute<TripExpensesDestination>().tripId
                        TripScreenOverMap(container, tripId) {
                            ExpensesRoute(
                                container = container,
                                currentMember = currentMember,
                                tripId = tripId,
                                onAdd = { navController.navigate(TripExpenseEditDestination(tripId)) },
                                onEdit = { id -> navController.navigate(TripExpenseEditDestination(tripId, id)) },
                                onHistory = { id -> navController.navigate(TripExpenseHistoryDestination(tripId, id)) },
                                onBack = { navController.popBackStack() },
                                // Règle 42: the currency of the trip is that of its budget (PRODUIT-162).
                                onMoney = { navController.navigate(TripMoneyDestination(tripId)) },
                            )
                        }
                    }
                    composable<TripExpenseEditDestination> { entry ->
                        val route = entry.toRoute<TripExpenseEditDestination>()
                        TripScreenOverMap(container, route.tripId) {
                            ExpenseEditRoute(
                                container = container,
                                currentMember = currentMember,
                                tripId = route.tripId,
                                expenseId = route.expenseId,
                                onHistory = { id -> navController.navigate(TripExpenseHistoryDestination(route.tripId, id)) },
                                onDone = { navController.popBackStack() },
                            )
                        }
                    }
                    composable<TripExpenseHistoryDestination> { entry ->
                        val route = entry.toRoute<TripExpenseHistoryDestination>()
                        TripScreenOverMap(container, route.tripId) {
                            ExpenseHistoryRoute(container = container, tripId = route.tripId, expenseId = route.expenseId, onBack = { navController.popBackStack() })
                        }
                    }
                    composable<TripPhotosDestination> { entry ->
                        val route = entry.toRoute<TripPhotosDestination>()
                        val tripId = route.tripId
                        TripPhotosRoute(
                            container = container,
                            currentMember = currentMember,
                            tripId = tripId,
                            startPhotoId = route.photoId,
                            onBack = { navController.popBackStack() },
                            onMakeBook = if (features.isOn(Feature.PhotoBook) && !viewingAs) {
                                { navController.navigate(PhotoBookDestination(PhotoBookDestination.TRIP, tripId)) }
                            } else {
                                null
                            },
                        )
                    }
                    composable<TripEditDestination> { entry ->
                        val tripId = entry.toRoute<TripEditDestination>().tripId
                        TripEditRoute(
                            container = container,
                            currentMember = currentMember,
                            tripId = tripId,
                            onSaved = { savedId, firstPlace ->
                                if (tripId == null) {
                                    // A new trip opens at once, in place of its form.
                                    navController.navigate(TripDetailDestination(savedId)) {
                                        popUpTo<TripEditDestination> { inclusive = true }
                                    }
                                    // « Ta première étape ? », already holding the destination; back leaves it for later.
                                    if (firstPlace != null) navController.navigate(TripStepEditDestination(savedId, null, firstPlace))
                                } else {
                                    navController.popBackStack()
                                }
                            },
                            // The trip is gone: back past its view too.
                            onDeleted = { navController.popBackStack<TripDetailDestination>(inclusive = true) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<TripStepEditDestination> { entry ->
                        val route = entry.toRoute<TripStepEditDestination>()
                        StepEditRoute(
                            container = container,
                            currentMember = currentMember,
                            tripId = route.tripId,
                            stepId = route.stepId,
                            presetPlace = route.place,
                            presetKind = route.kind?.let { name -> fr.familia.app.feature.trips.step.StepAddKind.entries.firstOrNull { it.name == name } },
                            presetDay = route.day?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
                            onDone = { navController.popBackStack() },
                        )
                    }
                    composable<EventDetailDestination> { entry ->
                        val eventId = entry.toRoute<EventDetailDestination>().eventId
                        EventDetailRoute(
                            container = container,
                            currentMember = currentMember,
                            eventId = eventId,
                            onEdit = { navController.navigate(EventEditDestination(eventId)) },
                            onOpenAlbum = { navController.navigate(EventAlbumDestination(eventId)) },
                            onOpenRecipe = if (features.isOn(Feature.Recipes)) ({ id -> navController.navigate(RecipeDetailDestination(id)) }) else null,
                            onNewPoll = { navController.navigate(EventPollEditDestination(eventId)) },
                            onOpenPoll = { id -> navController.navigate(PollDetailDestination(id)) },
                            onBack = { navController.popBackStack() },
                            // Shown only with the switch on, to an adult who comes or to the author (canOpenExpenses).
                            onOpenExpenses = { navController.navigate(EventExpensesDestination(eventId)) },
                        )
                    }
                    // Les comptes d'un week-end ou d'un repas (PRODUIT-57).
                    composable<EventExpensesDestination> { entry ->
                        val eventId = entry.toRoute<EventExpensesDestination>().eventId
                        EventExpensesRoute(
                            container = container,
                            currentMember = currentMember,
                            eventId = eventId,
                            onAdd = { navController.navigate(EventExpenseEditDestination(eventId)) },
                            onEdit = { id -> navController.navigate(EventExpenseEditDestination(eventId, id)) },
                            onHistory = { id -> navController.navigate(EventExpenseHistoryDestination(eventId, id)) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<EventExpenseEditDestination> { entry ->
                        val route = entry.toRoute<EventExpenseEditDestination>()
                        EventExpenseEditRoute(
                            container = container,
                            currentMember = currentMember,
                            eventId = route.eventId,
                            expenseId = route.expenseId,
                            onHistory = { id -> navController.navigate(EventExpenseHistoryDestination(route.eventId, id)) },
                            onDone = { navController.popBackStack() },
                        )
                    }
                    composable<EventExpenseHistoryDestination> { entry ->
                        val route = entry.toRoute<EventExpenseHistoryDestination>()
                        EventExpenseHistoryRoute(container = container, eventId = route.eventId, expenseId = route.expenseId, onBack = { navController.popBackStack() })
                    }
                    composable<RecipesDestination> { entry ->
                        RecipesRoute(
                            container = container,
                            originMemberId = entry.toRoute<RecipesDestination>().originMemberId,
                            onOpen = { id -> navController.navigate(RecipeDetailDestination(id)) },
                            onAdd = { navController.navigate(RecipeEditDestination()) },
                            onBack = { navController.popBackStack() },
                            onMakeBook = { navController.navigate(RecipeBookDestination) },
                        )
                    }
                    composable<RecipeBookDestination> {
                        RecipeBookRoute(container = container, currentMember = currentMember, onBack = { navController.popBackStack() })
                    }
                    composable<CapsulesDestination> {
                        CapsulesRoute(
                            container = container,
                            currentMember = currentMember,
                            onOpen = { id -> navController.navigate(CapsuleDetailDestination(id)) },
                            onBack = { navController.popBackStack() },
                            onAdd = { navController.navigate(CapsuleEditDestination) },
                        )
                    }
                    composable<WhoIsItDestination> {
                        WhoIsItListRoute(
                            container = container,
                            onOpen = { id -> navController.navigate(WhoIsItPostDestination(id)) },
                            onAdd = { navController.navigate(WhoIsItEditDestination) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<WhoIsItEditDestination> {
                        WhoIsItEditRoute(
                            container = container,
                            currentMember = currentMember,
                            onPublished = { id -> navController.navigate(WhoIsItPostDestination(id)) { popUpTo<WhoIsItEditDestination> { inclusive = true } } },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<WhoIsItPostDestination> { entry ->
                        WhoIsItPostRoute(
                            container = container,
                            currentMember = currentMember,
                            postId = entry.toRoute<WhoIsItPostDestination>().postId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<GenealogyDestination> {
                        GenealogyRoute(
                            container = container,
                            currentMember = currentMember,
                            onBack = { navController.popBackStack() },
                            onOpenProfile = if (souvenirsOn) ({ id -> navController.navigate(MemberProfileDestination(id)) }) else null,
                            onMakePoster = { navController.navigate(TreePosterDestination) },
                        )
                    }
                    composable<PhotoBookDestination> { entry ->
                        PhotoBookRoute(
                            container = container,
                            album = entry.toRoute<PhotoBookDestination>(),
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<TreePosterDestination> {
                        TreePosterRoute(container = container, currentMember = currentMember, onBack = { navController.popBackStack() })
                    }
                    composable<CapsuleEditDestination> {
                        CapsuleEditRoute(
                            container = container,
                            currentMember = currentMember,
                            // The sealed capsule opens in place of the form.
                            onSealed = { id -> navController.navigate(CapsuleDetailDestination(id)) { popUpTo<CapsuleEditDestination> { inclusive = true } } },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<CapsuleDetailDestination> { entry ->
                        CapsuleDetailRoute(
                            container = container,
                            currentMember = currentMember,
                            capsuleId = entry.toRoute<CapsuleDetailDestination>().capsuleId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<RecipeDetailDestination> { entry ->
                        val recipeId = entry.toRoute<RecipeDetailDestination>().recipeId
                        RecipeDetailRoute(
                            container = container,
                            currentMember = currentMember,
                            recipeId = recipeId,
                            onEdit = { navController.navigate(RecipeEditDestination(recipeId)) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<RecipeEditDestination> { entry ->
                        val recipeId = entry.toRoute<RecipeEditDestination>().recipeId
                        RecipeEditRoute(
                            container = container,
                            currentMember = currentMember,
                            recipeId = recipeId,
                            onSaved = { id ->
                                // A new recipe opens at once; a changed one goes back to itself.
                                if (recipeId == null) {
                                    navController.navigate(RecipeDetailDestination(id)) { popUpTo<RecipeEditDestination> { inclusive = true } }
                                } else {
                                    navController.popBackStack()
                                }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<MonthSlideshowDestination> { entry ->
                        val route = entry.toRoute<MonthSlideshowDestination>()
                        MonthSlideshowRoute(
                            container = container,
                            currentMember = currentMember,
                            month = YearMonth.of(route.year, route.month),
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<EventAlbumDestination> { entry ->
                        val route = entry.toRoute<EventAlbumDestination>()
                        val eventId = route.eventId
                        EventAlbumRoute(
                            container = container,
                            currentMember = currentMember,
                            eventId = eventId,
                            startPhotoId = route.photoId,
                            onBack = { navController.popBackStack() },
                            onMakeBook = if (features.isOn(Feature.PhotoBook) && !viewingAs) {
                                { navController.navigate(PhotoBookDestination(PhotoBookDestination.EVENT, eventId)) }
                            } else {
                                null
                            },
                        )
                    }
                    composable<EventEditDestination> { entry ->
                        val destination = entry.toRoute<EventEditDestination>()
                        EventEditRoute(
                            container = container,
                            currentMember = currentMember,
                            eventId = destination.eventId,
                            prefillTitle = destination.title,
                            onDone = { confirmation ->
                                // A deleted event has no detail left: back to the planning.
                                val deleted = confirmation == Confirmation.Deleted
                                if (!deleted || !navController.popBackStack<EventDetailDestination>(inclusive = true)) {
                                    navController.popBackStack()
                                }
                                confirm(confirmation)
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<BirthdaysRoute> {
                        AllBirthdaysScreen(onBack = { navController.popBackStack() }) { BirthdaysRoute(container) }
                    }
                    // --- Listes et cadeaux (V2) ---
                    composable<GiftsRoute> {
                        GiftsRoute(
                            container = container,
                            currentMember = currentMember,
                            onOpenList = { id -> navController.navigate(GiftListDestination(id)) },
                            onOpenMyPage = { navController.navigate(MemberProfileDestination(currentMember.id)) },
                            onOpenSurprisePicker = { navController.navigate(SurprisePickDestination) },
                            onAddWish = { id -> navController.navigate(GiftItemEditDestination(id)) },
                        )
                    }
                    composable<SurprisePickDestination> {
                        SurprisePickRoute(
                            container = container,
                            currentMember = currentMember,
                            onBack = { navController.popBackStack() },
                            onPick = { id ->
                                navController.popBackStack()
                                navController.navigate(GiftListDestination(ideasListId(id)))
                            },
                        )
                    }
                    composable<NotebookDestination> {
                        NotebookRoute(
                            container = container,
                            currentMember = currentMember,
                            onBack = { navController.popBackStack() },
                            onOpenList = { id -> navController.navigate(GiftListDestination(id)) },
                            onAddIdea = { at -> navController.navigate(NotebookIdeaDestination(occasion = at?.occasion?.name, year = at?.year)) },
                            onEditIdea = { id -> navController.navigate(NotebookIdeaDestination(entryId = id)) },
                            onProposeIdea = { id -> navController.navigate(ProposeIdeaDestination(id)) },
                            onAddPurchase = { at -> navController.navigate(NotebookIdeaDestination(occasion = at.occasion?.name, year = at.year, bought = true)) },
                        )
                    }
                    composable<ProposeIdeaDestination> { entry ->
                        ProposeIdeaRoute(
                            container = container,
                            currentMember = currentMember,
                            entryId = entry.toRoute<ProposeIdeaDestination>().entryId,
                            onBack = { navController.popBackStack() },
                            onDone = { listId ->
                                navController.popBackStack()
                                navController.navigate(GiftListDestination(listId))
                            },
                        )
                    }
                    composable<NotebookIdeaDestination> { entry ->
                        val route = entry.toRoute<NotebookIdeaDestination>()
                        NotebookIdeaRoute(
                            container = container,
                            currentMember = currentMember,
                            entryId = route.entryId,
                            startOccasion = NotebookOccasion(GiftOccasion.entries.firstOrNull { it.name == route.occasion }, route.year),
                            onDone = { navController.popBackStack() },
                            startBought = route.bought,
                            startPersonId = route.personId,
                        )
                    }
                    composable<GiftListDestination> { entry ->
                        val listId = entry.toRoute<GiftListDestination>().listId
                        GiftListRoute(
                            container = container,
                            currentMember = currentMember,
                            listId = listId,
                            onEditList = { navController.navigate(GiftListEditDestination(listId)) },
                            onAddItem = { navController.navigate(GiftItemEditDestination(listId)) },
                            onEditItem = { itemId -> navController.navigate(GiftItemEditDestination(listId, itemId)) },
                            onBack = { navController.popBackStack() },
                            onOpenList = { other -> navController.navigate(GiftListDestination(other)) },
                            showClaimOccasion = features.isOn(Feature.WishesReveal),
                            onOpenBeneficiary = if (features.isOn(Feature.MemberCard)) {
                                { id -> navController.navigate(MemberProfileDestination(id)) }
                            } else {
                                null
                            },
                        )
                    }
                    composable<GiftListEditDestination> { entry ->
                        val route = entry.toRoute<GiftListEditDestination>()
                        GiftListEditRoute(
                            container = container,
                            currentMember = currentMember,
                            listId = route.listId,
                            forMemberId = route.forMemberId,
                            nearestOccasion = route.nearestOccasion,
                            onDone = { deleted ->
                                // A deleted list closes its screen too.
                                if (deleted) navController.popBackStack(GiftsRoute, inclusive = false) else navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<GiftItemEditDestination> { entry ->
                        val route = entry.toRoute<GiftItemEditDestination>()
                        GiftItemEditRoute(
                            container = container,
                            currentMember = currentMember,
                            listId = route.listId,
                            itemId = route.itemId,
                            shared = route.sharedUrl?.let { url -> SharedWish(url, route.sharedTitle.orEmpty()) },
                            fromShare = route.fromShare,
                            onDone = { navController.popBackStack() },
                        )
                    }
                    // --- Coups de main ---
                    composable<HelpRequestsRoute> {
                        // Récit 45: the « Bons plans » beside the coups de main, behind their switch.
                        val goodDealsOn = LocalFeatureFlags.current.isOn(Feature.GoodDeals)
                        val lendingOn = LocalFeatureFlags.current.isOn(Feature.Lending)
                        EntraideTabs(
                            goodDealsOn = goodDealsOn,
                            help = {
                                HelpRequestsRoute(
                                    container = container,
                                    currentMember = currentMember,
                                    onAsk = { navController.navigate(HelpRequestEditDestination()) },
                                    onOpenRequest = { id -> navController.navigate(HelpRequestDetailDestination(id)) },
                                    showTitle = !goodDealsOn && !lendingOn,
                                )
                            },
                            goodDeals = {
                                GoodDealsRoute(
                                    container = container,
                                    currentMember = currentMember,
                                    onPublish = { kind -> navController.navigate(GoodDealEditDestination(kind = kind.firestoreValue)) },
                                    onOpenDeal = { id -> navController.navigate(GoodDealDetailDestination(id)) },
                                )
                            },
                            showGoodDeals = entraideOnGoodDeals,
                            open = entraideTab,
                            onOpened = { entraideTab = null },
                            // Récit 51: « On se prête », behind its switch. The form and the sheet come next.
                            lendingOn = lendingOn,
                            lending = {
                                LendItemsRoute(
                                    container = container,
                                    currentMember = currentMember,
                                    onAdd = { navController.navigate(LendItemEditDestination()) },
                                    onOpen = { id -> navController.navigate(LendItemDetailDestination(id)) },
                                )
                            },
                        )
                    }
                    composable<GoodDealDetailDestination> { entry ->
                        val dealId = entry.toRoute<GoodDealDetailDestination>().dealId
                        GoodDealDetailRoute(
                            container = container,
                            currentMember = currentMember,
                            dealId = dealId,
                            onEdit = { navController.navigate(GoodDealEditDestination(dealId)) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<GoodDealEditDestination> { entry ->
                        val destination = entry.toRoute<GoodDealEditDestination>()
                        GoodDealEditRoute(
                            container = container,
                            currentMember = currentMember,
                            dealId = destination.dealId,
                            kind = GoodDealKind.fromFirestore(destination.kind) ?: GoodDealKind.Deal,
                            onDone = { navController.popBackStack() },
                        )
                    }
                    composable<LendItemDetailDestination> { entry ->
                        val itemId = entry.toRoute<LendItemDetailDestination>().itemId
                        LendItemDetailRoute(
                            container = container,
                            currentMember = currentMember,
                            itemId = itemId,
                            onEdit = { navController.navigate(LendItemEditDestination(itemId)) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<LendItemEditDestination> { entry ->
                        LendItemEditRoute(
                            container = container,
                            currentMember = currentMember,
                            itemId = entry.toRoute<LendItemEditDestination>().itemId,
                            onDone = { navController.popBackStack() },
                        )
                    }
                    composable<HelpRequestDetailDestination> { entry ->
                        val requestId = entry.toRoute<HelpRequestDetailDestination>().requestId
                        HelpRequestDetailRoute(
                            container = container,
                            currentMember = currentMember,
                            requestId = requestId,
                            onEdit = { navController.navigate(HelpRequestEditDestination(requestId = requestId)) },
                            onDuplicate = { navController.navigate(HelpRequestEditDestination(duplicateOf = requestId)) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<HelpRequestEditDestination> { entry ->
                        val destination = entry.toRoute<HelpRequestEditDestination>()
                        HelpRequestEditRoute(
                            container = container,
                            currentMember = currentMember,
                            requestId = destination.requestId,
                            duplicateOf = destination.duplicateOf,
                            onDone = { navController.popBackStack() },
                        )
                    }
                    composable<GoodNewsFeedDestination> {
                        GoodNewsFeedRoute(
                            container = container,
                            currentMember = currentMember,
                            onPublish = { navController.navigate(GoodNewsEditDestination()) },
                            onOpen = { id -> navController.navigate(GoodNewsDetailDestination(id)) },
                            onEdit = { id -> navController.navigate(GoodNewsEditDestination(id)) },
                            onBack = { navController.popBackStack() },
                            onOpenYear = { year -> navController.navigate(YearInReviewDestination(year)) { launchSingleTop = true } },
                        )
                    }
                    composable<YearInReviewDestination> { entry ->
                        YearInReviewRoute(
                            container = container,
                            currentMember = currentMember,
                            year = entry.toRoute<YearInReviewDestination>().year,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<GoodNewsDetailDestination> { entry ->
                        val postId = entry.toRoute<GoodNewsDetailDestination>().postId
                        GoodNewsDetailRoute(
                            container = container,
                            currentMember = currentMember,
                            postId = postId,
                            onEdit = { navController.navigate(GoodNewsEditDestination(postId)) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<WeeklyQuestionDestination> { entry ->
                        WeeklyQuestionRoute(
                            container = container,
                            currentMember = currentMember,
                            questionId = entry.toRoute<WeeklyQuestionDestination>().questionId,
                            onBack = { navController.popBackStack() },
                            onArchive = { navController.navigate(WeeklyArchiveDestination) { launchSingleTop = true } },
                            onShareGoodNews = if (goodNewsOn) ({ navController.navigate(GoodNewsEditDestination()) }) else null,
                        )
                    }
                    composable<MemberProfileDestination> { entry ->
                        val memberId = entry.toRoute<MemberProfileDestination>().memberId
                        MemberProfileRoute(
                            container = container,
                            currentMember = currentMember,
                            memberId = memberId,
                            onBack = { navController.popBackStack() },
                            onEdit = { navController.navigate(MemberEditDestination(memberId)) },
                            onOpenRecipes = if (features.isOn(Feature.Recipes)) ({ navController.navigate(RecipesDestination(memberId)) }) else null,
                            giftActions = ProfileGiftActions(
                                onOpenList = { id -> navController.navigate(GiftListDestination(id)) },
                                onOpenNotebook = { navController.navigate(NotebookDestination) },
                                onAddIdea = { id -> navController.navigate(NotebookIdeaDestination(personId = id)) },
                                onEditIdea = { id -> navController.navigate(NotebookIdeaDestination(entryId = id)) },
                                onProposeIdea = { id -> navController.navigate(ProposeIdeaDestination(id)) },
                            ),
                            onOpenMember = { id -> navController.navigate(MemberProfileDestination(id)) },
                            onOpenEvent = { id -> navController.navigate(EventDetailDestination(id)) },
                        )
                    }
                    composable<WeeklyArchiveDestination> {
                        WeeklyArchiveRoute(
                            container = container,
                            onOpen = { id -> navController.navigate(WeeklyQuestionDestination(id)) },
                            onBack = { navController.popBackStack() },
                            onShareGoodNews = if (goodNewsOn) ({ navController.navigate(GoodNewsEditDestination()) }) else null,
                        )
                    }
                    composable<GoodNewsEditDestination> { entry ->
                        GoodNewsEditRoute(
                            container = container,
                            currentMember = currentMember,
                            postId = entry.toRoute<GoodNewsEditDestination>().postId,
                            forChallenge = entry.toRoute<GoodNewsEditDestination>().forChallenge,
                            onDone = { navController.popBackStack() },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<MembersRoute> {
                        MembersRoute(
                            container = container,
                            currentMember = currentMember,
                            onEditMember = { id -> navController.navigate(MemberEditDestination(id)) },
                            onOpenProfile = if (souvenirsOn) ({ id -> navController.navigate(MemberProfileDestination(id)) }) else null,
                            onOpenRecipes = if (features.isOn(Feature.Recipes)) ({ navController.navigate(RecipesDestination()) }) else null,
                            onOpenCapsules = if (capsulesOn) ({ navController.navigate(CapsulesDestination) }) else null,
                            onOpenGenealogy = if (features.isOn(Feature.Genealogy)) ({ navController.navigate(GenealogyDestination) }) else null,
                            onOpenWhoIsIt = if (features.isOn(Feature.WhoIsIt)) ({ navController.navigate(WhoIsItDestination) }) else null,
                            onOpenWishes = if (features.isOn(Feature.Gifts)) {
                                { id -> navController.navigate(GiftListDestination(wishesListId(id))) }
                            } else {
                                null
                            },
                        )
                    }
                    composable<WhatsNewDestination> {
                        WhatsNewRoute(
                            container = container,
                            currentMember = currentMember,
                            installedVersion = BuildConfig.VERSION_CODE.toLong(),
                            onOpen = navController::openWhatsNew,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<HelpDestination> {
                        HelpRoute(
                            container = container,
                            currentMember = currentMember,
                            onReplayOnboarding = { showOnboarding = true },
                            onBack = { navController.popBackStack() },
                            onOpenFeedback = { navController.navigate(FeedbackFormDestination(FeedbackOrigin.Help.key)) },
                            onOpenWhatsNew = { navController.navigate(WhatsNewDestination) { launchSingleTop = true } },
                        )
                    }
                    composable<UpdateCheckDestination> {
                        UpdateCheckRoute(container = container, onBack = { navController.popBackStack() })
                    }
                    composable<CalendarFeedDestination> {
                        CalendarFeedRoute(container = container, memberId = currentMember.id, onBack = { navController.popBackStack() })
                    }
                    composable<AdministrationDestination> {
                        val nextEvents = remember(container) {
                            container?.eventRepository?.observeUpcoming(familyToday())
                                ?.map { nextEventId(it, LocalDateTime.now(FAMILY_ZONE)) }
                                ?.catch { emit(null) }
                                ?: flowOf(null)
                        }
                        val nextEventId by nextEvents.collectAsStateWithLifecycle(initialValue = null)
                        AdministrationRoute(
                            container = container,
                            currentMember = currentMember,
                            onOpenExport = { navController.navigate(ExportDestination) },
                            onBack = { navController.popBackStack() },
                            onOpenUsage = { navController.navigate(UsageDestination) },
                            onOpenFeedback = { navController.navigate(FeedbackListDestination) },
                            onOpenCrashes = { navController.navigate(CrashesDestination) { launchSingleTop = true } },
                            onOpenAnnouncements = { navController.navigate(AnnouncementsDestination) { launchSingleTop = true } },
                            seeWhere = { feature -> navController.seeWhere(feature, currentMember.id, nextEventId) },
                        )
                    }
                    composable<AnnouncementsDestination> {
                        AnnouncementsRoute(
                            container = container,
                            currentMember = currentMember,
                            onBack = { navController.popBackStack() },
                            onOpen = { id -> navController.navigate(AnnouncementDestination(id)) },
                        )
                    }
                    composable<AnnouncementDestination> { entry ->
                        AnnouncementRoute(
                            container = container,
                            announcementId = entry.toRoute<AnnouncementDestination>().announcementId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<CrashesDestination> {
                        CrashesRoute(container = container, onBack = { navController.popBackStack() })
                    }
                    composable<FeedbackListDestination> {
                        FeedbackListRoute(container = container, onBack = { navController.popBackStack() })
                    }
                    composable<FeedbackFormDestination> { entry ->
                        val origin = FeedbackOrigin.fromKey(entry.toRoute<FeedbackFormDestination>().origin)
                        FeedbackFormRoute(
                            container = container,
                            currentMember = currentMember,
                            origin = origin,
                            onBack = { navController.popBackStack() },
                            onOpenMine = { navController.navigate(MyFeedbackDestination) { launchSingleTop = true } },
                        )
                    }
                    composable<MyFeedbackDestination> {
                        MyFeedbackRoute(container = container, currentMember = currentMember, onBack = { navController.popBackStack() })
                    }
                    composable<UsageDestination> {
                        UsageRoute(container = container, currentMember = currentMember, onBack = { navController.popBackStack() })
                    }
                    composable<ExportDestination> {
                        ExportRoute(container = container, memberId = currentMember.id, onBack = { navController.popBackStack() })
                    }
                    composable<AppearanceDestination> {
                        var chosenIcon by remember { mutableStateOf(iconSwitcher.current) }
                        AppearanceScreen(
                            current = chosenTheme,
                            onChoose = { theme ->
                                chosenTheme = theme
                                themePrefs.theme = theme
                            },
                            titleFont = chosenTitleFont,
                            onChooseTitleFont = { font ->
                                chosenTitleFont = font
                                themePrefs.titleFont = font
                            },
                            bodyFont = chosenBodyFont,
                            onChooseBodyFont = { font ->
                                chosenBodyFont = font
                                themePrefs.bodyFont = font
                            },
                            onBack = { navController.popBackStack() },
                            icon = chosenIcon,
                            onChooseIcon = { icon ->
                                chosenIcon = icon
                                iconSwitcher.choose(icon)
                            },
                            opening = openingStyle,
                            onChooseOpening = { style ->
                                openingStyle = style
                                welcome.style = style
                            },
                        )
                    }
                    composable<NotificationSettingsDestination> {
                        NotificationSettingsRoute(
                            container = container,
                            currentMember = currentMember,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<MemberEditDestination> { entry ->
                        MemberEditRoute(
                            container = container,
                            currentMember = currentMember,
                            memberId = entry.toRoute<MemberEditDestination>().memberId,
                            onDone = { confirmation ->
                                navController.popBackStack()
                                confirm(confirmation)
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                // Variante D (DESIGN-47), at the place of A (PRODUIT-129): floating over the screen, never pushing it; on the Accueil
                // just above « Tout Familia » (56 dp, 12 dp under it), elsewhere just above the tabs.
                val sheetUpdate = update?.takeIf { onTab && shouldShowUpdateSheet(it, updateSheetSeen) }
                if (sheetUpdate != null) {
                    val seen = {
                        updatePrefs.sheetSeen = sheetUpdate.versionName
                        updateSheetSeen = sheetUpdate.versionName
                    }
                    UpdateSheet(
                        onUpdate = { seen(); updater.start(sheetUpdate) },
                        onLater = seen,
                    )
                }
                val shownUpdate = update?.takeIf { onTab && !updateDismissed && sheetUpdate == null }
                if (shownUpdate != null) {
                    UpdateBanner(
                        onUpdate = { updater.start(shownUpdate) },
                        onLater = { updateDismissed = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    )
                }
                TapGuardWhileNavigating(navController)
                }
            }
            }
        }
    }
    }
}

/** 26 sp instead of the 22 sp of titleLarge. */
private const val TAB_TITLE_SCALE = 26f / 22f

/** Title bar of a tab, with its « ? » where the spec has one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabTopBar(
    tab: TopLevelDestination,
    member: Member,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onOpenTrips: (() -> Unit)? = null,
    onGoodNews: (() -> Unit)? = null,
    onWeeklyQuestion: (() -> Unit)? = null,
    /** My photo, last at the right, opens the sheet of my account and the settings (DESIGN-12). */
    onAccount: (() -> Unit)? = null,
    /** « Bonjour Camille » in place of the tab name, on the R1 bis Accueil. */
    greeting: String? = null,
    /** The « + » of the R1 bis Accueil, before « ? ». The Planning has its « + Ajouter » at the bottom (récit 75). */
    onAdd: (() -> Unit)? = null,
    /** The round « Retour » of a tab opened as a page: the Planning (récit 82). */
    onBack: (() -> Unit)? = null,
) {
    TopAppBar(
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (onBack != null) {
                Surface(shape = CircleShape, color = LocalClayColors.current.card, shadowElevation = 2.dp, modifier = Modifier.padding(start = 4.dp)) {
                    BackButton(onClick = onBack)
                }
            }
        },
        // Transparent at rest, so the theme's pattern shows behind the title; the surface color once
        // the content scrolls under it.
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = FamiliaClay.style.titleColor ?: MaterialTheme.colorScheme.onSurface,
        ),
        // The Entraide tab keeps the full name « Coups de main » as its title (DESIGN-4).
        title = {
            // 26 sp for the titles of the 5 tabs (DESIGN-11), scaled like titleLarge for a wide font.
            val base = MaterialTheme.typography.titleLarge
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The olive tree mascot before « Bonjour Camille » (DESIGN-58): colored fruits, decorative.
                if (greeting != null) {
                    OliveMascot(56.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    greeting ?: stringResource(if (tab == TopLevelDestination.HelpRequests) R.string.helprequest_screen_title else tab.labelFor(member)),
                    style = base.copy(fontSize = base.fontSize * TAB_TITLE_SCALE, lineHeight = base.lineHeight * TAB_TITLE_SCALE),
                )
            }
        },
        actions = {
            when (tab) {
                TopLevelDestination.Planning -> {
                    if (onGoodNews != null) {
                        IconButton(onClick = onGoodNews, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Outlined.Celebration, contentDescription = stringResource(R.string.goodnews_open_feed))
                        }
                    }
                    if (onWeeklyQuestion != null) {
                        IconButton(onClick = onWeeklyQuestion, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Outlined.QuestionAnswer, contentDescription = stringResource(R.string.weekly_open))
                        }
                    }
                    // Trips have no tab (carte DESIGN-4): from here only while the Accueil is off.
                    if (onOpenTrips != null) {
                        TextButton(onClick = onOpenTrips, modifier = Modifier.heightIn(min = 56.dp)) {
                            Icon(Icons.Outlined.CardTravel, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.trips_title))
                        }
                    }
                    HelpButton(ScreenHelp.Planning)
                }
                TopLevelDestination.Home -> {
                    onAdd?.let { add ->
                        IconButton(onClick = add, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.home_r1_add))
                        }
                    }
                    HelpButton(ScreenHelp.Home)
                }
                TopLevelDestination.Members -> HelpButton(ScreenHelp.Family)
                TopLevelDestination.HelpRequests -> HelpButton(ScreenHelp.HelpRequests)
                TopLevelDestination.Trips -> HelpButton(ScreenHelp.Trips)
                TopLevelDestination.Gifts -> HelpButton(ScreenHelp.Gifts)
            }
            onAccount?.let { AccountButton(member, onClick = it) }
        },
    )
}

/**
 * A tab name always on one line: with a large font setting, a wide theme font or a narrow phone,
 * « Planning » used to break into « Plannin / g ». The text shrinks a little until it fits.
 */
@Composable
private fun TabLabel(text: String) {
    val base = MaterialTheme.typography.labelMedium
    var size by remember(text, base) { mutableStateOf(base.fontSize) }
    Text(
        text = text,
        style = base.copy(fontSize = size),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow && size.value > MIN_TAB_LABEL_SP) size = (size.value * 0.9f).sp
        },
    )
}

private const val MIN_TAB_LABEL_SP = 9f


/**
 * The modules of « Tout Familia » (R1 bis, §3), each only while it is on for me. Comptes stays out
 * for the 21 (DESIGN); the Entraide badge is the one of its tab.
 */
private fun allModules(
    features: FeatureFlags,
    helpBadge: Int,
    navController: NavHostController,
    meId: String,
    /** « Notre année », findable all year long under « Se souvenir » (PRD rév. 259); null: not yet. */
    reviewYear: Int? = null,
    /** Bons plans, On se prête: Entraide, turned to that sub-tab (PRODUIT-118). */
    onEntraideTab: (EntraideTab) -> Unit = {},
    /**
     * Se souvenir en un seul lieu (récit 71), with the fil on: « Se souvenir » keeps Le fil, Albums,
     * Arbre and Capsule (to write one); « Qui est-ce ? » goes to « Se faire plaisir »; Souvenirs and
     * Bonnes nouvelles are cards of the fil; « Notre année » only from 26/12 to 10/01.
     */
    memoriesInFeed: Boolean = false,
): List<AllModule> = buildList {
    fun module(on: Boolean, key: String, label: Int, icon: ImageVector, family: ModuleFamily, badge: Int = 0, open: () -> Unit) {
        if (on) this.add(AllModule(key, label, icon, family, badge, open))
    }
    module(true, "planning", R.string.all_planning, Icons.Outlined.CalendarMonth, ModuleFamily.Organise) {
        navController.leaveAllFamiliaFor(TopLevelDestination.Planning)
    }
    module(features.isOn(Feature.HelpRequests), "help", R.string.all_help, Icons.Outlined.VolunteerActivism, ModuleFamily.Organise, helpBadge) {
        navController.leaveAllFamiliaFor(TopLevelDestination.HelpRequests)
    }
    // Each sub-tab of Entraide also by itself (PRODUIT-118), while its module and Entraide are on.
    val entraideOn = features.isOn(Feature.HelpRequests)
    module(entraideOn && features.isOn(Feature.GoodDeals), "goodDeals", R.string.entraide_tab_good_deals, Icons.Outlined.LocalOffer, ModuleFamily.Organise) {
        onEntraideTab(EntraideTab.GoodDeals)
    }
    module(entraideOn && features.isOn(Feature.Lending), "lending", R.string.entraide_tab_lending, Icons.Outlined.Category, ModuleFamily.Organise) {
        onEntraideTab(EntraideTab.Lending)
    }
    module(features.isOn(Feature.Polls), "polls", R.string.all_polls, Icons.Outlined.Poll, ModuleFamily.Organise) {
        navController.navigate(PollsDestination) { launchSingleTop = true }
    }
    module(features.isOn(Feature.Places) && features.isOn(Feature.Home), "where", R.string.all_where, Icons.Outlined.Place, ModuleFamily.Organise) {
        navController.navigate(JoinMeDestination) { launchSingleTop = true }
    }
    module(features.isOn(Feature.Gifts), "gifts", R.string.all_gifts, Icons.Outlined.CardGiftcard, ModuleFamily.Treat) {
        navController.leaveAllFamiliaFor(TopLevelDestination.Gifts)
    }
    module(features.isOn(Feature.Trips), "trips", R.string.all_trips, Icons.Outlined.CardTravel, ModuleFamily.Treat) {
        navController.leaveAllFamiliaFor(TopLevelDestination.Trips)
    }
    module(features.isOn(Feature.Recipes), "recipes", R.string.all_recipes, Icons.Outlined.MenuBook, ModuleFamily.Treat) {
        navController.navigate(RecipesDestination()) { launchSingleTop = true }
    }
    module(features.isOn(Feature.Tree), "challenges", R.string.all_challenges, Icons.Outlined.EmojiEvents, ModuleFamily.Treat) {
        navController.navigate(ChallengesDestination) { launchSingleTop = true }
    }
    module(memoriesInFeed, "feed", R.string.all_feed, Icons.Outlined.DynamicFeed, ModuleFamily.Remember) {
        FeedOpenRequest.ask()
        // Back down to the Accueil under Tout Familia: going to the tab would bring Tout Familia back,
        // saved with the Accueil, and nothing seemed to happen (retour d'essai, 20/11).
        if (!navController.popBackStack(HomeRoute, inclusive = false)) navController.leaveAllFamiliaFor(TopLevelDestination.Home)
    }
    module(features.isOn(Feature.Trips) || features.isOn(Feature.EventAlbums), "albums", R.string.all_albums, Icons.Outlined.PhotoLibrary, ModuleFamily.Remember) {
        navController.navigate(AlbumsDestination) { launchSingleTop = true }
    }
    module(features.isOn(Feature.Genealogy), "tree", R.string.all_tree, Icons.Outlined.AccountTree, ModuleFamily.Remember) {
        navController.navigate(GenealogyDestination) { launchSingleTop = true }
    }
    module(features.isOn(Feature.Capsules), "capsule", R.string.all_capsule, Icons.Outlined.HourglassTop, ModuleFamily.Remember) {
        navController.navigate(CapsulesDestination) { launchSingleTop = true }
    }
    // « Qui est-ce ? » was only in « Nos trésors » of the Famille tab, gone on 18/11 (Kévin).
    val whoIsItFamily = if (memoriesInFeed) ModuleFamily.Treat else ModuleFamily.Remember
    module(features.isOn(Feature.WhoIsIt), "whoIsIt", R.string.all_who_is_it, Icons.Outlined.PhotoAlbum, whoIsItFamily) {
        navController.navigate(WhoIsItDestination) { launchSingleTop = true }
    }
    // My own profile, whose souvenirs are never empty (DESIGN, 17/11).
    module(!memoriesInFeed && features.isOn(Feature.Souvenirs), "souvenirs", R.string.all_souvenirs, Icons.Outlined.AutoStories, ModuleFamily.Remember) {
        navController.navigate(MemberProfileDestination(meId)) { launchSingleTop = true }
    }
    module(!memoriesInFeed && features.isOn(Feature.GoodNews), "goodNews", R.string.all_good_news, Icons.Outlined.Celebration, ModuleFamily.Remember) {
        navController.navigate(GoodNewsFeedDestination) { launchSingleTop = true }
    }
    reviewYear?.let { year ->
        module(true, "yearInReview", R.string.all_year_in_review, Icons.Outlined.AutoAwesome, ModuleFamily.Remember) {
            navController.navigate(YearInReviewDestination(year)) { launchSingleTop = true }
        }
    }
}

/**
 * « Voir où » under a switch of Administration (PRODUIT, 17/11): the screen where [feature] shows,
 * or null for a module with no screen of its own (a notification, a share). A module shown on an
 * event opens [nextEventId], the next event to come, or the Planning when there is none.
 */
private fun NavHostController.seeWhere(feature: Feature, meId: String, nextEventId: String?): (() -> Unit)? {
    val lastMonth = YearMonth.now().minusMonths(1)
    return when (feature) {
        Feature.Home, Feature.YearAgo, Feature.BirthdayWishes, Feature.Discover, Feature.DailyNudge -> ({ navigateToTab(TopLevelDestination.Home) })
        Feature.Gifts, Feature.GiftNotebook, Feature.WishesReveal, Feature.GiftBudget -> ({ navigateToTab(TopLevelDestination.Gifts) })
        Feature.HelpRequests, Feature.GoodDeals, Feature.Lending -> ({ navigateToTab(TopLevelDestination.HelpRequests) })
        Feature.Trips, Feature.Expenses, Feature.PackingList -> ({ navigate(TripsDestination) { launchSingleTop = true } })
        Feature.GoodNews, Feature.LittleOnes -> ({ navigate(GoodNewsFeedDestination) { launchSingleTop = true } })
        Feature.Themes -> ({ navigate(AppearanceDestination) { launchSingleTop = true } })
        Feature.Usage -> ({ navigate(UsageDestination) { launchSingleTop = true } })
        Feature.WeeklyQuestion -> ({ navigate(WeeklyQuestionDestination()) { launchSingleTop = true } })
        Feature.EventAlbums, Feature.PhotoBook -> ({ navigate(AlbumsDestination) { launchSingleTop = true } })
        Feature.Bring, Feature.Comments, Feature.Carpool, Feature.RecurringEvents, Feature.MyReminders -> when (nextEventId) {
            null -> ({ navigateToTab(TopLevelDestination.Planning) })
            else -> ({ navigate(EventDetailDestination(nextEventId)) { launchSingleTop = true } })
        }
        Feature.Calendar, Feature.MorePhotos -> ({ navigateToTab(TopLevelDestination.Planning) })
        Feature.Souvenirs, Feature.MemberCard -> ({ navigate(MemberProfileDestination(meId)) { launchSingleTop = true } })
        Feature.Tree -> ({ navigate(ChallengesDestination) { launchSingleTop = true } })
        Feature.MonthSlideshow -> ({ navigate(MonthSlideshowDestination(lastMonth.year, lastMonth.monthValue)) { launchSingleTop = true } })
        Feature.Polls -> ({ navigate(PollsDestination) { launchSingleTop = true } })
        Feature.Recipes -> ({ navigate(RecipesDestination()) { launchSingleTop = true } })
        Feature.Capsules -> ({ navigate(CapsulesDestination) { launchSingleTop = true } })
        Feature.Genealogy, Feature.TreePoster, Feature.ArbreDeVie -> ({ navigate(GenealogyDestination) { launchSingleTop = true } })
        Feature.Places -> ({ navigate(JoinMeDestination) { launchSingleTop = true } })
        Feature.WhoIsIt -> ({ navigate(WhoIsItDestination) { launchSingleTop = true } })
        Feature.RecipeBook -> ({ navigate(RecipeBookDestination) { launchSingleTop = true } })
        Feature.CalendarFeed -> ({ navigate(CalendarFeedDestination) { launchSingleTop = true } })
        else -> null
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true

/** « Essayer » on a card of « Quoi de neuf ? ». */
private fun NavHostController.openWhatsNew(target: WhatsNewTarget) {
    when (target) {
        WhatsNewTarget.Home -> navigateToTab(TopLevelDestination.Home)
        WhatsNewTarget.Planning -> navigateToTab(TopLevelDestination.Planning)
        WhatsNewTarget.Gifts -> navigateToTab(TopLevelDestination.Gifts)
        WhatsNewTarget.HelpRequests -> navigateToTab(TopLevelDestination.HelpRequests)
        WhatsNewTarget.Members -> navigateToTab(TopLevelDestination.Members)
        WhatsNewTarget.Recipes -> navigate(RecipesDestination())
        WhatsNewTarget.Trips -> navigate(TripsDestination) { launchSingleTop = true }
        WhatsNewTarget.Polls -> navigate(PollsDestination) { launchSingleTop = true }
        WhatsNewTarget.Capsules -> navigate(CapsulesDestination) { launchSingleTop = true }
        WhatsNewTarget.Genealogy -> navigate(GenealogyDestination) { launchSingleTop = true }
        WhatsNewTarget.WeeklyQuestion -> navigate(WeeklyQuestionDestination())
        WhatsNewTarget.GoodNews -> navigate(GoodNewsFeedDestination) { launchSingleTop = true }
        WhatsNewTarget.JoinMe -> navigate(JoinMeDestination) { launchSingleTop = true }
        WhatsNewTarget.Appearance -> navigate(AppearanceDestination) { launchSingleTop = true }
        WhatsNewTarget.WhoIsIt -> navigate(WhoIsItDestination) { launchSingleTop = true }
    }
}

/**
 * From Tout Familia to a tab: Tout Familia closes first, so that it is not saved with the tab it was
 * opened from and does not come back over it (retour d'essai, 20/11: Famille brought it back).
 */
private fun NavHostController.leaveAllFamiliaFor(destination: TopLevelDestination) {
    popBackStack<AllFamiliaDestination>(inclusive = true)
    navigateToTab(destination)
}

private fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        // Keep one copy of each tab and remember where the user was in it.
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
