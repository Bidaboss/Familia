package fr.familia.app.feature.update

import fr.familia.app.core.data.AppConfig
import java.net.URI

/** A newer version than the one installed, and where to download it. */
data class AppUpdate(val versionName: String, val downloadUrl: String)

/**
 * The update to offer, or null. [downloadUrl] is the Drive folder fixed in the app. Only an HTTPS
 * link is ever opened: the APK itself is checked by Android, which refuses an update not signed
 * with the same key as the installed app.
 */
fun availableUpdate(config: AppConfig, installedVersionCode: Int, downloadUrl: String): AppUpdate? {
    val latest = config.latestVersionCode ?: return null
    if (latest <= installedVersionCode) return null
    val url = downloadUrl.trim().takeIf(::isHttpsUrl) ?: return null
    return AppUpdate(config.latestVersionName?.trim()?.takeIf { it.isNotEmpty() } ?: latest.toString(), url)
}

internal fun isHttpsUrl(value: String): Boolean = try {
    val uri = URI(value)
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
} catch (e: Exception) {
    false
}
