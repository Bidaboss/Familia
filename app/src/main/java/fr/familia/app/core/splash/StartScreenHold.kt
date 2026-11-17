package fr.familia.app.core.splash

import android.app.Activity
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver

/** How long the tree of the start screen grows (splash_tree.xml), in milliseconds. */
const val START_SCREEN_MILLIS = 1_350L

/**
 * Until when the start screen stays, in uptime milliseconds: [START_SCREEN_MILLIS] after it showed.
 * On a cold start ([coldStart], the first activity of the process) it showed when the process
 * started, so a slow start waits less, or not at all; on a warm start (process kept), about now.
 */
internal fun startScreenUntil(now: Long, processStart: Long, coldStart: Boolean, length: Long = START_SCREEN_MILLIS): Long =
    (if (coldStart) processStart else now) + length

/** True until the first activity of the process asks: that one is the cold start. */
private var firstInProcess = true

/**
 * Android 12 and later: keeps the start screen until its tree has grown, at most
 * [START_SCREEN_MILLIS] after it showed, so the tree is not cut by a quick first frame (retour de
 * Kévin, 17/11). Only on a real opening (no saved state: never after a rotation), and never when the
 * animations of the phone are off, where the tree does not grow. To go back: remove the call.
 */
fun Activity.holdStartScreen(restored: Boolean) {
    val coldStart = firstInProcess
    firstInProcess = false
    if (restored || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val off = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    if (off) return
    val until = startScreenUntil(SystemClock.uptimeMillis(), Process.getStartUptimeMillis(), coldStart)
    if (SystemClock.uptimeMillis() >= until) return
    val content = findViewById<View>(android.R.id.content)
    content.viewTreeObserver.addOnPreDrawListener(
        object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                // false: the frame waits and is asked again at the next one.
                if (SystemClock.uptimeMillis() < until) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        },
    )
}
