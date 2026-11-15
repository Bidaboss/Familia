package fr.familia.app.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.ui.graphics.vector.ImageVector
import fr.familia.app.R

/** One screen of the first-launch tutorial (spec « tuto et aide », récit 25), 4 in all (DESIGN-5). */
enum class OnboardingPage(val icon: ImageVector, @StringRes val titleRes: Int, @StringRes val bodyRes: Int) {
    Welcome(Icons.Outlined.FamilyRestroom, R.string.onboarding_welcome_title, R.string.onboarding_welcome_body),
    AddEvent(Icons.Outlined.AddCircleOutline, R.string.onboarding_add_title, R.string.onboarding_add_body),
    Answer(Icons.Outlined.HowToReg, R.string.onboarding_answer_title, R.string.onboarding_answer_body),

    /** Same screen for someone with no child to answer for, without "Tu peux aussi répondre pour tes enfants". */
    AnswerSelf(Icons.Outlined.HowToReg, R.string.onboarding_answer_title, R.string.onboarding_answer_body_child),

    /** Always last: it asks for the notification permission. */
    Notifications(Icons.Outlined.NotificationsActive, R.string.onboarding_notifications_title, R.string.onboarding_notifications_body),
}

/**
 * The screens shown to a member. Only someone who can answer for a child (see `answerableMembers`)
 * is told "Tu peux aussi répondre pour tes enfants": a grandparent or a child is not.
 */
fun onboardingPages(answersForChildren: Boolean): List<OnboardingPage> = listOf(
    OnboardingPage.Welcome,
    OnboardingPage.AddEvent,
    if (answersForChildren) OnboardingPage.Answer else OnboardingPage.AnswerSelf,
    OnboardingPage.Notifications,
)
