package fr.familia.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarFeedTokenTest {

    @Test
    fun `a token is 43 url-safe characters, as the rules ask`() {
        repeat(50) {
            val token = newCalendarFeedToken()
            assertEquals(43, token.length)
            assertTrue(token, token.matches(Regex("^[A-Za-z0-9_-]{43}$")))
        }
    }

    @Test
    fun `two tokens differ`() {
        assertNotEquals(newCalendarFeedToken(), newCalendarFeedToken())
    }

    @Test
    fun `the link names the function in europe-west1 and carries the token`() {
        assertEquals(
            "https://europe-west1-familia-93286.cloudfunctions.net/calendarFeed?t=abc",
            calendarFeedUrl("abc"),
        )
    }
}
