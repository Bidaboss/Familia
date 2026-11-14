package fr.familia.app.feature.onboarding

import fr.familia.app.core.model.MemberProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingPagesTest {

    @Test
    fun `an adult sees the five screens, notifications last`() {
        assertEquals(
            listOf(
                OnboardingPage.Welcome, OnboardingPage.Planning, OnboardingPage.AddEvent,
                OnboardingPage.Answer, OnboardingPage.Notifications,
            ),
            onboardingPages(MemberProfile.Adult),
        )
    }

    @Test
    fun `a child is not told to answer for their children`() {
        val pages = onboardingPages(MemberProfile.ChildWithApp)
        assertEquals(OnboardingPage.AnswerChild, pages[3])
        assertEquals(OnboardingPage.Notifications, pages.last())
        assertEquals(5, pages.size)
    }
}
