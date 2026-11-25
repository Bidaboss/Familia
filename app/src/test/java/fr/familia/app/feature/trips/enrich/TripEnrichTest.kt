package fr.familia.app.feature.trips.enrich

import fr.familia.app.core.model.StepType
import fr.familia.app.core.model.TripPlaceKind
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.data.TripPlaceRepository
import fr.familia.app.core.model.TripPlace
import fr.familia.app.core.place.Place
import fr.familia.app.core.place.PlaceLocator
import fr.familia.app.feature.trips.FakeTrips
import fr.familia.app.feature.trips.TestTrips
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import fr.familia.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TripEnrichTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val trip = TestTrips.trip().copy(title = "Titre privé")
    private val lisbon = TripStep("s1", StepType.Lodging, "Lisbonne", LocalDate.of(2027, 7, 10), null, LocalDate.of(2027, 7, 14), null, null)
    private val steps = listOf(lisbon)

    private class FakePlaces : TripPlaceRepository {
        val saved = mutableListOf<TripPlace>()
        private var next = 0
        override fun observePlaces(tripId: String): Flow<List<TripPlace>> = MutableStateFlow(saved.toList())
        override suspend fun getPlace(tripId: String, placeId: String): TripPlace? = saved.firstOrNull { it.id == placeId }
        override fun newPlaceId(tripId: String): String = "p${++next}"
        override suspend fun savePlace(tripId: String, place: TripPlace, isNew: Boolean) {
            saved += place
        }
        override suspend fun deletePlace(tripId: String, placeId: String) = Unit
    }

    private fun read(json: String, target: EnrichTarget = EnrichTarget.Trip) = readEnrichAnswer(json, trip, steps, target)

    @Test
    fun `the prompt carries the destinations and dates, never the title`() {
        val prompt = enrichPrompt(trip, steps, EnrichTarget.Trip)
        assertTrue(prompt.contains("Lisbonne : du 2027-07-10 au 2027-07-14"))
        assertTrue(prompt.contains("\"destinations\""))
        assertFalse(prompt.contains("Titre privé"))
        val one = enrichPrompt(trip, steps, EnrichTarget.Destination("s1"))
        assertTrue(one.contains("à Lisbonne"))
        assertTrue(one.contains("\"lieux\""))
        assertFalse(one.contains("\"destinations\""))
    }

    @Test
    fun `the AI asks to confirm before giving the JSON to copy`() {
        val prompt = enrichPrompt(trip, steps, EnrichTarget.Trip)
        assertTrue(prompt.contains("- Demande confirmation des étapes proposées."))
        assertTrue(prompt.contains("- Je peux te challenger dans tes propositions."))
        assertTrue(prompt.contains("Lorsque je valide, propose un objet JSON valide"))
        assertTrue(prompt.contains("bouton copier"))
        assertFalse(prompt.contains("UNIQUEMENT"))
    }

    @Test
    fun `a good answer, wrapped in a code block, enriches the known destination and adds a new one`() {
        val answer = """
            Voici :
            ```json
            {"destinations": [
              {"ville": "lisbonne", "du": "2027-07-01", "au": "2027-07-02", "lieux": [
                {"sorte": "Restaurant", "nom": "Chez Ana", "adresse": "Rua A 1", "heure": "20h30", "note": "Poisson", "cout": 60},
                {"sorte": "spot", "nom": "Tour", "cout": "gratuit"}
              ]},
              {"ville": "Porto", "du": "2027-07-14", "au": "2027-07-18", "lieux": [{"sorte": "hébergement", "nom": "Hôtel Douro"}], "autre": 1}
            ]}
            ```
        """.trimIndent()
        val reading = read(answer) as EnrichReading.Read
        assertEquals(2, reading.destinations.size)
        val known = reading.destinations[0]
        assertEquals("s1", known.existingStepId)
        assertNull(known.start)
        assertEquals(LocalTime.of(20, 30), known.places[0].arriveTime)
        assertEquals(60.0, known.places[0].cost!!, 0.0)
        assertNull(known.places[1].cost)
        val porto = reading.destinations[1]
        assertTrue(porto.isNew)
        assertEquals(TripPlaceKind.Lodging, porto.places.single().kind)
        assertEquals(4, reading.count)
        val stay = porto.toStay("new")
        assertEquals(StepType.Lodging, stay.type)
        assertEquals(LocalDate.of(2027, 7, 18), stay.arriveDate)
    }

    @Test
    fun `links, bad dates, dates outside the trip and unknown kinds are left out and said`() {
        val answer = """{"destinations": [
            {"ville": "Faro", "du": "2027-07-21", "au": "2027-07-23"},
            {"ville": "Sintra", "du": "2027-07-15", "au": "2027-07-15"},
            {"ville": "Évora", "du": "2027-07-15", "au": "2027-07-16", "lieux": [
              {"sorte": "restaurant", "nom": "Bon", "note": "voir https://exemple.pt"},
              {"sorte": "restaurant", "nom": "Site", "adresse": "www.exemple.pt"},
              {"sorte": "restaurant", "nom": "Lien", "note": "exemple.com"},
              {"sorte": "musee", "nom": "Musée"},
              {"sorte": "spot", "nom": "  Place‮   centrale  "}
            ]}
        ]}"""
        val reading = read(answer) as EnrichReading.Read
        val evora = reading.destinations.single()
        assertEquals("Place centrale", evora.places.single().name)
        val reasons = reading.skipped.map { it.reason }
        assertEquals(listOf(SkipReason.OutsideTrip, SkipReason.BadDates, SkipReason.Link, SkipReason.Link, SkipReason.Link, SkipReason.BadKind), reasons)
    }

    @Test
    fun `a new stay never shares a night with a stay of the trip or of the same answer`() {
        val answer = """{"destinations": [
            {"ville": "Sintra", "du": "2027-07-13", "au": "2027-07-15"},
            {"ville": "Porto", "du": "2027-07-14", "au": "2027-07-17"},
            {"ville": "Braga", "du": "2027-07-16", "au": "2027-07-18"},
            {"ville": "Faro", "du": "2027-07-17", "au": "2027-07-19"}
        ]}"""
        val reading = read(answer) as EnrichReading.Read
        assertEquals(listOf("Porto", "Faro"), reading.destinations.map { it.city })
        assertEquals(listOf(SkipReason.Overlap, SkipReason.Overlap), reading.skipped.map { it.reason })
        assertEquals(listOf("Sintra", "Braga"), reading.skipped.map { it.what })
    }

    @Test
    fun `anything else than the JSON asked is refused, and the limits hold`() {
        assertEquals(EnrichReading.Refused.Empty, read("   "))
        assertEquals(EnrichReading.Refused.NotJson, read("Voici des idées : Lisbonne, Porto."))
        assertEquals(EnrichReading.Refused.NotJson, read("{\"destinations\": oops}"))
        assertEquals(EnrichReading.Refused.NotJson, read("{\"lieux\": []}"))
        assertEquals(EnrichReading.Refused.TooLong, read("{" + " ".repeat(TripEnrich.MAX_CHARS) + "}"))
        assertEquals(EnrichReading.Refused.NothingToAdd, read("{\"destinations\": []}"))
        val many = (1..20).joinToString(",") { """{"sorte": "spot", "nom": "Spot $it"}""" }
        val reading = read("""{"lieux": [$many]}""", EnrichTarget.Destination("s1")) as EnrichReading.Read
        assertEquals(TripEnrich.MAX_PLACES_PER_DESTINATION, reading.destinations.single().places.size)
        assertEquals(SkipReason.TooMany, reading.skipped.single().reason)
        val long = read("""{"lieux": [{"sorte": "spot", "nom": "${"a".repeat(300)}"}]}""", EnrichTarget.Destination("s1")) as EnrichReading.Read
        assertEquals(TripEnrich.NAME_MAX, long.destinations.single().places.single().name.length)
    }

    @Test
    fun `a place under an unticked new destination is not counted`() {
        val reading = read("""{"destinations": [{"ville": "Porto", "du": "2027-07-14", "au": "2027-07-18", "lieux": [{"sorte": "spot", "nom": "Ribeira"}]}]}""") as EnrichReading.Read
        val all = allKeys(reading)
        assertEquals(2, chosenCount(reading, all))
        assertEquals(0, chosenCount(reading, all - destinationKey(0)))
        assertEquals(1, chosenCount(reading, all - placeKey(0, 0)))
    }

    @Test
    fun `only the ticked lines are written, the new stay before its places`() {
        val trips = FakeTrips(steps = mapOf(trip.id to steps))
        val places = FakePlaces()
        val vm = TripEnrichViewModel(trips, places, null, trip.id)
        vm.setAnswer("""{"destinations": [
            {"ville": "Lisbonne", "lieux": [{"sorte": "restaurant", "nom": "Chez Ana"}, {"sorte": "spot", "nom": "Tour"}]},
            {"ville": "Porto", "du": "2027-07-14", "au": "2027-07-18", "lieux": [{"sorte": "spot", "nom": "Ribeira"}]}
        ]}""")
        vm.read(trip, steps)
        vm.toggle(placeKey(0, 1))
        vm.add()
        assertEquals(3, vm.state.value.added)
        assertFalse(vm.state.value.saveFailed)
        val porto = trips.savedSteps.single().first
        assertEquals("Porto", porto.place)
        assertEquals(StepType.Lodging, porto.type)
        assertEquals(listOf("Chez Ana" to "s1", "Ribeira" to porto.id), places.saved.map { it.name to it.stepId })
        assertTrue(places.saved.all { it.lat == null && it.lng == null })
    }

    private class FakeLocator(private val points: Map<String, Place>) : PlaceLocator {
        val asked = mutableListOf<String>()
        override suspend fun currentPosition(): Pair<Double, Double>? = null
        override suspend fun addressOf(lat: Double, lng: Double): String? = null
        override suspend fun find(query: String): Place? = points[query].also { asked += query }
        override suspend fun expand(shortLink: String): String? = null
    }

    @Test
    fun `a place gets a point near its destination, never one far away`() {
        val placed = lisbon.copy(lat = 38.72, lng = -9.14)
        val trips = FakeTrips(steps = mapOf(trip.id to listOf(placed)))
        val places = FakePlaces()
        val locator = FakeLocator(
            mapOf(
                "Rua Augusta 1, Lisbonne" to Place(38.71, -9.137),
                // A « Tour » of the same name in Paris: too far from Lisbon to be the right one.
                "Tour, Lisbonne" to Place(48.858, 2.294),
            ),
        )
        val vm = TripEnrichViewModel(trips, places, locator, trip.id)
        vm.setAnswer("""{"destinations": [{"ville": "Lisbonne", "lieux": [
            {"sorte": "restaurant", "nom": "Chez Ana", "adresse": "Rua Augusta 1"},
            {"sorte": "spot", "nom": "Tour"},
            {"sorte": "spot", "nom": "Inconnu"}
        ]}]}""")
        vm.read(trip, listOf(placed))
        // The point of the destination travels with the reading (retour C2 de #579).
        assertEquals(38.72 to -9.14, (vm.state.value.reading as EnrichReading.Read).destinations.single().anchor)
        vm.add()
        assertEquals(listOf("Rua Augusta 1, Lisbonne", "Tour, Lisbonne", "Inconnu, Lisbonne"), locator.asked)
        val byName = places.saved.associateBy { it.name }
        assertEquals(38.71, byName.getValue("Chez Ana").lat!!, 1e-9)
        assertNull(byName.getValue("Tour").lat)
        assertNull(byName.getValue("Inconnu").lat)
        assertTrue(nearEnough(38.71 to -9.137, 38.72 to -9.14))
        assertFalse(nearEnough(48.858 to 2.294, 38.72 to -9.14))
    }
}
