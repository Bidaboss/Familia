package fr.familia.app.core.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import fr.familia.app.feature.birthdays.BirthdaysScreen
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.OfflineBanner
import fr.familia.app.feature.members.MembersRoute
import fr.familia.app.feature.planning.PlanningScreen

/** Root of the UI: bottom navigation bar plus the single NavHost of the app. */
@Composable
fun FamiliaApp(
    container: AppContainer?,
    currentMember: Member,
    isOffline: Boolean = false,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
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
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.start.route,
                modifier = Modifier.weight(1f),
            ) {
                composable<PlanningRoute> { PlanningScreen() }
                composable<BirthdaysRoute> { BirthdaysScreen() }
                composable<MembersRoute> { MembersRoute(container, currentMember) }
            }
        }
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
