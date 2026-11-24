package fr.familia.app.core.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R

/** Below this level, unplugged, the trips save the battery (récit 112, règle 46). */
const val BATTERY_SAVING_PERCENT = 20

/** Once saving, the trips come back only from this level, so nothing blinks around 20 % (retour C1 de #569). */
const val BATTERY_RESUME_PERCENT = 25

/**
 * Whether the trips save the battery, knowing whether they did ([wasSaving]): Android's own saver is
 * on, or the battery is under 20 % and not charging, and once saving, until it is back at 25 % or
 * charging. [levelPercent] null (unknown) keeps what was.
 */
fun batterySaving(levelPercent: Int?, charging: Boolean, systemPowerSave: Boolean, wasSaving: Boolean = false): Boolean = when {
    systemPowerSave -> true
    charging -> false
    levelPercent == null -> wasSaving
    wasSaving -> levelPercent < BATTERY_RESUME_PERCENT
    else -> levelPercent < BATTERY_SAVING_PERCENT
}

private fun readSaving(context: Context, battery: Intent?, wasSaving: Boolean): Boolean {
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val percent = if (level >= 0 && scale > 0) level * 100 / scale else null
    val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    val powerSave = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    return batterySaving(percent, charging, powerSave, wasSaving)
}

/**
 * Whether the trips save the battery, for the whole app: one receiver, watched by FamiliaApp
 * ([watchBatterySaving]) (retour C3 de #569). False where nothing provides it (previews, tests).
 */
val LocalBatterySaving = compositionLocalOf { false }

/**
 * « Économie de batterie » (récit 112, règle 46), automatic: true while the trips should go without
 * the satellite map, the globe and the animations. Follows the battery and Android's saver live;
 * called once, at the root of the app.
 */
@Composable
fun watchBatterySaving(): Boolean {
    val context = LocalContext.current
    // Read the sticky battery at once, so a map opened on the very first frame (cold start from a
    // link) does not freeze « false » under 20 % (retour C1 de #571).
    var saving by remember(context) {
        mutableStateOf(readSaving(context, context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)), false))
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                // The power-saver broadcast carries no battery: read the sticky one again.
                val battery = if (intent.action == Intent.ACTION_BATTERY_CHANGED) intent else context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                saving = readSaving(context, battery, saving)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        // ACTION_BATTERY_CHANGED is sticky: the current state comes back at once.
        saving = readSaving(context, context.registerReceiver(receiver, filter), saving)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return saving
}

/** Whether the trips save the battery now, live. */
@Composable
fun rememberBatterySaving(): Boolean = LocalBatterySaving.current

/**
 * The same, read once when the screen opens and kept: a map that comes or goes under the fingers,
 * while a form is being typed above it, would move the whole sheet (retour C1 de #569).
 */
@Composable
fun rememberBatterySavingAtOpening(): Boolean {
    val now = LocalBatterySaving.current
    return rememberSaveable { now }
}

/** The line that says why the map and the animations are gone. */
@Composable
fun BatterySavingBanner(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.battery_saving_banner, BATTERY_SAVING_PERCENT),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
