package fr.familia.app.core.data

import kotlinx.coroutines.flow.Flow

/**
 * `config/app`, written by the « APK de publication » workflow once the new APK is in the Drive
 * folder (docs/mise-a-jour.md). Null fields mean the document is missing or incomplete. The folder
 * link is not here: it is fixed in the app (BuildConfig.DOWNLOAD_URL). [apkFileId], [apkSize] and
 * [apkSha256] describe the APK itself, so the app can download and check it (PRD, récit 6 quater).
 */
data class AppConfig(
    val latestVersionCode: Long?,
    val latestVersionName: String?,
    val apkFileId: String? = null,
    val apkSize: Long? = null,
    val apkSha256: String? = null,
    /** Modules switched on for the family (config/app.features); the others are in test. */
    val features: Map<String, Boolean> = emptyMap(),
)

interface AppConfigRepository {
    /** Read each time the app comes on screen, then kept up to date. */
    fun observeAppConfig(): Flow<AppConfig>

    /**
     * Reads the document from the server, never from the cache, for the "Vérifier les mises à
     * jour" button. Throws [AppConfigOfflineException] when the server cannot be reached.
     */
    suspend fun fetchAppConfig(): AppConfig

    /** The admin principal switches a module on or off for the family (the rules check it). */
    suspend fun setFeature(key: String, enabled: Boolean)
}

class AppConfigOfflineException(cause: Throwable? = null) : Exception(cause)
