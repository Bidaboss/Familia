package fr.familia.app.core.data

import kotlinx.coroutines.flow.Flow

/**
 * `config/app`, written by Kévin in the Firebase console when he puts a new APK in the Drive
 * folder (docs/mise-a-jour.md). Null fields mean the document is missing or incomplete.
 */
data class AppConfig(val latestVersionCode: Long?, val latestVersionName: String?, val downloadUrl: String?)

interface AppConfigRepository {
    /** Read at each opening of the app, then kept up to date. */
    fun observeAppConfig(): Flow<AppConfig>
}
