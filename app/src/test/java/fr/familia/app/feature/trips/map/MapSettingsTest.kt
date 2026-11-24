package fr.familia.app.feature.trips.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapSettingsTest {
    @Test
    fun `a saved look comes back`() {
        assertEquals(MapLook.Streets, MapSettingsStore.lookOf("Streets"))
        assertEquals(MapLook.Satellite, MapSettingsStore.lookOf("Satellite"))
    }

    @Test
    fun `nothing saved, or an unknown look, gives Satellite`() {
        assertEquals(MapLook.Satellite, MapSettingsStore.lookOf(null))
        assertEquals(MapLook.Satellite, MapSettingsStore.lookOf("Hiver"))
    }

    @Test
    fun `by default, Satellite in 2D`() {
        assertEquals(MapSettings(MapLook.Satellite, tilted = false), MapSettings())
    }
}
