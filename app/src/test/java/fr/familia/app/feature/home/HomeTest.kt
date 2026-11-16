package fr.familia.app.feature.home

import fr.familia.app.core.model.ClaimType
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.GiftClaim
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.Rsvp
import fr.familia.app.core.model.RsvpStatus
import fr.familia.app.core.model.TripArrival
import fr.familia.app.feature.events.FakeEvents
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.FakeRsvps
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.gifts.FakeGifts
import fr.familia.app.feature.helprequests.FakeHelpRequests
import fr.familia.app.feature.helprequests.helpRequest
import fr.familia.app.feature.trips.FakeTrips
import fr.familia.app.feature.trips.TestTrips
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import fr.familia.app.core.model.Poll
import fr.familia.app.core.model.PollVote
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 21)
    private val family = TestFamily.family
    private val me = TestFamily.adult

    private fun list(
        id: String,
        date: LocalDate?,
        kind: GiftListKind = GiftListKind.Own,
        beneficiary: String = "adult2",
        viewers: List<String> = listOf(me.id),
    ) = GiftList(id, kind, "Liste $id", GiftOccasion.Birthday, date, beneficiary, viewers, beneficiary, "Adult2")

    @Test
    fun `to do lists the events of the month without my answer, and the coups de main for me`() {
        val answered = TestFamily.event("answered", TestFamily.admin, date = today.plusDays(1))
        val open = TestFamily.event("open", TestFamily.admin, date = today.plusDays(2))
        val later = TestFamily.event("later", TestFamily.admin, date = today.plusDays(40))
        val otherHousehold = TestFamily.event("h2", TestFamily.otherAdult, EventTarget.ForHousehold("h2"), today.plusDays(1))
        val forMyKid = TestFamily.event("kid", TestFamily.admin, EventTarget.ForChild("kid1"), today.plusDays(1))
        val answers = mapOf("answered" to listOf(Rsvp(me.id, RsvpStatus.Yes, me.id)))
        val help = listOf(
            helpRequest("r1", TestFamily.otherAdult, listOf(me.id), today.plusDays(3)),
            helpRequest("r2", TestFamily.otherAdult, listOf("admin1"), today.plusDays(3)),
        )

        val todos = todos(me, family, listOf(answered, open, later, otherHousehold, forMyKid), answers, help, today)

        assertEquals(listOf("open", "r1"), todos.map { if (it is HomeTodo.Answer) it.event.id else (it as HomeTodo.Help).request.id })
    }

    @Test
    fun `a child is never asked to help`() {
        val help = listOf(helpRequest("r1", TestFamily.adult, listOf("kid1"), today.plusDays(1)))
        assertTrue(todos(TestFamily.kid, family, emptyList(), emptyMap(), help, today).isEmpty())
    }

    @Test
    fun `the week shows 3 events of the next 7 days and counts the others`() {
        val events = (0..8L).map { TestFamily.event("e$it", TestFamily.admin, date = today.plusDays(it)) }
        val state = homeState(me, family, today, events, emptyMap(), emptyList(), null, emptyList())
        assertEquals(listOf("e0", "e1", "e2"), state.week.map { it.id })
        assertEquals(4, state.weekMore)
    }

    @Test
    fun `birthdays look 30 days ahead`() {
        val withBirthdays = family.copy(
            members = family.members.map {
                when (it.id) {
                    "adult1" -> it.copy(birthDate = LocalDate.of(1990, 12, 1))
                    "adult2" -> it.copy(birthDate = LocalDate.of(1990, 12, 31))
                    else -> it
                }
            },
        )
        assertEquals(listOf("adult1"), birthdays(withBirthdays, today).map { it.member.id })
    }

    @Test
    fun `the next occasion skips wishes, surprises, past and revealed lists`() {
        val lists = listOf(
            list("wishes", null, GiftListKind.Wishes),
            list("surprise", today, GiftListKind.Surprise),
            list("past", today.minusDays(1)),
            list("revealed", today.plusDays(1)).copy(revealed = true),
            list("far", today.plusDays(30)),
            list("next", today.plusDays(10)),
        )
        assertEquals("next", nextOccasionList(lists, me.id, today)?.id)
    }

    @Test
    fun `the beneficiary of a surprise never learns it exists, nor a list they cannot read, even in Voir comme`() {
        // The surprise for me, read by the others: what the account of the admin principal gets in Voir comme.
        val surprise = list("surprise", today.plusDays(2), GiftListKind.Surprise, beneficiary = me.id, viewers = listOf("adult2"))
        val notMine = list("closed", today.plusDays(3), viewers = listOf("adult2"))
        assertNull(nextOccasionList(listOf(surprise, notMine), me.id, today))
        val mine = list("mine", today.plusDays(5), beneficiary = me.id, viewers = listOf(me.id))
        val shown = occasion(nextOccasionList(listOf(surprise, notMine, mine), me.id, today), me, family, 0, today)
        assertEquals("mine", shown?.list?.id)
        assertTrue(shown?.isMine == true)
    }

    @Test
    fun `my own occasion never says what is reserved for me`() {
        val mine = occasion(list("l", today.plusDays(3), beneficiary = me.id), me, family, 2, today)
        assertNull(mine?.myClaims)
        assertEquals(3L, mine?.daysLeft)
        assertEquals(2, occasion(list("l", today.plusDays(3)), me, family, 2, today)?.myClaims)
    }

    @Test
    fun `the ongoing trip shows its last arrival`() {
        val ongoing = TestTrips.trip("t1", start = today.minusDays(1), end = today.plusDays(2))
        val soon = TestTrips.trip("t2", start = today.plusDays(5), end = today.plusDays(6))
        val over = TestTrips.trip("t3", start = today.minusDays(9), end = today.minusDays(1))
        val shown = shownTrips(listOf(soon, over, ongoing), today)
        assertEquals(listOf("t1", "t2"), shown.map { it.id })
        val arrival = TripArrival(Instant.parse("2026-11-20T10:00:00Z"), "adult2", "Adult2")
        val trips = homeTrips(shown, mapOf("t1" to arrival, "t2" to arrival), family, today)
        assertEquals(arrival, trips[0].lastArrival)
        assertNull(trips[1].lastArrival)
        assertEquals("Adult1 et Adult2", trips[0].who)
    }

    @Test
    fun `the view model gathers every card, each module only while it shows`() = runTest {
        val events = FakeEvents(listOf(TestFamily.event("e1", TestFamily.admin, date = today.plusDays(1))))
        val gifts = FakeGifts(list("l1", today.plusDays(4)))
        gifts.claims.value = listOf(GiftClaim("i1", ClaimType.Solo, me.id, "Adult1"))
        val trip = TestTrips.trip("t1", start = today.minusDays(1), end = today.plusDays(2))
        val vm = HomeViewModel(
            members = FakeMembers(),
            events = events,
            currentMemberId = me.id,
            rsvps = FakeRsvps(),
            helpRequests = FakeHelpRequests(listOf(helpRequest("r1", TestFamily.otherAdult, listOf(me.id), today.plusDays(2)))),
            gifts = gifts,
            trips = FakeTrips(listOf(trip), mapOf("t1" to listOf(TestTrips.step(arrival = TripArrival(null, "adult2", "Adult2"))))),
            today = flowOf(today),
        )
        vm.uiState.launchIn(backgroundScope)
        val ready = vm.uiState.first { it is HomeUiState.Ready && it.occasion?.myClaims == 1 && it.trips.firstOrNull()?.lastArrival != null }
            as HomeUiState.Ready
        assertEquals(2, ready.todos.size)
        assertEquals(listOf("e1"), ready.week.map { it.id })
        assertEquals("l1", ready.occasion?.list?.id)
        assertTrue(ready.canCreateTrip)

        val off = HomeViewModel(FakeMembers(), events, me.id, today = flowOf(today))
        off.uiState.launchIn(backgroundScope)
        val bare = off.uiState.first { it is HomeUiState.Ready && it.week.isNotEmpty() } as HomeUiState.Ready
        assertNull(bare.occasion)
        assertTrue(bare.trips.isEmpty())
    }

    @Test
    fun `an open poll that asks me shows until I vote, by its first date`() {
        fun poll(id: String, kind: Poll.Kind, options: List<String>, asked: List<String>, closed: Boolean = false) =
            Poll(id, kind, "Question $id", options, false, asked, asked + "adult2", null, null, closed, null, null, "adult2", "Adult2", null)
        val now = Instant.parse("2026-11-16T10:00:00Z")
        val dates = poll("p1", Poll.Kind.Date, listOf(today.plusDays(9).toString(), "${today.plusDays(3)} 12:30"), listOf(me.id))
        val free = poll("p2", Poll.Kind.Choice, listOf("Pizza", "Crêpes"), listOf(me.id))
        val voted = poll("p3", Poll.Kind.Choice, listOf("A", "B"), listOf(me.id))
        val notMe = poll("p4", Poll.Kind.Choice, listOf("A", "B"), listOf("kid1"))
        val over = poll("p5", Poll.Kind.Choice, listOf("A", "B"), listOf(me.id), closed = true)
        val votes = mapOf("p3" to listOf(PollVote(me.id, choices = listOf(0), votedBy = me.id)))
        val shown = pollsToVote(me, listOf(dates, free, voted, notMe, over), votes, now, today)
        assertEquals(listOf("p1", "p2"), shown.map { it.poll.id })
        assertEquals(today.plusDays(3), shown[0].date)
        assertEquals(today, shown[1].date)
    }

    @Test
    fun `a poll where I voted stays with its leader until its end, then two days with its result`() {
        val now = Instant.parse("2026-11-16T10:00:00Z")
        fun poll(id: String, closesAt: Instant?, closed: Boolean = false, chosen: Int? = null) =
            Poll(id, Poll.Kind.Choice, "Question $id", listOf("Pizza", "Crêpes"), false, listOf(me.id), listOf(me.id), null, closesAt, closed, chosen, null, "adult2", "Adult2", null)
        val open = poll("p1", now.plusSeconds(86_400))
        val ended = poll("p2", now.minusSeconds(86_400))
        val longAgo = poll("p3", now.minusSeconds(3 * 86_400))
        val closedByHand = poll("p4", null, closed = true)
        val notVoted = poll("p5", now.plusSeconds(86_400))
        fun vote(member: String, choice: Int) = PollVote(member, choices = listOf(choice), votedBy = member)
        val votes = mapOf(
            "p1" to listOf(vote(me.id, 1), vote("adult2", 1), vote("kid1", 0)),
            "p2" to listOf(vote(me.id, 0)),
            "p3" to listOf(vote(me.id, 0)),
            "p4" to listOf(vote(me.id, 0)),
        )
        val shown = pollsVoted(me, listOf(open, ended, longAgo, closedByHand, notVoted), votes, now)
        assertEquals(listOf("p2", "p1"), shown.map { it.poll.id })
        assertTrue(shown[0].over)
        assertEquals(listOf(1), shown[1].leaders)
        assertEquals(2, shown[1].leaderVotes)
        assertTrue(!shown[1].over)
        // The one I voted on no longer asks me, but stays on the Accueil.
        assertTrue(pollsToVote(me, listOf(open), votes, now, today).isEmpty())
        val chosen = pollsVoted(me, listOf(poll("p6", now.minusSeconds(3600), closed = true, chosen = 0)), mapOf("p6" to listOf(vote(me.id, 1))), now)
        assertEquals(listOf(0), chosen.single().leaders)
    }

    @Test
    fun `the polls have their own block, shown only with a poll to show`() {
        val now = Instant.parse("2026-11-16T10:00:00Z")
        val poll = Poll("p1", Poll.Kind.Choice, "Question", listOf("A", "B"), false, listOf(me.id), listOf(me.id), null, null, false, null, null, "adult2", "Adult2", null)
        val toVote = pollsToVote(me, listOf(poll), emptyMap(), now, today)
        val withPoll = homeState(me, family, today, emptyList(), emptyMap(), emptyList(), null, emptyList(), toVote, pollsOn = true)
        assertTrue(withPoll.todos.isEmpty())
        assertEquals(listOf("p1"), withPoll.pollsToVote.map { it.poll.id })
        assertTrue(withPoll.showsPolls)
        val off = homeState(me, family, today, emptyList(), emptyMap(), emptyList(), null, emptyList(), toVote, pollsOn = false)
        assertTrue(!off.showsPolls)
        val none = homeState(me, family, today, emptyList(), emptyMap(), emptyList(), null, emptyList(), emptyList(), pollsOn = true)
        assertTrue(none.canAskPoll)
        assertTrue(!none.showsPolls)
    }
}
