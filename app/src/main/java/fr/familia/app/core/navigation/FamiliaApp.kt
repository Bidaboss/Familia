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
import fr.familia.app.core.model.FeatureFlags
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.feature.update.UpdateCheckRoute
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.HelpRoute
import fr.familia.app.feature.goodnews.detail.GoodNewsDetailRoute
import fr.familia.app.feature.goodnews.edit.GoodNewsEditRoute
import fr.familia.app.feature.goodnews.feed.GoodNewsFeedRoute
import fr.familia.app.feature.help.LocalOpenHelp
import fr.familia.app.feature.help.ScreenHelp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.LocalMemberPhotos
import fr.familia.app.core.ui.OfflineBanner
import fr.familia.app.core.ui.rememberMemberPhotos
import fr.familia.app.feature.members.MembersRoute
import fr.familia.app.feature.events.edit.EventEditRoute
import fr.familia.app.feature.planning.PlanningRoute
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
import fr.familia.app.feature.events.answerableMembers
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.feature.helprequests.badgeCount
import fr.familia.app.feature.helprequests.detail.HelpRequestDetailRoute
import fr.familia.app.feature.helprequests.edit.HelpRequestEditRoute
import fr.familia.app.feature.helprequests.list.HelpRequestsRoute
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import kotlinx.coroutines.flow.combine

/** Root of the UI: bottom navigation bar plus the single NavHost of the app. */
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
    var showOnboarding by rememberSaveable { mutableStateOf(!onboarding.seen) }
    if (showOnboarding) {
        // The 4th screen mentions answering for one's children only to those who have some.
        val familyFlow = remember(container) { container?.memberRepository?.observeFamily() ?: flowOf(null) }
        val family by familyFlow.collectAsStateWithLifecycle(initialValue = null)
        val answersForChildren = family?.let { answerableMembers(currentMember, it).size > 1 } ?: false
        OnboardingRoute(answersForChildren = answersForChildren) {
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

    // The Entraide tab and its entry points stay hidden while the module is off (PRD rév. 120).
    val helpRequestsOn = features.isOn(Feature.HelpRequests)
    val tabs = TopLevelDestination.entries.filter { it != TopLevelDestination.HelpRequests || helpRequestsOn }
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

    // The bar only shows on the three tabs, not on screens opened from them.
    val onTab = TopLevelDestination.entries.any { currentDestination.isOn(it) }

    // Profile photos (PRD, récit 3 bis): read once here, shown by every avatar of the app.
    val photos by rememberMemberPhotos(container?.memberPhotoRepository)

    CompositionLocalProvider(
        LocalOpenHelp provides { navController.navigate(HelpDestination) { launchSingleTop = true } },
        LocalMemberPhotos provides photos,
        LocalFeatureFlags provides features,
        LocalInAppUpdater provides updater,
        LocalPhotoStorage provides container?.photoStorage,
    ) {
        Scaffold(
            topBar = {
                // The tabs get a title bar, with the « ? » of their screen (spec « tuto et aide », récit 26).
                val tab = TopLevelDestination.entries.firstOrNull { currentDestination.isOn(it) }
                // Until the Accueil arrives, the Bonnes nouvelles open from the Planning bar (behind their module).
                val onGoodNews = if (goodNewsOn) ({ navController.navigate(GoodNewsFeedDestination) }) else null
                if (tab != null) TabTopBar(tab, currentMember, onGoodNews)
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
            Column(Modifier.padding(innerPadding)) {
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
                    startDestination = TopLevelDestination.start.route,
                    modifier = Modifier.weight(1f),
                ) {
                    composable<PlanningRoute> {
                        PlanningRoute(
                            container = container,
                            currentMember = currentMember,
                            onAddEvent = { navController.navigate(EventEditDestination()) },
                            onOpenEvent = { id -> navController.navigate(EventDetailDestination(id)) },
                        )
                    }
                    composable<EventDetailDestination> { entry ->
                        val eventId = entry.toRoute<EventDetailDestination>().eventId
                        EventDetailRoute(
                            container = container,
                            currentMember = currentMember,
                            eventId = eventId,
                            onEdit = { navController.navigate(EventEditDestination(eventId)) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<EventEditDestination> { entry ->
                        EventEditRoute(
                            container = container,
                            currentMember = currentMember,
                            eventId = entry.toRoute<EventEditDestination>().eventId,
                            onDone = { navController.popBackStack() },
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
                        )
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
                    composable<GoodNewsEditDestination> { entry ->
                        GoodNewsEditRoute(
                            container = container,
                            currentMember = currentMember,
                            postId = entry.toRoute<GoodNewsEditDestination>().postId,
                            onDone = { navController.popBackStack() },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<MembersRoute> {
                        MembersRoute(
                            container = container,
                            currentMember = currentMember,
                            onEditMember = { id -> navController.navigate(MemberEditDestination(id)) },
                            onOpenNotifications = { navController.navigate(NotificationSettingsDestination) },
                            onOpenAdministration = { navController.navigate(AdministrationDestination) },
                            onOpenUpdates = { navController.navigate(UpdateCheckDestination) },
                            onOpenHelp = { navController.navigate(HelpDestination) },
                        )
                    }
                    composable<HelpDestination> {
                        HelpRoute(
                            container = container,
                            currentMember = currentMember,
                            onReplayOnboarding = { showOnboarding = true },
                            onBack = { navController.popBackStack() },
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
                        )
                    }
                    composable<ExportDestination> {
                        ExportRoute(container = container, onBack = { navController.popBackStack() })
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
                            onDone = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

/** Title bar of a tab, with its « ? » where the spec has one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabTopBar(tab: TopLevelDestination, member: Member, onGoodNews: (() -> Unit)? = null) {
    TopAppBar(
        // The Entraide tab keeps the full name « Coups de main » as its title (DESIGN-4).
        title = { Text(stringResource(if (tab == TopLevelDestination.HelpRequests) R.string.helprequest_screen_title else tab.labelFor(member))) },
        actions = {
            when (tab) {
                TopLevelDestination.Planning -> {
                    if (onGoodNews != null) {
                        IconButton(onClick = onGoodNews, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.Celebration, contentDescription = stringResource(R.string.goodnews_open_feed))
                        }
                    }
                    HelpButton(ScreenHelp.Planning)
                }
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
