package fr.familia.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagsTest {

    @Test
    fun `a module stays hidden from the family until it is switched on`() {
        assertFalse(FeatureFlags().isOn(Feature.Trips))
        assertFalse(FeatureFlags(mapOf("trips" to false)).isOn(Feature.Trips))
        assertTrue(FeatureFlags(mapOf("trips" to true)).isOn(Feature.Trips))
    }

    @Test
    fun `the base is always on, for everyone, whatever config app says`() {
        assertTrue(FeatureFlags().isOn(Feature.Gifts))
        assertTrue(FeatureFlags(mapOf("gifts" to false)).isOn(Feature.Gifts))
        assertTrue(FeatureFlags(mapOf("comments" to false)).isOnForFamily(Feature.Comments))
    }

    @Test
    fun `the admin principal always sees every module`() {
        val flags = FeatureFlags(emptyMap(), isOwner = true)
        Feature.entries.forEach { assertTrue(flags.isOn(it)) }
        assertFalse(flags.isOnForFamily(Feature.Trips))
    }

    @Test
    fun `every module has its own key`() {
        assertEquals(Feature.entries.size, Feature.entries.map { it.key }.toSet().size)
    }
}
