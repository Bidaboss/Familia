package fr.familia.app.feature.update

import fr.familia.app.core.data.AppConfig
import java.net.URI

/**
 * A newer version than the one installed, and where to download it: [downloadUrl] is the Drive
 * folder (first install and fallback), [apk] the file the app can download and install itself.
 */
data class AppUpdate(val versionName: String, val downloadUrl: String, val apk: ApkFile? = null)

/** The APK of the Drive folder, as described in config/app; checked before it is installed. */
data class ApkFile(val driveFileId: String, val size: Long, val sha256: String) {
    /** Direct download of a file shared « à tous ceux qui ont le lien ». */
    val downloadUrl: String get() = "https://drive.usercontent.google.com/download?id=$driveFileId&export=download&confirm=t"
}

/** Up to 200 MB: far above Familia, low enough to refuse a wrong value. */
private const val MAX_APK_SIZE = 200L * 1024 * 1024

/** The APK described by [config], or null when a field is missing or looks wrong. */
fun apkFile(config: AppConfig): ApkFile? {
    val id = config.apkFileId?.trim()?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{10,200}")) } ?: return null
    val size = config.apkSize?.takeIf { it in 1..MAX_APK_SIZE } ?: return null
    val sha = config.apkSha256?.trim()?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) } ?: return null
    return ApkFile(id, size, sha)
}

/**
 * The update to offer, or null. [downloadUrl] is the Drive folder fixed in the app. Only an HTTPS
 * link is ever opened: the APK itself is checked by Android, which refuses an update not signed
 * with the same key as the installed app.
 */
fun availableUpdate(config: AppConfig, installedVersionCode: Int, downloadUrl: String): AppUpdate? {
    val latest = config.latestVersionCode ?: return null
    if (latest <= installedVersionCode) return null
    val url = downloadUrl.trim().takeIf(::isHttpsUrl) ?: return null
    return AppUpdate(config.latestVersionName?.trim()?.takeIf { it.isNotEmpty() } ?: latest.toString(), url, apkFile(config))
}

internal fun isHttpsUrl(value: String): Boolean = try {
    val uri = URI(value)
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
} catch (e: Exception) {
    false
}
