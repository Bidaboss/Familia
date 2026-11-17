package fr.familia.app.core.data

import fr.familia.app.core.model.Consumption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ConsumptionCostsTest {

    @Test
    fun `the detail of the month is read limit by limit, a bad value left out`() {
        val costs = monthCostsOf(mapOf("reads" to 0.06, "photos" to 0L, "downloads" to "abîmé", "autre" to 3.0))
        assertEquals(mapOf(Consumption.Limit.Reads to 0.06, Consumption.Limit.Photos to 0.0), costs)
        assertEquals(emptyMap<Consumption.Limit, Double>(), monthCostsOf(null))
    }

    @Test
    fun `each line of the card carries its cost, none for a day read before the detail`() {
        val day = LocalDate.of(2026, 11, 20)
        val withDetail = Consumption(day, reads = 60_000, photoBytes = 86_000_000, monthCosts = mapOf(Consumption.Limit.Reads to 0.06, Consumption.Limit.Photos to 0.0))
        assertEquals(0.06, withDetail.gauges.first { it.limit == Consumption.Limit.Reads }.cost!!, 1e-9)
        assertEquals(0.0, withDetail.gauges.first { it.limit == Consumption.Limit.Photos }.cost!!, 1e-9)
        assertNull(withDetail.gauges.first { it.limit == Consumption.Limit.Writes }.cost)
        assertNull(Consumption(day, reads = 60_000).gauges.first().cost)
    }
}
