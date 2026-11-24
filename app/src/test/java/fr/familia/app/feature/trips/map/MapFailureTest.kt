package fr.familia.app.feature.trips.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFailureTest {
    private val token = "pk.test"

    @Test fun blankTokenSaysSo() = assertTrue(mapFailureReason("", true, null, null, true) is MapUnavailable.NoToken)

    @Test fun secretTokenIsNotPublic() = assertTrue(mapFailureReason("sk.abc", true, null, null, false) is MapUnavailable.BadToken)

    @Test fun viewErrorKeepsItsCause() {
        val failure = mapFailureReason(token, true, null, IllegalStateException(), false)
        assertTrue(failure is MapUnavailable.ViewFailed)
        assertTrue(failure.cause is IllegalStateException)
        assertEquals("IllegalStateException", failure.detail)
    }

    @Test fun offlineWinsOverTheDelay() {
        val failure = mapFailureReason(token, false, null, null, true)
        assertTrue(failure is MapUnavailable.NoNetwork)
        assertEquals("délai 15 s", failure.detail)
    }

    @Test fun styleErrorOfMapbox() {
        val failure = mapFailureReason(token, true, "STYLE: HTTP 401", null, false)
        assertTrue(failure is MapUnavailable.SdkError)
        assertEquals("STYLE: HTTP 401", failure.detail)
    }

    @Test fun delayWithTheLastError() {
        val failure = mapFailureReason(token, true, "TILE: timeout", null, true)
        assertTrue(failure is MapUnavailable.Timeout)
        assertEquals("délai 15 s, dernière erreur TILE: timeout", failure.detail)
    }

    @Test fun screenLineSaysWhereAndWhy() =
        assertEquals("Carte TripDetail : délai 15 s", mapFailureScreen("TripDetail", MapUnavailable.Timeout("délai 15 s")))

    @Test fun screenLineFitsTheRules() =
        assertTrue(mapFailureScreen("x".repeat(100), MapUnavailable.Timeout("délai 15 s")).length <= 80)

    @Test fun theTokenOfAnAddressNeverLeaves() {
        val line = mapErrorLine("STYLE", "401 https://api.mapbox.com/s?access_token=pk.abc&x=1")
        assertTrue("pk.abc" !in line)
        assertTrue(line.startsWith("STYLE: 401"))
        assertTrue(line.length <= 50)
    }
}
