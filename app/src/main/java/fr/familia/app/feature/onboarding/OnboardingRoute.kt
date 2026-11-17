package fr.familia.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import fr.familia.app.core.notifications.rememberNotificationPermission

/** The tutorial, screen after screen. [onDone] is called once, when the member reaches the app. */
@Composable
fun OnboardingRoute(
    answersForChildren: Boolean,
    gifts: Boolean = false,
    helpRequests: Boolean = false,
    usage: Boolean = false,
    onDone: () -> Unit,
) {
    val pages = remember(answersForChildren, gifts, helpRequests, usage) { onboardingPages(answersForChildren, gifts, helpRequests, usage) }
    // The screen itself is kept, not its number: the list can grow while it shows (see positionOf).
    var current by rememberSaveable { mutableStateOf(pages.first()) }
    val index = positionOf(pages, current)
    val permission = rememberNotificationPermission()
    var askedPermission by rememberSaveable { mutableStateOf(false) }
    // Back goes to the previous screen; on the first one, Android leaves the app as usual.
    BackHandler(enabled = index > 0) { current = pages[index - 1] }
    // Once the Android dialog has answered, whatever the answer, the app opens.
    LaunchedEffect(askedPermission, permission.allowed) {
        if (askedPermission && permission.allowed) onDone()
    }
    OnboardingScreen(
        pages = pages,
        index = index,
        notificationsAllowed = permission.allowed,
        onNext = { current = pages[(index + 1).coerceAtMost(pages.lastIndex)] },
        onSkip = { current = pages.last() },
        onAllowNotifications = {
            askedPermission = true
            permission.ask()
        },
        onFinish = onDone,
    )
}
