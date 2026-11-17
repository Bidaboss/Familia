package fr.familia.app.feature.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The last screen of the tutorial, « Rester prévenu », after the Android dialog (first arrival, 21/11). */
class LeavesOnboardingTest {

    @Test
    fun `a yes opens the app`() = assertTrue(leavesOnboarding(askedPermission = true, allowed = true, dialogAnswered = true))

    @Test
    fun `a no opens the app too instead of keeping the newcomer on the screen`() =
        assertTrue(leavesOnboarding(askedPermission = true, allowed = false, dialogAnswered = true))

    @Test
    fun `while the dialog shows the screen stays`() =
        assertFalse(leavesOnboarding(askedPermission = true, allowed = false, dialogAnswered = false))

    @Test
    fun `notifications already allowed before asking do not skip the last screen`() =
        assertFalse(leavesOnboarding(askedPermission = false, allowed = true, dialogAnswered = false))
}
