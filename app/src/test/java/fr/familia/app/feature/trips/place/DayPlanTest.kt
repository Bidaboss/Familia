package fr.familia.app.feature.trips.place

import fr.familia.app.core.model.TripPlace
import fr.familia.app.core.model.TripPlaceKind
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Récit 122: a stay laid out by day. Fictional places of a stay from Sunday 11 to Friday 16 April 2027. */
class DayPlanTest {
    private fun day(d: Int) = LocalDate.of(2027, 4, d)
    private val start = day(11)
    private val end = day(16)

    private fun place(id: String, kind: TripPlaceKind, day: LocalDate? = null, at: LocalTime? = null, stepId: String = "s1", checkIn: LocalDate? = null, checkOut: LocalDate? = null) =
        TripPlace(id, stepId, kind, id, null, null, null, at, null, day = day, checkIn = checkIn, checkOut = checkOut)

    @Test
    fun theStayHasOneSectionPerDayWithItsPlacesByHourThenToPlace() {
        val places = listOf(
            place("Crêperie", TripPlaceKind.Restaurant, day(12), LocalTime.of(12, 15)),
            place("Zoo", TripPlaceKind.Spot, day(12), LocalTime.of(9, 30)),
            place("Bistrot", TripPlaceKind.Restaurant, day(11), LocalTime.of(19, 30)),
            place("Baie", TripPlaceKind.Spot),
            place("Ailleurs", TripPlaceKind.Spot, day(12), stepId = "s2"),
        )
        val plan = dayPlan("s1", start, end, places)
        assertEquals(6, plan.days.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), plan.days.map { it.number })
        assertEquals(listOf("Bistrot"), plan.days[0].places.map { it.id })
        assertEquals(listOf("Zoo", "Crêperie"), plan.days[1].places.map { it.id })
        assertEquals(emptyList<String>(), plan.days[3].places.map { it.id })
        assertEquals(listOf("Baie"), plan.toPlace.map { it.id })
    }

    @Test
    fun aDayTheStayNoLongerHasIsToPlaceAgainWithoutAnyWrite() {
        val plan = dayPlan("s1", start, day(13), listOf(place("Phare", TripPlaceKind.Spot, day(15))))
        assertEquals(3, plan.days.size)
        assertEquals(listOf("Phare"), plan.toPlace.map { it.id })
    }

    @Test
    fun anAccommodationCoversTheWholeStayUnlessItsNightsFitInIt() {
        val plan = dayPlan(
            "s1", start, end,
            listOf(
                place("Camping", TripPlaceKind.Lodging),
                place("Hôtel", TripPlaceKind.Lodging, checkIn = day(13), checkOut = day(15)),
                place("Trop loin", TripPlaceKind.Lodging, checkIn = day(13), checkOut = day(20)),
            ),
        )
        assertEquals(listOf("Camping", "Hôtel", "Trop loin"), plan.lodgings.map { it.place.id })
        assertEquals(start to end, plan.lodgings[0].checkIn to plan.lodgings[0].checkOut)
        assertEquals(day(13) to day(15), plan.lodgings[1].checkIn to plan.lodgings[1].checkOut)
        // A departure after the stay is not kept: up to the end of the stay.
        assertEquals(day(13) to end, plan.lodgings[2].checkIn to plan.lodgings[2].checkOut)
        assertEquals(emptyList<TripPlace>(), plan.toPlace)
    }

    @Test
    fun aStayOfOneDayKeepsOneNightAndOneDay() {
        assertEquals(listOf(start), stayDays(start, start))
        val nights = lodgingNights(place("Gîte", TripPlaceKind.Lodging), start, start)
        assertEquals(start to day(12), nights.checkIn to nights.checkOut)
    }
}
