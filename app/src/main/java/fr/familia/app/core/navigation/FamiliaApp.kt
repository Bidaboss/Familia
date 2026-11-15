package fr.familia.app.core.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import fr.familia.app.BuildConfig
import fr.familia.app.feature.update.UpdateBanner
import fr.familia.app.feature.update.availableUpdate
import fr.familia.app.feature.update.openDownload
import fr.familia.app.feature.update.UpdateCheckRoute
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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

/** Root of the UI: bottom navigation bar plus the single NavHost of the app. */
@Composable
fun FamiliaApp(
    container: AppContainer?,
    currentMember: Member,
    isOffline: Boolean = false,
    /** The event of a tapped notification, to open once. */
    openEventId: String? = null,
    onEventOpened: () -> Unit = {},
    /** The gift list of a tapped notification, to open once (V2). */
    openListId: String? = null,
    onListOpened: () -> Unit = {},
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
    val updates = remember(container) {
        container?.appConfigRepository?.observeAppConfig()
            ?.map { config -> availableUpdate(config, BuildConfig.VERSION_CODE, BuildConfig.DOWNLOAD_URL) }
            ?.catch { /* Offline or no config yet: nothing to offer. */ }
            ?: flowOf(null)
    }
    val update by updates.collectAsStateWithLifecycle(initialValue = null)
    var updateDismissed by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { updateDismissed = false }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // The bar only shows on the three tabs, not on screens opened from them.
    val onTab = TopLevelDestination.entries.any { currentDestination.isOn(it) }

    // Profile photos (PRD, récit 3 bis): read once here, shown by every avatar of the app.
    val photos by rememberMemberPhotos(container?.memberPhotoRepository)

    CompositionLocalProvider(
        LocalOpenHelp provides { navController.navigate(HelpDestination) { launchSingleTop = true } },
        LocalMemberPhotos provides photos,
    ) {
        Scaffold(
            topBar = {
                // The tabs get a title bar, with the « ? » of their screen (spec « tuto et aide », récit 26).
                val tab = TopLevelDestination.entries.firstOrNull { currentDestination.isOn(it) }
                if (tab != null) TabTopBar(tab, currentMember)
            },
            bottomBar = {
                if (onTab) NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination.isOn(destination)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(destination) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = null, // The label below already names the tab.
                                )
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
                        onUpdate = { openDownload(context, shownUpdate.downloadUrl) },
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
private fun TabTopBar(tab: TopLevelDestination, member: Member) {
    TopAppBar(
        title = { Text(stringResource(tab.labelFor(member))) },
        actions = {
            when (tab) {
                TopLevelDestination.Planning -> HelpButton(ScreenHelp.Planning)
                TopLevelDestination.Members -> HelpButton(ScreenHelp.Family)
                TopLevelDestination.Gifts -> Unit
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
