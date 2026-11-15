package fr.familia.app.feature.update

import fr.familia.app.core.data.AppConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ApkFileTest {

    private val bytes = ByteArray(200_000) { (it % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val id = "1AbC-dEf_ghIJklmnop"

    private fun config(fileId: String? = id, size: Long? = bytes.size.toLong(), sha256: String? = sha) =
        AppConfig(2, "0.2.2", apkFileId = fileId, apkSize = size, apkSha256 = sha256)

    @Test
    fun `a complete config describes the apk`() {
        val apk = apkFile(config(sha256 = sha.uppercase()))
        assertEquals(ApkFile(id, bytes.size.toLong(), sha), apk)
        assertEquals("https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t", apk?.downloadUrl)
    }

    @Test
    fun `a missing or odd field leaves only the drive folder`() {
        assertNull(apkFile(config(fileId = null)))
        assertNull(apkFile(config(fileId = "short")))
        assertNull(apkFile(config(fileId = "abc&evil=1xxxxxx")))
        assertNull(apkFile(config(size = 0)))
        assertNull(apkFile(config(size = 300L * 1024 * 1024)))
        assertNull(apkFile(config(sha256 = "1234")))
        assertNull(apkFile(config(sha256 = "z".repeat(64))))
    }

    @Test
    fun `the update carries the apk when config describes it`() {
        val update = availableUpdate(config(), installedVersionCode = 1, "https://drive.google.com/drive/folders/x")
        assertEquals(id, update?.apk?.driveFileId)
    }

    @Test
    fun `the expected file is copied whole`() {
        val out = ByteArrayOutputStream()
        var last = 0f
        copyChecked(ByteArrayInputStream(bytes), out, ApkFile(id, bytes.size.toLong(), sha)) { last = it }
        assertArrayEquals(bytes, out.toByteArray())
        assertEquals(1f, last)
    }

    @Test
    fun `a wrong size or checksum is refused`() {
        val apk = ApkFile(id, bytes.size.toLong(), sha)
        assertThrows(ApkDownloadException::class.java) {
            copyChecked(ByteArrayInputStream(bytes.copyOf(bytes.size - 1)), ByteArrayOutputStream(), apk)
        }
        assertThrows(ApkDownloadException::class.java) {
            copyChecked(ByteArrayInputStream(bytes + 1), ByteArrayOutputStream(), apk)
        }
        val changed = bytes.copyOf().also { it[10] = 99 }
        assertThrows(ApkDownloadException::class.java) {
            copyChecked(ByteArrayInputStream(changed), ByteArrayOutputStream(), apk)
        }
    }
}
