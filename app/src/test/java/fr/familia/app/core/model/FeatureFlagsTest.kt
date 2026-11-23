package fr.familia.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagsTest {

    @Test
    fun `a module stays hidden from the family until it is switched on`() {
        assertFalse(FeatureFlags().isOn(Feature.NewsFeed))
        assertFalse(FeatureFlags(mapOf(Feature.NewsFeed.key to false)).isOn(Feature.NewsFeed))
        assertTrue(FeatureFlags(mapOf(Feature.NewsFeed.key to true)).isOn(Feature.NewsFeed))
    }

    @Test
    fun `the base is always on, for everyone, whatever config app says`() {
        assertTrue(FeatureFlags().isOn(Feature.Gifts))
        assertTrue(FeatureFlags(mapOf("trips" to false)).isOn(Feature.Trips))
        assertTrue(FeatureFlags(mapOf("gifts" to false)).isOn(Feature.Gifts))
        assertTrue(FeatureFlags(mapOf("comments" to false)).isOnForFamily(Feature.Comments))
    }

    @Test
    fun `the admin principal always sees every module`() {
        val flags = FeatureFlags(emptyMap(), isOwner = true)
        Feature.entries.forEach { assertTrue(flags.isOn(it)) }
        assertFalse(flags.isOnForFamily(Feature.NewsFeed))
    }

    @Test
    fun `every module has its own key`() {
        assertEquals(Feature.entries.size, Feature.entries.map { it.key }.toSet().size)
    }
}
