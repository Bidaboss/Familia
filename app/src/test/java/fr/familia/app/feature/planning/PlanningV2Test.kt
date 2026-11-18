package fr.familia.app.feature.planning

import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Trip
import fr.familia.app.feature.home.DayTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PlanningV2Test {

    private val today = LocalDate.of(2026, 11, 18) // A Wednesday.

    @Test
    fun `the title of a week says its two ends in French`() {
        assertEquals("16" to "22 novembre", weekRange(LocalDate.of(2026, 11, 16)))
        assertEquals("30 novembre" to "6 décembre", weekRange(LocalDate.of(2026, 11, 30)))
        assertEquals("28 décembre 2026" to "3 janvier 2027", weekRange(LocalDate.of(2026, 12, 28)))
    }

    @Test
    fun `the first day of a month is said 1er`() {
        assertEquals("1er" to "7 mars", weekRange(LocalDate.of(2027, 3, 1)))
        assertEquals("23 février" to "1er mars", weekRange(LocalDate.of(2026, 2, 23)))
    }

    @Test
    fun `the hour block shows the hours big and the minutes under`() {
        assertEquals("18" to "h00", hourParts(LocalTime.of(18, 0)))
        assertEquals("9" to "h05", hourParts(LocalTime.of(9, 5)))
    }

    @Test
    fun `a trip of three days shows which day it is`() {
        val trip = Trip("t", "Ski", LocalDate.of(2026, 11, 27), LocalDate.of(2026, 11, 29), listOf("a"), listOf("a"), "a", "Camille")
        assertEquals(1 to 3, tripDay(trip, LocalDate.of(2026, 11, 27)))
        assertEquals(2 to 3, tripDay(trip, LocalDate.of(2026, 11, 28)))
        assertEquals("27" to "29 nov.", tripRange(trip))
    }

    @Test
    fun `the week shows the chosen day then the next busy days of the week`() {
        val busy = setOf(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 25))
        assertEquals(
            listOf(today, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21)),
            weekDaysShown(today, busy),
        )
    }

    @Test
    fun `the arrows move a week or a month and come back to today`() {
        assertEquals(LocalDate.of(2026, 11, 23), planningMove(PlanningMode.Week, today, today, forward = true))
        assertEquals(today, planningMove(PlanningMode.Week, LocalDate.of(2026, 11, 25), today, forward = false))
        assertEquals(LocalDate.of(2026, 12, 1), planningMove(PlanningMode.Month, today, today, forward = true))
        assertEquals(today, planningMove(PlanningMode.Month, LocalDate.of(2026, 12, 1), today, forward = false))
    }

    @Test
    fun `no going back before the period of today, at most a year ahead`() {
        assertFalse(canMoveBack(PlanningMode.Week, today, today))
        assertTrue(canMoveBack(PlanningMode.Week, LocalDate.of(2026, 11, 23), today))
        assertFalse(canMoveBack(PlanningMode.Month, today, today))
        assertTrue(canMoveForward(PlanningMode.Month, today, today))
        assertFalse(canMoveForward(PlanningMode.Month, LocalDate.of(2027, 11, 1), today))
    }

    @Test
    fun `a party is terracotta, the rest violet, as in the frise`() {
        fun event(title: String) = Event("e", title, today, null, null, null, EventTarget.ForFamily, "a", "Camille")
        assertEquals(DayTone.Party, eventTone(event("Repas de famille")))
        assertEquals(DayTone.Organise, eventTone(event("Judo")))
    }
}
