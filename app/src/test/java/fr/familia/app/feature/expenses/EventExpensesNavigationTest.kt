package fr.familia.app.feature.expenses

import fr.familia.app.core.model.UsageScreen
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The accounts of an event (PRODUIT-57) open from its card: the three destinations carry the event
 * and the expense, and their time counts for the planning, not for the trips.
 */
class EventExpensesNavigationTest {

    @Test
    fun `the destinations of an event carry its id and the expense`() {
        val list = EventExpensesDestination("e1")
        val new = EventExpenseEditDestination("e1")
        val edit = EventExpenseEditDestination("e1", "x1")
        val history = EventExpenseHistoryDestination("e1", "x1")
        assertEquals(list, Json.decodeFromString<EventExpensesDestination>(Json.encodeToString(list)))
        assertEquals(null, Json.decodeFromString<EventExpenseEditDestination>(Json.encodeToString(new)).expenseId)
        assertEquals(edit, Json.decodeFromString<EventExpenseEditDestination>(Json.encodeToString(edit)))
        assertEquals(history, Json.decodeFromString<EventExpenseHistoryDestination>(Json.encodeToString(history)))
    }

    @Test
    fun `time on the accounts of an event counts for the planning, those of a trip for the trips`() {
        val pkg = "fr.familia.app.feature.expenses"
        assertEquals(UsageScreen.Planning, UsageScreen.forRoute("$pkg.EventExpensesDestination/{eventId}"))
        assertEquals(UsageScreen.Planning, UsageScreen.forRoute("$pkg.EventExpenseEditDestination/{eventId}?expenseId={expenseId}"))
        assertEquals(UsageScreen.Planning, UsageScreen.forRoute("$pkg.EventExpenseHistoryDestination/{eventId}/{expenseId}"))
        assertEquals(UsageScreen.Trips, UsageScreen.forRoute("$pkg.TripExpensesDestination/{tripId}"))
    }
}
