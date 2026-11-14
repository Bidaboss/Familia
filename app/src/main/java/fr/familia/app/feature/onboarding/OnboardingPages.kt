package fr.familia.app.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.ui.graphics.vector.ImageVector
import fr.familia.app.R
import fr.familia.app.core.model.MemberProfile

/** One screen of the first-launch tutorial (spec « tuto et aide », récit 25). */
enum class OnboardingPage(val icon: ImageVector, @StringRes val titleRes: Int, @StringRes val bodyRes: Int) {
    Welcome(Icons.Outlined.FamilyRestroom, R.string.onboarding_welcome_title, R.string.onboarding_welcome_body),
    Planning(Icons.Outlined.CalendarMonth, R.string.onboarding_planning_title, R.string.onboarding_planning_body),
    AddEvent(Icons.Outlined.AddCircleOutline, R.string.onboarding_add_title, R.string.onboarding_add_body),
    Answer(Icons.Outlined.HowToReg, R.string.onboarding_answer_title, R.string.onboarding_answer_body),

    /** Same screen for a child, without "Tu peux aussi répondre pour tes enfants". */
    AnswerChild(Icons.Outlined.HowToReg, R.string.onboarding_answer_title, R.string.onboarding_answer_body_child),

    /** Always last: it asks for the notification permission. */
    Notifications(Icons.Outlined.NotificationsActive, R.string.onboarding_notifications_title, R.string.onboarding_notifications_body),
}

/** The screens shown to a member, by profile: a child does not see what only adults can do. */
fun onboardingPages(profile: MemberProfile): List<OnboardingPage> = listOf(
    OnboardingPage.Welcome,
    OnboardingPage.Planning,
    OnboardingPage.AddEvent,
    if (profile == MemberProfile.Adult) OnboardingPage.Answer else OnboardingPage.AnswerChild,
    OnboardingPage.Notifications,
)
