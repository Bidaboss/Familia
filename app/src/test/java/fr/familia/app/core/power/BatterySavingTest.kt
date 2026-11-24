package fr.familia.app.core.power

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatterySavingTest {
    @Test
    fun `under 20 percent unplugged, or with the saver of Android, the trips save the battery`() {
        assertTrue(batterySaving(19, charging = false, systemPowerSave = false))
        assertFalse(batterySaving(20, charging = false, systemPowerSave = false))
        assertFalse(batterySaving(5, charging = true, systemPowerSave = false))
        assertTrue(batterySaving(80, charging = true, systemPowerSave = true))
        assertFalse(batterySaving(null, charging = false, systemPowerSave = false))
    }

    @Test
    fun `once saving, the trips come back only at 25 percent or plugged in`() {
        assertTrue(batterySaving(22, charging = false, systemPowerSave = false, wasSaving = true))
        assertFalse(batterySaving(25, charging = false, systemPowerSave = false, wasSaving = true))
        assertFalse(batterySaving(10, charging = true, systemPowerSave = false, wasSaving = true))
        assertFalse(batterySaving(22, charging = false, systemPowerSave = false, wasSaving = false))
        assertTrue(batterySaving(null, charging = false, systemPowerSave = false, wasSaving = true))
    }
}
