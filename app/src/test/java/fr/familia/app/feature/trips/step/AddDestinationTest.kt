package fr.familia.app.feature.trips.step

import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.Trip
import fr.familia.app.core.place.Place
import fr.familia.app.core.place.PlaceScale
import fr.familia.app.core.place.placeScale
import fr.familia.app.core.place.placeShortName
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddDestinationTest {
    private val trip = Trip(
        id = "t",
        title = "Été",
        startDate = LocalDate.of(2027, 8, 2),
        endDate = LocalDate.of(2027, 8, 9),
        participantIds = listOf("a"),
        readerIds = emptyList(),
        creatorId = "a",
        creatorName = "Lou",
    )

    @Test fun aTownACountryAndARegionFromTheGeocoder() {
        assertEquals(PlaceScale.City, placeScale("Séville", "Séville", "Séville", "Andalousie", "Espagne"))
        assertEquals(PlaceScale.Country, placeScale("Espagne", null, null, null, "Espagne"))
        assertEquals(PlaceScale.Region, placeScale("Bretagne", null, null, "Bretagne", "France"))
        assertEquals("Andalousie", placeShortName(PlaceScale.Region, "Andalousie", null, "Andalousie", "Espagne"))
    }

    @Test fun aPlaceFoundKeepsItsShortNameAndCountry() {
        val found = choiceOf(Place(37.39, -5.98, address = "Séville, Espagne", countryCode = "ES", scale = PlaceScale.City, shortName = "Séville"))
        assertEquals(DestinationChoice("Séville", 37.39, -5.98, "ES", PlaceScale.City), found)
    }

    @Test fun aKnownPlaceNamedAsItsCountryIsACountry() {
        assertEquals(PlaceScale.Country, knownChoice("Portugal", 39.5, -8.0, "PT").scale)
        assertEquals(PlaceScale.City, knownChoice("Quimper", 48.0, -4.1, "FR").scale)
    }

    @Test fun beforeThreeLettersTheKnownPlacesThenTheSearch() {
        val known = listOf(knownChoice("Quimper", 48.0, -4.1, "FR"), knownChoice("Portugal", 39.5, -8.0, "PT"))
        val found = listOf(DestinationChoice("Nice", 43.7, 7.26, "FR", PlaceScale.City))
        assertEquals(listOf("Quimper"), shownChoices("Qu", PlaceScale.City, known, found).map { it.name })
        assertEquals(listOf("Portugal"), shownChoices("", PlaceScale.Country, known, null).map { it.name })
        assertEquals(listOf("Nice"), shownChoices("Nic", PlaceScale.City, known, found).map { it.name })
    }

    @Test fun eachNameOnce() =
        assertEquals(1, distinctChoices(listOf(knownChoice("Nice", 1.0, 1.0, "FR"), knownChoice("nice", 2.0, 2.0, "FR"))).size)

    @Test fun oneNightFromTheDayOfThePlus() {
        val step = newDestinationStep("s", trip, LocalDate.of(2027, 8, 4), DestinationChoice("Séville", 37.39, -5.98, "ES", PlaceScale.City))
        assertEquals(StepType.Lodging, step.type)
        assertEquals(LocalDate.of(2027, 8, 4), step.departDate)
        assertEquals(LocalDate.of(2027, 8, 5), step.arriveDate)
        assertEquals(37.39, step.lat!!, 0.0)
    }

    @Test fun onTheDayOfTheReturnItTakesTheLastNight() {
        val step = newDestinationStep("s", trip, trip.endDate, DestinationChoice("Nice", 43.7, 7.26, "FR", PlaceScale.City))
        assertEquals(LocalDate.of(2027, 8, 8), step.departDate)
        assertEquals(LocalDate.of(2027, 8, 9), step.arriveDate)
        assertNull(step.departTime)
    }
}
