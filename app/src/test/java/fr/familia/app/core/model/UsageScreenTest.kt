package fr.familia.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageScreenTest {
    private fun route(name: String) = "fr.familia.app.core.navigation.$name"

    @Test
    fun eachScreenCountsForItsPartOfTheApp() {
        assertEquals(UsageScreen.Home, UsageScreen.forRoute(route("HomeRoute")))
        assertEquals(UsageScreen.Planning, UsageScreen.forRoute(route("EventDetailDestination/{eventId}")))
        assertEquals(UsageScreen.Gifts, UsageScreen.forRoute(route("GiftListDestination/{listId}")))
        assertEquals(UsageScreen.Gifts, UsageScreen.forRoute(route("NotebookIdeaDestination?entryId={entryId}")))
        assertEquals(UsageScreen.HelpRequests, UsageScreen.forRoute(route("HelpRequestDetailDestination/{requestId}")))
        assertEquals(UsageScreen.Family, UsageScreen.forRoute(route("HelpDestination")))
        assertEquals(UsageScreen.Family, UsageScreen.forRoute(route("UsageDestination")))
        assertEquals(UsageScreen.GoodNews, UsageScreen.forRoute(route("GoodNewsDetailDestination/{postId}")))
        assertEquals(UsageScreen.Other, UsageScreen.forRoute(route("SomethingNew")))
        assertNull(UsageScreen.forRoute(null))
    }

    @Test
    fun keysAreUniqueAndKnownToTheRules() {
        val rulesKeys = setOf("home", "planning", "gifts", "help", "goodNews", "weeklyQuestion", "trips", "family", "other")
        assertEquals(rulesKeys, UsageScreen.entries.map { it.key }.toSet())
        assertEquals(UsageScreen.entries.size, rulesKeys.size)
    }
}
