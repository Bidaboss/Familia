package fr.familia.app.feature.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Installs a downloaded APK over the app with PackageInstaller (PRD, récit 6 quater). Android shows
 * its own confirmation, and refuses an APK that is not signed with the key of the installed app.
 */
object ApkInstaller {

    /** True when the member already allowed Familia to install apps (asked once, in the settings). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the setting « Installer des applis inconnues » for Familia. */
    fun openInstallSetting(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private val _failed = MutableStateFlow(false)

    /** True when Android refused or cancelled the install: the screen offers the Drive instead. */
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    fun failureShown() {
        _failed.value = false
    }

    /** Streams [apk] into an install session; the result comes back to [InstallResultReceiver]. */
    fun install(context: Context, apk: File) {
        _failed.value = false
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("familia.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender)
        }
    }

    internal fun reportFailure() {
        _failed.value = true
    }
}

/** Receives the install result: asks Android for its confirmation screen, or reports a failure. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return ApkInstaller.reportFailure()
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { ApkInstaller.reportFailure() }
            }
            // On success Android restarts nothing: the new version opens next time.
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> ApkInstaller.reportFailure()
        }
    }
}
