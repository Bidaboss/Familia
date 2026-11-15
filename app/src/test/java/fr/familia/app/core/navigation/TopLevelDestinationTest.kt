package fr.familia.app.core.navigation

import fr.familia.app.core.model.FeatureFlags
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

    @Test
    fun `the gifts tab waits for its module, except for the admin principal`() {
        val off = TopLevelDestination.visible(FeatureFlags())
        assertEquals(listOf(TopLevelDestination.Planning, TopLevelDestination.Members), off)
        assertEquals(TopLevelDestination.entries.toList(), TopLevelDestination.visible(FeatureFlags(mapOf("gifts" to true))))
        assertEquals(TopLevelDestination.entries.toList(), TopLevelDestination.visible(FeatureFlags(isOwner = true)))
    }
}
