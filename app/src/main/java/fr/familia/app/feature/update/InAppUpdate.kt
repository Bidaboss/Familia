package fr.familia.app.feature.update

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import fr.familia.app.R
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import fr.familia.app.core.data.MemberPhotoRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.core.ui.rememberMemberPhotos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where the in-app update stands (PRD, récit 6 quater). */
sealed interface UpdateStep {
    data object Idle : UpdateStep
    /** Familia may not install apps yet: the setting is opened once, then the update goes on. */
    data class AskPermission(val update: AppUpdate) : UpdateStep
    data class Downloading(val progress: Float) : UpdateStep
    /** The download, the checks or the install failed: the Drive folder is offered instead. */
    data class Failed(val update: AppUpdate) : UpdateStep
}

/**
 * « Mettre à jour » without leaving the app: downloads the APK of the Drive folder, checks its size
 * and SHA-256, then hands it to Android's installer. Without the APK details in config/app, or
 * after any failure, it opens the Drive folder as before.
 */
@Stable
class InAppUpdater internal constructor(private val context: Context, private val scope: CoroutineScope) {
    var step: UpdateStep by mutableStateOf(UpdateStep.Idle)
        private set

    fun start(update: AppUpdate) {
        val apk = update.apk
        if (apk == null) {
            openDownload(context, update.downloadUrl)
            return
        }
        if (!ApkInstaller.canInstall(context)) {
            step = UpdateStep.AskPermission(update)
            return
        }
        step = UpdateStep.Downloading(0f)
        scope.launch {
            val file = File(context.cacheDir, "updates/familia.apk")
            try {
                withContext(Dispatchers.IO) {
                    var shown = -1
                    downloadApk(apk, file) { progress ->
                        // Compose state may be written from another thread; one update per percent is enough.
                        val percent = (progress * 100).toInt()
                        if (percent != shown) {
                            shown = percent
                            step = UpdateStep.Downloading(progress)
                        }
                    }
                    ApkInstaller.install(context, file)
                }
                step = UpdateStep.Idle
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                step = UpdateStep.Failed(update)
            }
        }
    }

    internal fun openSetting() = ApkInstaller.openInstallSetting(context)

    /** Back from the settings: goes on if the permission was given. */
    internal fun resumeAfterSetting() {
        val asked = step as? UpdateStep.AskPermission ?: return
        if (ApkInstaller.canInstall(context)) start(asked.update)
    }

    internal fun openDrive(update: AppUpdate) {
        step = UpdateStep.Idle
        openDownload(context, update.downloadUrl)
    }

    internal fun installFailed(update: AppUpdate?) {
        ApkInstaller.failureShown()
        step = if (update != null) UpdateStep.Failed(update) else UpdateStep.Idle
    }

    fun dismiss() {
        step = UpdateStep.Idle
    }
}

/** The app-wide updater, provided by FamiliaApp, so every « Mettre à jour » shares the same dialogs. */
val LocalInAppUpdater = staticCompositionLocalOf<InAppUpdater?> { null }

@Composable
fun rememberInAppUpdater(): InAppUpdater {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember(context, scope) { InAppUpdater(context, scope) }
}

/**
 * The dialogs of [updater]: the permission explanation, the progress, the failure with the Drive.
 * [lastUpdate] is offered again when Android refuses the install. [family], [me] and the photos of
 * [photoRepository] make the round of the family while it downloads.
 */
@Composable
fun InAppUpdateDialogs(
    updater: InAppUpdater,
    lastUpdate: AppUpdate?,
    family: Family? = null,
    me: Member? = null,
    photoRepository: MemberPhotoRepository? = null,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { updater.resumeAfterSetting() }
    val installFailed by ApkInstaller.failed.collectAsState()
    LaunchedEffect(installFailed) { if (installFailed) updater.installFailed(lastUpdate) }

    when (val step = updater.step) {
        UpdateStep.Idle -> Unit
        is UpdateStep.AskPermission -> AlertDialog(
            onDismissRequest = updater::dismiss,
            title = { Text(stringResource(R.string.update_permission_title)) },
            text = { Text(stringResource(R.string.update_permission_body)) },
            confirmButton = {
                TextButton(onClick = updater::openSetting, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.update_permission_open))
                }
            },
            dismissButton = {
                TextButton(onClick = { updater.openDrive(step.update) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.update_open_drive))
                }
            },
        )
        is UpdateStep.Downloading -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = {
                Text(
                    stringResource(R.string.update_downloading_title),
                    style = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
                )
            },
            text = { DownloadingContent(step.progress, family, me, photoRepository) },
            confirmButton = {},
        )
        is UpdateStep.Failed -> AlertDialog(
            onDismissRequest = updater::dismiss,
            title = { Text(stringResource(R.string.update_failed_title)) },
            text = { Text(stringResource(R.string.update_failed_body)) },
            confirmButton = {
                TextButton(onClick = { updater.openDrive(step.update) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.update_open_drive))
                }
            },
            dismissButton = {
                TextButton(onClick = updater::dismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * « Mise à jour en cours » (spec DESIGN « la ronde de la famille », choisie par Kévin le 18/11): a
 * line, the round of the family, the real percentage and its bar, then what Android asks next.
 */
@Composable
private fun DownloadingContent(progress: Float, family: Family?, me: Member?, photoRepository: MemberPhotoRepository?) {
    val photos by rememberMemberPhotos(photoRepository)
    val percent = (progress * 100).toInt()
    val read = stringResource(R.string.update_downloading_progress, percent)
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.update_downloading_line),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        FamilyRound(family, me, photos, modifier = Modifier.clearAndSetSemantics {})
        Text(
            stringResource(R.string.update_downloading_percent, percent),
            style = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 30.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { contentDescription = read },
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clearAndSetSemantics {},
            color = Color(0xFF1F5C3C),
            trackColor = Color(0xFFEDF7F0),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(
            stringResource(R.string.update_downloading_help),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
