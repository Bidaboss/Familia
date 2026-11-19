package fr.familia.app

import fr.familia.app.core.share.SHARE_HOST
import org.junit.Assert.assertEquals
import org.junit.Test

/** The single door of MainActivity (audit): each kind of notification and of shared link. */
class DeepLinksTest {

    private val view = "android.intent.action.VIEW"

    private fun fromExtras(vararg pairs: Pair<String, String>): List<DeepLink> {
        val extras = pairs.toMap()
        return deepLinks({ extras[it] }, action = null, scheme = null, host = null, path = null)
    }

    private fun fromLink(scheme: String, host: String, path: String, action: String? = view): List<DeepLink> =
        deepLinks({ null }, action, scheme, host, path)

    @Test
    fun `each notification opens its own screen`() {
        assertEquals(listOf(DeepLink.Event("e1")), fromExtras("eventId" to "e1"))
        assertEquals(listOf(DeepLink.GiftList("l1")), fromExtras("listId" to "l1"))
        assertEquals(listOf(DeepLink.HelpRequest("h1")), fromExtras("helpRequestId" to "h1"))
        assertEquals(listOf(DeepLink.GoodNews("g1")), fromExtras("goodNewsId" to "g1"))
        assertEquals(listOf(DeepLink.Trip("t1")), fromExtras("tripId" to "t1"))
        assertEquals(listOf(DeepLink.WeeklyQuestion("q1")), fromExtras("weeklyQuestionId" to "q1"))
        assertEquals(listOf(DeepLink.Souvenir), fromExtras("souvenirKey" to "first-event"))
        assertEquals(listOf(DeepLink.Poll("p1")), fromExtras("pollId" to "p1"))
        assertEquals(listOf(DeepLink.JoinMe), fromExtras("joinMeId" to "j1"))
        assertEquals(listOf(DeepLink.Capsule("c1")), fromExtras("capsuleId" to "c1"))
        assertEquals(listOf(DeepLink.Recap("2026-11-15")), fromExtras("recapWeek" to "2026-11-15"))
        assertEquals(listOf(DeepLink.Feedback("list")), fromExtras("feedback" to "list"))
        assertEquals(listOf(DeepLink.Feedback("crashes")), fromExtras("feedback" to "crashes"))
        assertEquals(listOf(DeepLink.GoodDeals), fromExtras("goodDeals" to "list"))
        assertEquals(listOf(DeepLink.LendItem("i1")), fromExtras("lendItemId" to "i1"))
        assertEquals(listOf(DeepLink.Announcement("a1")), fromExtras("announcementId" to "a1"))
    }

    @Test
    fun `a birthday and a welcome open the member's page, once`() {
        assertEquals(listOf(DeepLink.Member("m1")), fromExtras("birthdayOf" to "m1"))
        assertEquals(listOf(DeepLink.Member("m2")), fromExtras("welcomeOf" to "m2"))
        assertEquals(listOf(DeepLink.Member("m1")), fromExtras("birthdayOf" to "m1", "welcomeOf" to "m2"))
    }

    @Test
    fun `an old « Pensez à moi » or « À qui le tour ? » notification just opens the app`() {
        assertEquals(emptyList<DeepLink>(), fromExtras("thinkOfMeOf" to "m3"))
        assertEquals(emptyList<DeepLink>(), fromExtras("turnId" to "t1"))
    }

    @Test
    fun `each shared link opens the same screen as its notification`() {
        assertEquals(listOf(DeepLink.Event("abc")), fromLink("https", SHARE_HOST, "/o/event/abc"))
        assertEquals(listOf(DeepLink.Poll("abc")), fromLink("https", SHARE_HOST, "/o/poll/abc"))
        assertEquals(listOf(DeepLink.Member("abc")), fromLink("https", SHARE_HOST, "/o/birthday/abc"))
        assertEquals(listOf(DeepLink.GiftList("abc")), fromLink("https", SHARE_HOST, "/o/list/abc"))
        assertEquals(listOf(DeepLink.GoodNews("abc")), fromLink("https", SHARE_HOST, "/o/goodNews/abc"))
        assertEquals(listOf(DeepLink.Trip("abc")), fromLink("https", SHARE_HOST, "/o/trip/abc"))
        assertEquals(listOf(DeepLink.HelpRequest("abc")), fromLink("https", SHARE_HOST, "/o/help/abc"))
        assertEquals(listOf(DeepLink.Event("abc")), fromLink("familia", "o", "/event/abc"))
    }

    @Test
    fun `a link opens nothing unless it is viewed, from our host, of a known kind`() {
        assertEquals(emptyList<DeepLink>(), fromLink("https", SHARE_HOST, "/o/event/abc", action = "android.intent.action.SEND"))
        assertEquals(emptyList<DeepLink>(), fromLink("https", "example.com", "/o/event/abc"))
        assertEquals(emptyList<DeepLink>(), fromLink("https", SHARE_HOST, "/o/unknown/abc"))
        assertEquals(emptyList<DeepLink>(), fromLink("https", SHARE_HOST, "/o/event"))
    }

    @Test
    fun `an intent with nothing opens nothing`() {
        assertEquals(emptyList<DeepLink>(), fromExtras())
    }
}
