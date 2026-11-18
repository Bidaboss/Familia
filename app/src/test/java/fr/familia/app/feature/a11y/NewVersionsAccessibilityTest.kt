package fr.familia.app.feature.a11y

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.ui.checkAccessibility
import fr.familia.app.feature.feed.BirthdayFeedCard
import fr.familia.app.feature.feed.EventFeedCard
import fr.familia.app.feature.feed.FeedContext
import fr.familia.app.feature.feed.FeedList
import fr.familia.app.feature.feed.FeedState
import fr.familia.app.feature.feed.GoodNewsFeedCard
import fr.familia.app.feature.feed.HelpFeedCard
import fr.familia.app.feature.feed.HomeFeedSwitch
import fr.familia.app.feature.goodnews.previewGoodNewsItems
import fr.familia.app.feature.helprequests.list.previewHelpRequests
import fr.familia.app.feature.planning.PlanningMode
import fr.familia.app.feature.planning.PlanningV2
import fr.familia.app.feature.planning.previewPlanning
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * AVIS-22: the new versions the family discovers on the 21st pass the same checks as the old
 * screens. Police 200 % and TalkBack on the new Planning (week and month, adult and child) and on
 * the fil d'actualité of the Accueil, with the common check of [checkAccessibility]: everything one
 * can touch is read by TalkBack and is big enough. The same screens read only (« Voir comme », Mode
 * présentation) are checked too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2f)
class NewVersionsAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private fun check(content: @Composable () -> Unit) = compose.checkAccessibility(content)

    private val adult = Member("a", "Camille", null, null, "h1", MemberProfile.Adult, false, emptyList(), null)
    private val child = Member("c", "Noé", null, LocalDate.of(2015, 11, 18), "h1", MemberProfile.ChildWithApp, false, listOf("a"), null)
    private val family = Family(listOf(Household("h1", "Foyer Un")), listOf(adult, child))
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun `the new Planning by week`() = check { PlanningV2(previewPlanning(), adult, {}, {}, onShareBirthday = {}) }

    @Test
    fun `the new Planning by month`() = check {
        PlanningV2(previewPlanning(), adult, {}, {}, onShareBirthday = null, initialMode = PlanningMode.Month)
    }

    @Test
    fun `the new Planning of a child`() = check { PlanningV2(previewPlanning(), child, {}, {}, onShareBirthday = null) }

    @Test
    fun `the switch Aujourd'hui or Le fil`() = check { HomeFeedSwitch(onFeed = false, unseen = 4, onChoose = {}) }

    @Test
    fun `the fil d'actualité`() = check { FeedList(feed(readOnly = false)) }

    @Test
    fun `the fil d'actualité read only, in Voir comme`() = check { FeedList(feed(readOnly = true)) }

    private fun feed(readOnly: Boolean): FeedState {
        val event = Event(
            "e1", "Repas de famille", today.plusDays(3), LocalTime.of(12, 30), "Chez Inès", null,
            EventTarget.ForFamily, "a", "Camille", createdAt = Instant.parse("2026-11-18T09:00:00Z"),
        )
        val cards = listOf(
            EventFeedCard(event, canAnswer = true, rsvps = null, comments = null),
            BirthdayFeedCard(Birthday(child, today, 11), wishes = true),
            GoodNewsFeedCard(previewGoodNewsItems().first().news, goodNews = null, comments = null),
            HelpFeedCard(previewHelpRequests().open.first().request, requests = null),
        )
        return FeedState(
            cards = cards,
            context = FeedContext(adult, family, today, readOnly),
            unseen = 2,
            canLoadMore = true,
            onMore = {},
            onSeen = {},
        )
    }
}
