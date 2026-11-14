package fr.familia.app.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingPagesTest {

    @Test
    fun `a parent sees the five screens, notifications last`() {
        assertEquals(
            listOf(
                OnboardingPage.Welcome, OnboardingPage.Planning, OnboardingPage.AddEvent,
                OnboardingPage.Answer, OnboardingPage.Notifications,
            ),
            onboardingPages(answersForChildren = true),
        )
    }

    @Test
    fun `someone with no child to answer for is not told to answer for their children`() {
        val pages = onboardingPages(answersForChildren = false)
        assertEquals(OnboardingPage.AnswerSelf, pages[3])
        assertEquals(OnboardingPage.Notifications, pages.last())
        assertEquals(5, pages.size)
    }
}
