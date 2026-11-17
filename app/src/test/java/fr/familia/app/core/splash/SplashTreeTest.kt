package fr.familia.app.core.splash

import android.graphics.drawable.Animatable
import android.util.TypedValue
import androidx.core.content.ContextCompat
import fr.familia.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The start screen of Android 12 and later (spec DESIGN « animation d'ouverture », part 1): a badly
 * formed splash_tree.xml must break the CI, never the start of the app on a phone.
 */
@RunWith(RobolectricTestRunner::class)
class SplashTreeTest {

    @Test
    @Config(sdk = [31, 34])
    fun `the growing tree loads and can be played`() {
        val context = RuntimeEnvironment.getApplication()
        val drawable = ContextCompat.getDrawable(context, R.drawable.splash_tree)
        assertNotNull(drawable)
        assertTrue(drawable is Animatable)
        (drawable as Animatable).start()
        drawable.stop()
    }

    @Test
    @Config(sdk = [31, 34])
    fun `the start screen of the theme shows the growing tree for at most 1350 ms`() {
        val context = RuntimeEnvironment.getApplication()
        context.setTheme(R.style.Theme_Familia)
        val icon = TypedValue()
        assertTrue(context.theme.resolveAttribute(android.R.attr.windowSplashScreenAnimatedIcon, icon, true))
        assertEquals(R.drawable.splash_tree, icon.resourceId)
        val duration = TypedValue()
        assertTrue(context.theme.resolveAttribute(android.R.attr.windowSplashScreenAnimationDuration, duration, true))
        assertTrue(duration.data in 1..START_SCREEN_MILLIS.toInt())
    }

    @Test
    fun `a cold start keeps the screen from the start of the process, a warm one from now`() {
        assertEquals(1_000L + START_SCREEN_MILLIS, startScreenUntil(now = 1_400L, processStart = 1_000L, coldStart = true))
        assertEquals(90_000L + START_SCREEN_MILLIS, startScreenUntil(now = 90_000L, processStart = 1_000L, coldStart = false))
        // A slow cold start: the time is already over, nothing waits more.
        assertTrue(startScreenUntil(now = 2_400L, processStart = 1_000L, coldStart = true) < 2_400L)
    }

    @Test
    @Config(sdk = [30])
    fun `before Android 12 the theme loads as before`() {
        val context = RuntimeEnvironment.getApplication()
        context.setTheme(R.style.Theme_Familia)
        assertNotNull(context.theme)
    }
}
