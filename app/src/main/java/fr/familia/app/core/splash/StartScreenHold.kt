package fr.familia.app.core.splash

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver

/** How long the tree of the start screen grows (splash_tree.xml), in milliseconds. */
const val START_SCREEN_MILLIS = 1_350L

/**
 * Until when the start screen stays, in uptime milliseconds: a whole [START_SCREEN_MILLIS] from
 * when the app is ready to draw. Kévin, 17/11: « même si l'appli démarre plus tard, je veux voir
 * l'animation en entier ». So a slow start no longer cuts the tree: it waits the full length from
 * here, cold or warm, even if the tree already grew a little during the start.
 */
internal fun startScreenUntil(now: Long, length: Long = START_SCREEN_MILLIS): Long = now + length

/**
 * Android 12 and later: keeps the start screen [START_SCREEN_MILLIS] from now, so its tree is
 * never cut, by a quick first frame or by a slow start (retours de Kévin, 17/11). Only on a real opening (no saved state: never after a rotation), and never when the
 * animations of the phone are off, where the tree does not grow. To go back: remove the call.
 */
fun Activity.holdStartScreen(restored: Boolean) {
    if (restored || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val off = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    if (off) return
    val until = startScreenUntil(SystemClock.uptimeMillis())
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
