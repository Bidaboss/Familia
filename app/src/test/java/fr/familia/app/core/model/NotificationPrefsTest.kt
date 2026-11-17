package fr.familia.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPrefsTest {

    @Test
    fun `without a document everything is on and the nights are quiet`() {
        val prefs = NotificationPrefs.from(null)
        assertEquals(NotificationLevel.All, prefs.level)
        assertEquals(true, prefs.quietHours)
    }

    @Test
    fun `the nights stay quiet unless the member turned it off themselves`() {
        assertEquals(true, NotificationPrefs.from(mapOf("goodNews" to true)).quietHours)
        assertEquals(false, NotificationPrefs.from(mapOf("quietHours" to false)).quietHours)
    }

    @Test
    fun `only false turns a family off, and the level follows the switches`() {
        val essential = NotificationFamily.entries.associate { it.key to (it in NotificationLevel.Essential.on) }
        assertEquals(NotificationLevel.Essential, NotificationPrefs.from(essential).level)
        assertEquals(NotificationLevel.Minimal, NotificationPrefs.from(NotificationFamily.entries.associate { it.key to false }).level)

        val mixed = NotificationPrefs.from(mapOf("gifts" to false, "trips" to "non", "quietHours" to true))
        assertEquals(false, mixed.isOn(NotificationFamily.Gifts))
        assertEquals(true, mixed.isOn(NotificationFamily.Trips))
        assertEquals(true, mixed.quietHours)
        assertEquals(null, mixed.level)
    }
}
