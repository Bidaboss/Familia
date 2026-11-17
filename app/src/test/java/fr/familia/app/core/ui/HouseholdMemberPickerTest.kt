package fr.familia.app.core.ui

import androidx.compose.ui.state.ToggleableState
import org.junit.Assert.assertEquals
import org.junit.Test

class HouseholdMemberPickerTest {

    private val home = listOf("a", "b", "c")

    @Test
    fun `a household is on, half or off from its members`() {
        assertEquals(ToggleableState.Off, householdState(setOf("x"), home))
        assertEquals(ToggleableState.Indeterminate, householdState(setOf("a", "x"), home))
        assertEquals(ToggleableState.On, householdState(setOf("a", "b", "c", "x"), home))
        assertEquals(ToggleableState.Off, householdState(setOf("a"), emptyList()))
    }

    @Test
    fun `ticking a household picks all its members, then none, and leaves the others alone`() {
        assertEquals(setOf("x", "a", "b", "c"), toggleHousehold(setOf("x", "a"), home))
        assertEquals(setOf("x"), toggleHousehold(setOf("x", "a", "b", "c"), home))
    }

    @Test
    fun `a member toggles alone`() {
        assertEquals(setOf("a", "b"), toggleMember(setOf("a"), "b"))
        assertEquals(setOf("a"), toggleMember(setOf("a", "b"), "b"))
    }
}
