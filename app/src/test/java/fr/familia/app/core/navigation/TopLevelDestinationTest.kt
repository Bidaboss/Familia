package fr.familia.app.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun `bottom bar shows planning, gifts and family in that order`() {
        assertEquals(
            listOf(TopLevelDestination.Planning, TopLevelDestination.Gifts, TopLevelDestination.Members),
            TopLevelDestination.entries.toList(),
        )
    }

    @Test
    fun `app opens on the planning tab`() {
        assertEquals(TopLevelDestination.Planning, TopLevelDestination.start)
    }

    @Test
    fun `each tab has its own route`() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }
}
