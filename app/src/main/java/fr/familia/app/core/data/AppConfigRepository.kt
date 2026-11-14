package fr.familia.app.core.data

import kotlinx.coroutines.flow.Flow

/**
 * `config/app`, written by Kévin in the Firebase console when he puts a new APK in the Drive
 * folder (docs/mise-a-jour.md). Null fields mean the document is missing or incomplete. The link
 * itself is not here: it is fixed in the app (BuildConfig.DOWNLOAD_URL).
 */
data class AppConfig(val latestVersionCode: Long?, val latestVersionName: String?)

interface AppConfigRepository {
    /** Read each time the app comes on screen, then kept up to date. */
    fun observeAppConfig(): Flow<AppConfig>

    /**
     * Reads the document from the server, never from the cache, for the "Vérifier les mises à
     * jour" button. Throws [AppConfigOfflineException] when the server cannot be reached.
     */
    suspend fun fetchAppConfig(): AppConfig
}

class AppConfigOfflineException(cause: Throwable? = null) : Exception(cause)
