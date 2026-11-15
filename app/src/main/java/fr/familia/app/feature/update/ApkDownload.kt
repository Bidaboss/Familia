package fr.familia.app.feature.update

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Why the app could not get the APK itself: the banner then opens the Drive folder instead. */
class ApkDownloadException(message: String) : Exception(message)

/**
 * Downloads [apk] to [target] and checks it (PRD, récit 6 quater). The size and the SHA-256 come from
 * config/app, written by the release workflow: a page of warnings from Google, a cut download or
 * any other file is refused. Android then checks the signature again when installing. Blocking:
 * call it off the main thread. [onProgress] gets a value from 0 to 1.
 */
fun downloadApk(apk: ApkFile, target: File, onProgress: (Float) -> Unit = {}) {
    val connection = (URL(apk.downloadUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 30_000
        instanceFollowRedirects = true
    }
    try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw ApkDownloadException("HTTP ${connection.responseCode}")
        }
        target.parentFile?.mkdirs()
        connection.inputStream.use { input -> target.outputStream().use { copyChecked(input, it, apk, onProgress) } }
    } catch (e: Exception) {
        target.delete()
        throw if (e is ApkDownloadException) e else ApkDownloadException(e.javaClass.simpleName)
    } finally {
        connection.disconnect()
    }
}

/**
 * Copies [input] to [output] while counting and hashing; refuses more bytes than announced, fewer
 * bytes, or another SHA-256. Pure, so the unit tests check it without network.
 */
internal fun copyChecked(input: InputStream, output: java.io.OutputStream, apk: ApkFile, onProgress: (Float) -> Unit = {}) {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > apk.size) throw ApkDownloadException("Too large")
        digest.update(buffer, 0, read)
        output.write(buffer, 0, read)
        onProgress(total.toFloat() / apk.size)
    }
    if (total != apk.size) throw ApkDownloadException("Size $total")
    val sha = digest.digest().joinToString("") { "%02x".format(it) }
    if (sha != apk.sha256) throw ApkDownloadException("Checksum")
}
