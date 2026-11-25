package fr.familia.app.feature.trips.safety

import fr.familia.app.core.data.AlertTooSoonException
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.trips.FakeTrips
import fr.familia.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TripSafetyTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `France and unknown countries show no number`() {
        assertTrue(EmergencyNumbers.of("FR").isEmpty())
        assertTrue(EmergencyNumbers.of("fr").isEmpty())
        assertTrue(EmergencyNumbers.of(null).isEmpty())
        assertTrue(EmergencyNumbers.of("ZZ").isEmpty())
    }

    @Test
    fun `the countries of the table have their numbers, 112 first in Europe`() {
        assertEquals("112", EmergencyNumbers.of("ES").first().number)
        assertEquals("112", EmergencyNumbers.of("IT").first().number)
        assertEquals(listOf("911"), EmergencyNumbers.of("US").map { it.number })
        assertEquals("999", EmergencyNumbers.of("GB").first().number)
        assertTrue(EmergencyNumbers.of("DE").any { it.kind == EmergencyKind.Police && it.number == "110" })
    }

    @Test
    fun `abroad keeps the countries with numbers, in order, each once`() {
        assertEquals(listOf("ES", "IT"), EmergencyNumbers.abroad(listOf("es", "FR", "ZZ", "IT", "ES")))
    }

    @Test
    fun `a participant alerts the family once, signed with their id`() {
        val trips = FakeTrips()
        val viewModel = TripSafetyViewModel(trips, "t1", TestFamily.adult)
        viewModel.alertFamily()
        assertEquals(AlertState.Sent, viewModel.alert.value)
        assertEquals(listOf("t1" to "adult1"), trips.alerts)
        viewModel.dismiss()
        assertEquals(AlertState.Idle, viewModel.alert.value)
    }

    @Test
    fun `an alert refused by the rules says it is too soon`() {
        val viewModel = TripSafetyViewModel(FakeTrips(failWith = AlertTooSoonException()), "t1", TestFamily.adult)
        viewModel.alertFamily()
        assertEquals(AlertState.TooSoon, viewModel.alert.value)
    }

    @Test
    fun `another failure says it did not go`() {
        val viewModel = TripSafetyViewModel(FakeTrips(failWith = IllegalStateException("boom")), "t1", TestFamily.adult)
        viewModel.alertFamily()
        assertEquals(AlertState.Failed, viewModel.alert.value)
    }
}
