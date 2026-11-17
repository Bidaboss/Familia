package fr.familia.app.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingPagesTest {

    @Test
    fun `a parent sees the four screens, notifications last`() {
        assertEquals(
            listOf(
                OnboardingPage.Welcome, OnboardingPage.AddEvent,
                OnboardingPage.Answer, OnboardingPage.Notifications,
            ),
            onboardingPages(answersForChildren = true),
        )
    }

    @Test
    fun `someone with no child to answer for is not told to answer for their children`() {
        val pages = onboardingPages(answersForChildren = false)
        assertEquals(OnboardingPage.AnswerSelf, pages[2])
        assertEquals(OnboardingPage.Notifications, pages.last())
        assertEquals(4, pages.size)
    }

    @Test
    fun `cadeaux and entraide come before notifications, only when their module shows`() {
        assertEquals(
            listOf(
                OnboardingPage.Welcome, OnboardingPage.AddEvent, OnboardingPage.Answer,
                OnboardingPage.Gifts, OnboardingPage.HelpRequests, OnboardingPage.Notifications,
            ),
            onboardingPages(answersForChildren = true, gifts = true, helpRequests = true),
        )
        val giftsOnly = onboardingPages(answersForChildren = false, gifts = true)
        assertEquals(OnboardingPage.Gifts, giftsOnly[3])
        assertEquals(false, OnboardingPage.HelpRequests in giftsOnly)
    }

    @Test
    fun `once the time in the app is counted, the welcome says who sees it`() {
        val pages = onboardingPages(answersForChildren = true, usage = true)
        assertEquals(OnboardingPage.WelcomeUsage, pages.first())
        assertEquals(4, pages.size)
    }

    @Test
    fun `a skip made before the config is read stays on notifications when cadeaux and entraide arrive`() {
        val before = onboardingPages(answersForChildren = false)
        val skipped = before.last()
        val after = onboardingPages(answersForChildren = true, gifts = true, helpRequests = true)
        assertEquals(after.lastIndex, positionOf(after, skipped))
        assertEquals(OnboardingPage.Notifications, after[positionOf(after, skipped)])
    }

    @Test
    fun `the answer screen stays the same screen when it switches to its parent variant`() {
        val before = onboardingPages(answersForChildren = false)
        val after = onboardingPages(answersForChildren = true, gifts = true)
        assertEquals(2, positionOf(after, OnboardingPage.AnswerSelf))
        assertEquals(0, positionOf(onboardingPages(answersForChildren = true, usage = true), before.first()))
    }

    @Test
    fun `a screen that left the list goes back to the first one`() {
        assertEquals(0, positionOf(onboardingPages(answersForChildren = true), OnboardingPage.Gifts))
    }
}
