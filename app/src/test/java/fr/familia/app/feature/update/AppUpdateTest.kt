package fr.familia.app.feature.update

import fr.familia.app.core.data.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateTest {

    private val url = "https://drive.google.com/drive/folders/example"

    @Test
    fun `a newer version is offered with the drive folder`() {
        assertEquals(AppUpdate("0.2.0", url), availableUpdate(AppConfig(2, "0.2.0"), installedVersionCode = 1, url))
    }

    @Test
    fun `the installed version or an older one offers nothing`() {
        assertNull(availableUpdate(AppConfig(1, "0.1.0"), installedVersionCode = 1, url))
        assertNull(availableUpdate(AppConfig(1, "0.1.0"), installedVersionCode = 3, url))
    }

    @Test
    fun `only an https link is ever opened`() {
        val config = AppConfig(2, "0.2.0")
        assertNull(availableUpdate(config, 1, "http://example.com/familia"))
        assertNull(availableUpdate(config, 1, "file:///sdcard/familia.apk"))
        assertNull(availableUpdate(config, 1, "javascript:alert(1)"))
        assertNull(availableUpdate(config, 1, "https://"))
        assertNull(availableUpdate(config, 1, ""))
    }

    @Test
    fun `a missing or incomplete config offers nothing`() {
        assertNull(availableUpdate(AppConfig(null, null), 1, url))
    }

    @Test
    fun `without a version name, the version number is shown`() {
        assertEquals("2", availableUpdate(AppConfig(2, " "), 1, url)?.versionName)
    }
}
