package fr.familia.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ConsumptionTest {

    private val day = LocalDate.of(2026, 11, 20)

    @Test
    fun `the gauges follow the free limits, the size only when known`() {
        val quiet = Consumption(day, reads = 25_000, writes = 2_000, deletes = 0)
        assertEquals(listOf(Consumption.Limit.Reads, Consumption.Limit.Writes, Consumption.Limit.Deletes), quiet.gauges.map { it.limit })
        assertEquals(0.5f, quiet.gauges.first().share, 0.0001f)
        val sized = quiet.copy(storageBytes = 512L * 1024 * 1024)
        assertEquals(0.5f, sized.gauges.last().share, 0.0001f)
    }

    @Test
    fun `from 80 percent a gauge is near its limit, and it never passes full`() {
        val busy = Consumption(day, reads = 40_000, writes = 15_999, deletes = 90_000)
        assertTrue(busy.gauges[0].near)
        assertFalse(busy.gauges[1].near)
        assertEquals(1f, busy.gauges[2].share, 0f)
    }

    @Test
    fun `the photos of Cloud Storage have their gauges, when Monitoring gave them`() {
        val withPhotos = Consumption(day, photoBytes = 4_100_000_000, monthDownloadBytes = 1_200_000_000)
        assertEquals(
            listOf(Consumption.Limit.Reads, Consumption.Limit.Writes, Consumption.Limit.Deletes, Consumption.Limit.Photos, Consumption.Limit.Downloads),
            withPhotos.gauges.map { it.limit },
        )
        assertTrue(withPhotos.gauges[3].near)
        assertFalse(withPhotos.gauges[4].near)
        assertEquals(0.012f, withPhotos.gauges[4].share, 0.0001f)
    }

    @Test
    fun `the cost warns past 50 cents, like the server`() {
        assertFalse(Consumption(day, monthCost = 0.5).costNear)
        assertTrue(Consumption(day, monthCost = 0.51).costNear)
    }
}
