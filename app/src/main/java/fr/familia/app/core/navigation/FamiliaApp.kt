package fr.familia.app.core.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import fr.familia.app.BuildConfig
import fr.familia.app.feature.update.UpdateBanner
import fr.familia.app.feature.update.availableUpdate
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
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
import fr.familia.app.core.ui.OfflineBanner
import fr.familia.app.feature.members.MembersRoute
import fr.familia.app.feature.events.edit.EventEditRoute
import fr.familia.app.feature.planning.PlanningRoute
import fr.familia.app.feature.settings.NotificationSettingsRoute
import fr.familia.app.feature.export.ExportRoute
import fr.familia.app.core.notifications.AskNotificationPermissionOnce
import kotlinx.coroutines.CancellationException

/** Root of the UI: bottom navigation bar plus the single NavHost of the app. */
@Composable
fun FamiliaApp(
    container: AppContainer?,
    currentMember: Member,
    isOffline: Boolean = false,
    /** The event of a tapped notification, to open once. */
    openEventId: String? = null,
    onEventOpened: () -> Unit = {},
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
    if (container != null) AskNotificationPermissionOnce()
    LaunchedEffect(openEventId) {
        if (openEventId != null) {
            navController.navigate(EventDetailDestination(openEventId))
            onEventOpened()
        }
    }

    // Lot 10: a newer version in config/app is offered above the tabs, once per opening.
    val updates = remember(container) {
        container?.appConfigRepository?.observeAppConfig()
            ?.map { config -> availableUpdate(config, BuildConfig.VERSION_CODE, BuildConfig.DOWNLOAD_URL) }
            ?.catch { /* Offline or no config yet: nothing to offer. */ }
            ?: flowOf(null)
    }
    val update by updates.collectAsState(initial = null)
    var updateDismissed by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // The bar only shows on the three tabs, not on screens opened from them.
    val onTab = TopLevelDestination.entries.any { currentDestination.isOn(it) }

    Scaffold(
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
                        label = { Text(stringResource(destination.labelRes)) },
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
                composable<BirthdaysRoute> { BirthdaysRoute(container) }
                composable<MembersRoute> {
                    MembersRoute(
                        container = container,
                        currentMember = currentMember,
                        onEditMember = { id -> navController.navigate(MemberEditDestination(id)) },
                        onOpenNotifications = { navController.navigate(NotificationSettingsDestination) },
                        onOpenExport = { navController.navigate(ExportDestination) },
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

/** Opens the APK link in the browser or the Drive app; Android then checks the signature on install. */
private fun openDownload(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // No browser at all: nothing sensible to do, the banner stays.
    }
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
