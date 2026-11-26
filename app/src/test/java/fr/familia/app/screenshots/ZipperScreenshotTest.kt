package fr.familia.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.home.MyDayZipper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La fermeture éclair de « Ma journée » (brief §7): closed with its badge, half-way, open. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class ZipperScreenshotTest {

    private fun zipper(open: Boolean, startAt: Float?): @Composable () -> Unit = {
        MyDayZipper(
            open = open,
            onOpen = {},
            waiting = 3,
            gazette = { Box(Modifier.fillMaxSize().background(Color(0xFFF6EFE4))) { Text("La gazette", Modifier.padding(24.dp)) } },
            myDay = { Box(Modifier.fillMaxSize().background(Color(0xFFFBF7EC))) { Text("Ma journée", Modifier.padding(24.dp)) } },
            startAt = startAt,
        )
    }

    @Test
    fun zipperClosed() = capture("fermeture_eclair_fermee", zipper(open = false, startAt = 0f))

    @Test
    fun zipperHalfWay() = capture("fermeture_eclair_mi_chemin", zipper(open = false, startAt = 0.45f))

    @Test
    fun zipperOpen() = capture("fermeture_eclair_ouverte", zipper(open = true, startAt = 1f))

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") { FamiliaTheme { content() } }
    }
}
