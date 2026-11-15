package fr.familia.app.core.navigation

import fr.familia.app.core.model.FeatureFlags
import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun `bottom bar shows accueil, planning, gifts, entraide and family in that order`() {
        assertEquals(
            listOf(TopLevelDestination.Home, TopLevelDestination.Planning, TopLevelDestination.Gifts, TopLevelDestination.HelpRequests, TopLevelDestination.Members),
            TopLevelDestination.entries.toList(),
        )
    }

    @Test
    fun `app opens on the accueil once its module shows, on the planning before`() {
        assertEquals(TopLevelDestination.Planning, TopLevelDestination.startFor(homeOn = false))
        assertEquals(TopLevelDestination.Home, TopLevelDestination.startFor(homeOn = true))
    }

    @Test
    fun `each tab has its own route`() {
        val routes = TopLevelDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `the accueil, gifts and entraide tabs wait for their module, except for the admin principal`() {
        val off = TopLevelDestination.visible(FeatureFlags())
        assertEquals(listOf(TopLevelDestination.Planning, TopLevelDestination.Members), off)
        assertEquals(
            listOf(TopLevelDestination.Planning, TopLevelDestination.Gifts, TopLevelDestination.Members),
            TopLevelDestination.visible(FeatureFlags(mapOf("gifts" to true))),
        )
        assertEquals(TopLevelDestination.entries.toList(), TopLevelDestination.visible(FeatureFlags(isOwner = true)))
    }
}
