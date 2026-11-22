package fr.familia.app.feature.trips.packing

import fr.familia.app.core.model.PackingLine
import fr.familia.app.core.model.TripItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PackingCountTest {
    private val free = TripItem("a", "Tente", assigneeId = null, assigneeName = null, done = false)
    private val taken = TripItem("b", "Glacière", assigneeId = "adult1", assigneeName = "Camille", done = false)
    private val finished = TripItem("c", "Billets", assigneeId = null, assigneeName = null, done = true)
    private val mine = BagUiState(BagOwner("adult1", "Camille", isMe = true), listOf(PackingLine("Maillot"), PackingLine("Chargeur", done = true)))
    private val child = BagUiState(BagOwner("kid1", "Noé", isMe = false), listOf(PackingLine("Doudou")))

    @Test
    fun `free lines and what is left in my bag are counted`() {
        assertEquals(2, packingCount(listOf(free, taken, finished), PackingUiState(listOf(mine, child)), packingOn = true))
    }

    @Test
    fun `a child bag does not count`() {
        assertEquals(0, packingCount(emptyList(), PackingUiState(listOf(child)), packingOn = true))
    }

    @Test
    fun `without packing only the checklist counts`() {
        assertEquals(1, packingCount(listOf(free, taken), PackingUiState(listOf(mine)), packingOn = false))
    }
}
