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
}
