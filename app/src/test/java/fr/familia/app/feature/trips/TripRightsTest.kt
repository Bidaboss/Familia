package fr.familia.app.feature.trips

import fr.familia.app.core.model.TripArrival
import fr.familia.app.feature.events.TestFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TripRightsTest {

    private val family = TestFamily.family
    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun `the creator and the admin principal manage the trip, a participant does not`() {
        val trip = TestTrips.trip()
        assertTrue(canManageTrip(trip, TestFamily.adult, family))
        assertTrue(canManageTrip(trip, TestFamily.admin, family))
        assertFalse(canManageTrip(trip, TestFamily.otherAdult, family))
        assertTrue(isParticipant(trip, TestFamily.otherAdult))
        assertFalse(isParticipant(trip, TestFamily.kid))
    }

    @Test
    fun `an adult adds adults of any household and the children of their own`() {
        val ids = participantChoices(TestFamily.adult, family).map { it.id }
        assertEquals("adult1", ids.first())
        assertTrue(ids.containsAll(listOf("admin1", "adult2", "kid1")))
        assertFalse("kid2" in ids) // a child of another household
    }

    @Test
    fun `a child adds adults only, the admin principal anyone`() {
        assertEquals(listOf("kid1", "admin1", "adult1", "adult2"), participantChoices(TestFamily.kid, family).map { it.id })
        assertEquals(5, participantChoices(TestFamily.admin, family).size)
    }

    @Test
    fun `a trip is upcoming, ongoing then past`() {
        val trip = TestTrips.trip()
        assertEquals(TripPhase.Upcoming, tripPhase(trip, LocalDate.of(2027, 7, 9)))
        assertEquals(TripPhase.Ongoing, tripPhase(trip, LocalDate.of(2027, 7, 10)))
        assertEquals(TripPhase.Ongoing, tripPhase(trip, LocalDate.of(2027, 7, 20)))
        assertEquals(TripPhase.Past, tripPhase(trip, LocalDate.of(2027, 7, 21)))
    }

    @Test
    fun `the reminder is 3 hours after the planned arrival, in the phone's time zone`() {
        val step = TestTrips.step() // arrives on 10 July 2027 at 9 h 40
        val now = Instant.parse("2027-07-01T00:00:00Z")
        assertEquals(Instant.parse("2027-07-10T10:40:00Z"), reminderTime(step, paris, now))
        assertEquals(Instant.parse("2027-07-10T12:40:00Z"), reminderTime(step, ZoneId.of("UTC"), now))
    }

    @Test
    fun `no reminder without an arrival time, once arrived, or once past`() {
        val step = TestTrips.step()
        val now = Instant.parse("2027-07-01T00:00:00Z")
        assertNull(reminderTime(step.copy(arriveTime = null), paris, now))
        assertNull(reminderTime(step.copy(arrival = TripArrival(now, "adult1", "Adult1")), paris, now))
        assertNull(reminderTime(step, paris, Instant.parse("2027-07-10T11:00:00Z")))
    }

    @Test
    fun `an arrival time alone is on the day of departure`() {
        val step = TestTrips.step().copy(arriveDate = null, arriveTime = LocalTime.of(23, 0))
        assertEquals(Instant.parse("2027-07-11T00:00:00Z"), reminderTime(step, paris, Instant.parse("2027-07-01T00:00:00Z")))
    }

    @Test
    fun `names are joined in French, and those who left are skipped`() {
        assertEquals("", joinNames(emptyList()))
        assertEquals("Lou et Sam", joinNames(listOf("Lou", "Sam")))
        assertEquals("Lou, Noé et Sam", joinNames(listOf("Lou", "Noé", "Sam")))
        assertEquals(listOf("Adult1", "Adult2"), firstNames(listOf("adult1", "gone", "adult2"), family))
    }
}
