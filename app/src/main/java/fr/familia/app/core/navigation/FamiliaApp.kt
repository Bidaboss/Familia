package fr.familia.app.core.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import fr.familia.app.BuildConfig
import fr.familia.app.R
import fr.familia.app.feature.update.UpdateBanner
import fr.familia.app.feature.update.availableUpdate
import fr.familia.app.feature.update.InAppUpdateDialogs
import fr.familia.app.feature.update.LocalInAppUpdater
import fr.familia.app.feature.update.rememberInAppUpdater
import fr.familia.app.core.model.Feature
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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.HelpRoute
import fr.familia.app.feature.goodnews.detail.GoodNewsDetailRoute
import fr.familia.app.feature.goodnews.edit.GoodNewsEditRoute
import fr.familia.app.feature.goodnews.feed.GoodNewsFeedRoute
import fr.familia.app.feature.members.profile.MemberProfileRoute
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
import fr.familia.app.core.model.NotebookOccasion
import fr.familia.app.core.model.wishesListId
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
import fr.familia.app.feature.gifts.share.ShareTargetSwitch
import fr.familia.app.feature.usage.UsageRoute
import fr.familia.app.feature.feedback.FeedbackFormDestination
import fr.familia.app.feature.feedback.FeedbackFormRoute
import fr.familia.app.feature.feedback.FeedbackListDestination
import fr.familia.app.feature.feedback.FeedbackListRoute
import fr.familia.app.feature.feedback.MyFeedbackDestination
import fr.familia.app.feature.feedback.MyFeedbackRoute
import fr.familia.app.core.model.FeedbackOrigin
import fr.familia.app.feature.tree.HostChallengeRoute
import fr.familia.app.feature.settings.appearance.AppearanceScreen
import fr.familia.app.feature.settings.appearance.ThemePreferences
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.FamiliaThemeId
import fr.familia.app.core.theme.themeBackground
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.graphics.Color
import fr.familia.app.feature.home.HomeRoute as HomeTab
import fr.familia.app.feature.settings.NotificationSettingsRoute
import fr.familia.app.feature.export.ExportRoute
import fr.familia.app.feature.members.admin.AdministrationRoute
import fr.familia.app.feature.onboarding.OnboardingPreferences
import fr.familia.app.feature.onboarding.OnboardingRoute
import kotlinx.coroutines.CancellationException
import fr.familia.app.feature.gifts.AllBirthdaysScreen
import fr.familia.app.feature.gifts.GiftsRoute
import fr.familia.app.feature.gifts.detail.GiftListRoute
import fr.familia.app.feature.gifts.received.ReceivedGiftsRoute
import fr.familia.app.feature.gifts.edit.GiftListEditRoute
import fr.familia.app.feature.gifts.item.GiftItemEditRoute
import fr.familia.app.feature.gifts.notebook.NotebookIdeaRoute
import fr.familia.app.feature.gifts.notebook.NotebookRoute
import fr.familia.app.feature.gifts.notebook.ProposeIdeaRoute
import fr.familia.app.feature.events.answerableMembers
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.feature.helprequests.badgeCount
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
import fr.familia.app.feature.recipes.RecipeDetailDestination
import fr.familia.app.feature.recipes.RecipeDetailRoute
import fr.familia.app.feature.recipes.RecipeEditDestination
import fr.familia.app.feature.recipes.RecipeEditRoute
import fr.familia.app.feature.recipes.RecipesDestination
import fr.familia.app.feature.recipes.RecipesRoute
import fr.familia.app.feature.trips.TripDetailDestination
import fr.familia.app.feature.expenses.ExpenseEditRoute
import fr.familia.app.feature.expenses.ExpenseHistoryRoute
import fr.familia.app.feature.expenses.ExpensesRoute
import fr.familia.app.feature.expenses.TripExpenseEditDestination
import fr.familia.app.feature.expenses.TripExpenseHistoryDestination
import fr.familia.app.feature.expenses.TripExpensesDestination
import fr.familia.app.feature.trips.TripEditDestination
import fr.familia.app.feature.trips.TripPhotosDestination
import fr.familia.app.feature.trips.TripStepEditDestination
import fr.familia.app.feature.trips.TripsDestination
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
    openRecap: Boolean = false,
    onRecapOpened: () -> Unit = {},
    /** « Une idée ? Un souci ? » of a tapped notification: "list" (Retours) or "mine" (récit 6 sexies). */
    openFeedback: String? = null,
    onFeedbackOpened: () -> Unit = {},
    /** A link shared from another app, to add to « Mes envies » once (récit 8 bis). */
    sharedWish: SharedWish? = null,
    onSharedWishOpened: () -> Unit = {},
    /** The member of a shared birthday link (récit 31): their page opens once. */
    openMemberId: String? = null,
    onMemberOpened: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    // Each time the app opens, this phone is (re)registered for the notifications of this member.
    LaunchedEffect(currentMember.id) {
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
    val iconSwitcher = remember(context) { AppIconSwitcher(context.applicationContext) }
    var showOnboarding by rememberSaveable { mutableStateOf(!onboarding.seen) }
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
    LaunchedEffect(openListId) {
        if (openListId != null) {
            navController.navigate(GiftListDestination(openListId))
            onListOpened()
        }
    }
    LaunchedEffect(openEventId) {
        if (openEventId != null) {
            navController.navigate(EventDetailDestination(openEventId))
            onEventOpened()
        }
    }
    LaunchedEffect(openTripId) {
        if (openTripId != null) {
            navController.navigate(TripDetailDestination(openTripId))
            onTripOpened()
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
    InAppUpdateDialogs(updater, update)

    // Modules still in test are shown to the admin principal only, until switched on in
    // Administration (config/app.features). The owner is read from the live family.
    val ownerFlow = remember(container) {
        container?.memberRepository?.observeFamily()?.map { it.ownerId }?.catch { emit(null) } ?: flowOf(null)
    }
    val ownerId by ownerFlow.collectAsStateWithLifecycle(initialValue = null)
    val features = FeatureFlags(appConfig?.features.orEmpty(), isOwner = ownerId != null && ownerId == currentMember.id)

    // « Partager » to Familia (récit 8 bis): in the share menu only with its module, and a shared
    // link opens a new wish once the modules are known. Module off: the link is dropped.
    val shareOn = features.isOn(Feature.Gifts) && features.isOn(Feature.ShareToWishes)
    val shareSwitch = remember(context) { ShareTargetSwitch(context.applicationContext) }
    LaunchedEffect(appConfig, ownerId) {
        if (appConfig != null && ownerId != null) shareSwitch.set(shareOn)
    }
    LaunchedEffect(sharedWish, appConfig, ownerId) {
        if (sharedWish != null && appConfig != null && ownerId != null) {
            if (shareOn) {
                navController.navigate(
                    GiftItemEditDestination(wishesListId(currentMember.id), sharedUrl = sharedWish.url, sharedTitle = sharedWish.title),
                )
            }
            onSharedWishOpened()
        }
    }

    // The Accueil (récit 29) is the first tab and the start screen once its module shows. The start
    // is read from the phone at once, and kept for this opening even if the module changes.
    val startTab = rememberSaveable { TopLevelDestination.startFor(homePrefs.homeOn) }
    LaunchedEffect(appConfig, ownerId) {
        if (appConfig != null && ownerId != null) homePrefs.homeOn = features.isOn(Feature.Home)
    }

    // The Entraide tab and its entry points stay hidden while the module is off (PRD rév. 120).
    val helpRequestsOn = features.isOn(Feature.HelpRequests)
    val tabs = TopLevelDestination.visible(features).let { visible ->
        // The start screen keeps its tab until the next opening, so the bar always shows where one is.
        if (startTab in visible) visible else listOf(startTab) + visible
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

    // And for « La semaine de la famille » (récit 36).
    val recapOn = features.isOn(Feature.Recap)
    LaunchedEffect(openRecap, recapOn) {
        if (openRecap && recapOn) {
            navController.navigate(RecapDestination) { launchSingleTop = true }
            onRecapOpened()
        }
    }

    // And for an idea or a problem: Retours for the admin principal, « Mes demandes » for its author.
    LaunchedEffect(openFeedback) {
        when (openFeedback) {
            "list" -> navController.navigate(FeedbackListDestination) { launchSingleTop = true }
            "mine" -> navController.navigate(MyFeedbackDestination) { launchSingleTop = true }
        }
        if (openFeedback != null) onFeedbackOpened()
    }

    var updateDismissed by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { updateDismissed = false }

    // Récit 15: the Entraide tab counts the open requests where I was warned and can still help.
    val helpBadges = remember(container, currentMember) {
        container?.helpRequestRepository?.observeMine(currentMember.id)
            ?.combine(currentDayFlow()) { requests, today -> badgeCount(requests, currentMember, today) }
            ?.catch { emit(0) }
            ?: flowOf(0)
    }
    val helpBadge by helpBadges.collectAsStateWithLifecycle(initialValue = 0)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // The bar only shows on the tabs, not on screens opened from them.
    val onTab = TopLevelDestination.entries.any { currentDestination.isOn(it) }

    // Profile photos (PRD, récit 3 bis): read once here, shown by every avatar of the app.
    val photos by rememberMemberPhotos(container?.memberPhotoRepository)

    // « C'est enregistré », at the bottom, once a form closes (audit DESIGN du 14/11).
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val confirm: (Confirmation) -> Unit = { confirmation ->
        scope.launch { snackbar.showConfirmation(context.getString(confirmation.textRes)) }
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
    FamiliaTheme(theme = if (themesOn) chosenTheme else FamiliaThemeId.Classic) {
    CompositionLocalProvider(
        LocalOpenHelp provides { navController.navigate(HelpDestination) { launchSingleTop = true } },
        LocalMemberPhotos provides photos,
        LocalFeatureFlags provides features,
        LocalInAppUpdater provides updater,
        LocalSnackbarHost provides snackbar,
        LocalPhotoStorage provides container?.photoStorage,
    ) {
        // Pinned: the title bar stays, and takes the surface color once the content scrolls under it
        // (audit DESIGN n°4), so the theme's pattern no longer mixes with the title.
        val topBarScroll = TopAppBarDefaults.pinnedScrollBehavior()
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
                if (tab != null) {
                    TabTopBar(
                        tab, currentMember,
                        scrollBehavior = topBarScroll,
                        onOpenTrips = onTrips,
                        onGoodNews = onGoodNews,
                        onWeeklyQuestion = onWeekly,
                        onPolls = if (pollsOn) ({ navController.navigate(PollsDestination) { launchSingleTop = true } }) else null,
                    )
                }
            },
            bottomBar = {
                if (onTab) NavigationBar {
                    tabs.forEach { destination ->
                        val selected = currentDestination.isOn(destination)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(destination) },
                            icon = {
                                val badge = if (destination == TopLevelDestination.HelpRequests) helpBadge else 0
                                BadgedBox(badge = { if (badge > 0) Badge { Text(badge.toString()) } }) {
                                    Icon(
                                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = null, // The label below already names the tab.
                                    )
                                }
                            },
                            label = { Text(stringResource(destination.labelFor(currentMember))) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            // Consumed here, so the inner screens with their own Scaffold do not add the system bars again.
            Column(Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)) {
                AnimatedVisibility(visible = isOffline) { OfflineBanner() }
                val shownUpdate = update?.takeIf { onTab && !updateDismissed }
                if (shownUpdate != null) {
                    UpdateBanner(
                        update = shownUpdate,
                        onUpdate = { updater.start(shownUpdate) },
                        onLater = { updateDismissed = true },
                    )
                }
                NavHost(
                    navController = navController,
                    startDestination = startTab.route,
                    modifier = Modifier.weight(1f),
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
                                onOpenPoll = { id -> navController.navigate(PollDetailDestination(id)) },
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
                    composable<JoinMeDestination> {
                        JoinMeRoute(container = container, currentMember = currentMember, onDone = { navController.popBackStack() })
                    }
                    // Trips (V3): no tab (carte DESIGN-4), they open from the planning.
                    composable<TripsDestination> {
                        TripsRoute(
                            container = container,
                            currentMember = currentMember,
                            onOpenTrip = { id -> navController.navigate(TripDetailDestination(id)) },
                            onAddTrip = { navController.navigate(TripEditDestination()) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<TripDetailDestination> { entry ->
                        val tripId = entry.toRoute<TripDetailDestination>().tripId
                        TripDetailRoute(
                            container = container,
                            currentMember = currentMember,
                            tripId = tripId,
                            onEditTrip = { navController.navigate(TripEditDestination(tripId)) },
                            onAddStep = { navController.navigate(TripStepEditDestination(tripId)) },
                            onEditStep = { stepId -> navController.navigate(TripStepEditDestination(tripId, stepId)) },
                            onSeePhotos = { navController.navigate(TripPhotosDestination(tripId)) },
                            onBack = { navController.popBackStack() },
                            // The accounts are for the adults who take part (récit 16 bis); the trip view checks the participant.
                            onOpenExpenses = if (features.isOn(Feature.Expenses) && currentMember.profile == MemberProfile.Adult) {
                                { navController.navigate(TripExpensesDestination(tripId)) }
                            } else {
                                null
                            },
                        )
                    }
                    composable<TripExpensesDestination> { entry ->
                        ExpensesRoute(
                            container = container,
                            currentMember = currentMember,
                            tripId = entry.toRoute<TripExpensesDestination>().tripId,
                            onAdd = { navController.navigate(TripExpenseEditDestination(entry.toRoute<TripExpensesDestination>().tripId)) },
                            onEdit = { id -> navController.navigate(TripExpenseEditDestination(entry.toRoute<TripExpensesDestination>().tripId, id)) },
                            onHistory = { id -> navController.navigate(TripExpenseHistoryDestination(entry.toRoute<TripExpensesDestination>().tripId, id)) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<TripExpenseEditDestination> { entry ->
                        val route = entry.toRoute<TripExpenseEditDestination>()
                        ExpenseEditRoute(
                            container = container,
                            currentMember = currentMember,
                            tripId = route.tripId,
                            expenseId = route.expenseId,
                            onHistory = { id -> navController.navigate(TripExpenseHistoryDestination(route.tripId, id)) },
                            onDone = { navController.popBackStack() },
                        )
                    }
                    composable<TripExpenseHistoryDestination> { entry ->
                        val route = entry.toRoute<TripExpenseHistoryDestination>()
                        ExpenseHistoryRoute(container = container, tripId = route.tripId, expenseId = route.expenseId, onBack = { navController.popBackStack() })
                    }
                    composable<TripPhotosDestination> { entry ->
                        TripPhotosRoute(
                            container = container,
                            currentMember = currentMember,
                            tripId = entry.toRoute<TripPhotosDestination>().tripId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<TripEditDestination> { entry ->
                        val tripId = entry.toRoute<TripEditDestination>().tripId
                        TripEditRoute(
                            container = container,
                            currentMember = currentMember,
                            tripId = tripId,
                            onSaved = { savedId ->
                                if (tripId == null) {
                                    // A new trip opens at once, in place of its form.
                                    navController.navigate(TripDetailDestination(savedId)) {
                                        popUpTo<TripEditDestination> { inclusive = true }
                                    }
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
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<RecipesDestination> { entry ->
                        RecipesRoute(
                            container = container,
                            originMemberId = entry.toRoute<RecipesDestination>().originMemberId,
                            onOpen = { id -> navController.navigate(RecipeDetailDestination(id)) },
                            onAdd = { navController.navigate(RecipeEditDestination()) },
                            onBack = { navController.popBackStack() },
                        )
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
                    composable<GenealogyDestination> {
                        GenealogyRoute(
                            container = container,
                            currentMember = currentMember,
                            onBack = { navController.popBackStack() },
                            onOpenProfile = if (souvenirsOn) ({ id -> navController.navigate(MemberProfileDestination(id)) }) else null,
                        )
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
                        EventAlbumRoute(
                            container = container,
                            currentMember = currentMember,
                            eventId = entry.toRoute<EventAlbumDestination>().eventId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<EventEditDestination> { entry ->
                        EventEditRoute(
                            container = container,
                            currentMember = currentMember,
                            eventId = entry.toRoute<EventEditDestination>().eventId,
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
                            onNewList = { navController.navigate(GiftListEditDestination()) },
                            onAllBirthdays = { navController.navigate(BirthdaysRoute) },
                            onPrepareSurprise = { id -> navController.navigate(GiftListEditDestination(forMemberId = id)) },
                            onOpenNotebook = if (features.isOn(Feature.GiftNotebook)) {
                                { navController.navigate(NotebookDestination) }
                            } else {
                                null
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
                        )
                    }
                    composable<ProposeIdeaDestination> { entry ->
                        ProposeIdeaRoute(
                            container = container,
                            currentMember = currentMember,
                            entryId = entry.toRoute<ProposeIdeaDestination>().entryId,
                            onBack = { navController.popBackStack() },
                            onNewSurprise = { id -> navController.navigate(GiftListEditDestination(forMemberId = id)) },
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
                            showClaimOccasion = features.isOn(Feature.WishesReveal),
                            onOpenReceived = if (features.isOn(Feature.WishesReveal)) {
                                { navController.navigate(ReceivedGiftsDestination) }
                            } else {
                                null
                            },
                        )
                    }
                    composable<ReceivedGiftsDestination> {
                        ReceivedGiftsRoute(container = container, currentMember = currentMember, onBack = { navController.popBackStack() })
                    }
                    composable<GiftListEditDestination> { entry ->
                        val route = entry.toRoute<GiftListEditDestination>()
                        GiftListEditRoute(
                            container = container,
                            currentMember = currentMember,
                            listId = route.listId,
                            forMemberId = route.forMemberId,
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
                            onDone = { navController.popBackStack() },
                        )
                    }
                    // --- Coups de main ---
                    composable<HelpRequestsRoute> {
                        HelpRequestsRoute(
                            container = container,
                            currentMember = currentMember,
                            onAsk = { navController.navigate(HelpRequestEditDestination()) },
                            onOpenRequest = { id -> navController.navigate(HelpRequestDetailDestination(id)) },
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
                        )
                    }
                    composable<WeeklyArchiveDestination> {
                        WeeklyArchiveRoute(
                            container = container,
                            onOpen = { id -> navController.navigate(WeeklyQuestionDestination(id)) },
                            onBack = { navController.popBackStack() },
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
                            onOpenNotifications = { navController.navigate(NotificationSettingsDestination) },
                            onOpenAdministration = { navController.navigate(AdministrationDestination) },
                            onOpenUpdates = { navController.navigate(UpdateCheckDestination) },
                            onOpenHelp = { navController.navigate(HelpDestination) },
                            onOpenAppearance = if (themesOn) ({ navController.navigate(AppearanceDestination) }) else null,
                            onOpenChildrenUsage = if (features.isOn(Feature.Usage)) ({ navController.navigate(UsageDestination) }) else null,
                            onOpenRecipes = if (features.isOn(Feature.Recipes)) ({ navController.navigate(RecipesDestination()) }) else null,
                            onOpenCapsules = if (capsulesOn) ({ navController.navigate(CapsulesDestination) }) else null,
                            onOpenGenealogy = if (features.isOn(Feature.Genealogy)) ({ navController.navigate(GenealogyDestination) }) else null,
                            onOpenFeedback = { navController.navigate(FeedbackFormDestination(FeedbackOrigin.Family.key)) },
                            onOpenWishes = if (features.isOn(Feature.Gifts)) {
                                { id -> navController.navigate(GiftListDestination(wishesListId(id))) }
                            } else {
                                null
                            },
                        )
                    }
                    composable<HelpDestination> {
                        HelpRoute(
                            container = container,
                            currentMember = currentMember,
                            onReplayOnboarding = { showOnboarding = true },
                            onBack = { navController.popBackStack() },
                            onOpenFeedback = { navController.navigate(FeedbackFormDestination(FeedbackOrigin.Help.key)) },
                        )
                    }
                    composable<UpdateCheckDestination> {
                        UpdateCheckRoute(container = container, onBack = { navController.popBackStack() })
                    }
                    composable<AdministrationDestination> {
                        AdministrationRoute(
                            container = container,
                            currentMember = currentMember,
                            onOpenExport = { navController.navigate(ExportDestination) },
                            onBack = { navController.popBackStack() },
                            onOpenUsage = { navController.navigate(UsageDestination) },
                            onOpenFeedback = { navController.navigate(FeedbackListDestination) },
                        )
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
                            onBack = { navController.popBackStack() },
                            icon = chosenIcon,
                            onChooseIcon = { icon ->
                                chosenIcon = icon
                                iconSwitcher.choose(icon)
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
            }
        }
    }
    }
}

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
    onPolls: (() -> Unit)? = null,
) {
    TopAppBar(
        scrollBehavior = scrollBehavior,
        // Transparent at rest, so the theme's pattern shows behind the title; the surface color once
        // the content scrolls under it.
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = FamiliaClay.style.titleColor ?: MaterialTheme.colorScheme.onSurface,
        ),
        // The Entraide tab keeps the full name « Coups de main » as its title (DESIGN-4).
        title = { Text(stringResource(if (tab == TopLevelDestination.HelpRequests) R.string.helprequest_screen_title else tab.labelFor(member))) },
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
                    if (onPolls != null) {
                        IconButton(onClick = onPolls, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Outlined.Poll, contentDescription = stringResource(R.string.polls_title))
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
                TopLevelDestination.Home -> HelpButton(ScreenHelp.Home)
                TopLevelDestination.Members -> HelpButton(ScreenHelp.Family)
                TopLevelDestination.HelpRequests -> HelpButton(ScreenHelp.HelpRequests)
                TopLevelDestination.Gifts -> HelpButton(ScreenHelp.Gifts)
            }
        },
    )
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true

private fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        // Keep one copy of each tab and remember where the user was in it.
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
