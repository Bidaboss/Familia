package fr.familia.app.feature.update

import fr.familia.app.core.data.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateTest {

    private val url = "https://drive.google.com/file/d/abc/view"

    @Test
    fun `a newer version is offered with its link`() {
        assertEquals(AppUpdate("0.2.0", url), availableUpdate(AppConfig(2, "0.2.0", url), installedVersionCode = 1))
    }

    @Test
    fun `the installed version or an older one offers nothing`() {
        assertNull(availableUpdate(AppConfig(1, "0.1.0", url), installedVersionCode = 1))
        assertNull(availableUpdate(AppConfig(1, "0.1.0", url), installedVersionCode = 3))
    }

    @Test
    fun `only an https link is ever opened`() {
        assertNull(availableUpdate(AppConfig(2, "0.2.0", "http://example.com/familia.apk"), 1))
        assertNull(availableUpdate(AppConfig(2, "0.2.0", "file:///sdcard/familia.apk"), 1))
        assertNull(availableUpdate(AppConfig(2, "0.2.0", "javascript:alert(1)"), 1))
        assertNull(availableUpdate(AppConfig(2, "0.2.0", "https://"), 1))
        assertNull(availableUpdate(AppConfig(2, "0.2.0", null), 1))
    }

    @Test
    fun `a missing or incomplete config offers nothing`() {
        assertNull(availableUpdate(AppConfig(null, null, null), 1))
    }

    @Test
    fun `without a version name, the version number is shown`() {
        assertEquals("2", availableUpdate(AppConfig(2, " ", url), 1)?.versionName)
    }
}
