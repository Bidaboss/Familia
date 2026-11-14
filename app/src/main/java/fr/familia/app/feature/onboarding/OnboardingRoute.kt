package fr.familia.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.notifications.rememberNotificationPermission

/** The tutorial, screen after screen. [onDone] is called once, when the member reaches the app. */
@Composable
fun OnboardingRoute(profile: MemberProfile, onDone: () -> Unit) {
    val pages = remember(profile) { onboardingPages(profile) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val permission = rememberNotificationPermission()
    var askedPermission by rememberSaveable { mutableStateOf(false) }
    // Back goes to the previous screen; on the first one, Android leaves the app as usual.
    BackHandler(enabled = index > 0) { index-- }
    // Once the Android dialog has answered, whatever the answer, the app opens.
    LaunchedEffect(askedPermission, permission.allowed) {
        if (askedPermission && permission.allowed) onDone()
    }
    OnboardingScreen(
        pages = pages,
        index = index,
        notificationsAllowed = permission.allowed,
        onNext = { index = (index + 1).coerceAtMost(pages.lastIndex) },
        onSkip = { index = pages.lastIndex },
        onAllowNotifications = {
            askedPermission = true
            permission.ask()
        },
        onFinish = onDone,
    )
}
