package fr.familia.app.screenshots

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.capsules.CapsulesScreen
import fr.familia.app.feature.capsules.previewCapsulesState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The time capsules (récit 39) in light, dark and at 200 % font size. Fictional data, no loading state. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class CapsuleScreenshotTest {

    private val list: @Composable () -> Unit = { Surface { CapsulesScreen(previewCapsulesState(), {}, onOpen = {}, onAdd = {}) } }

    @Test
    fun capsulesLight() = capture("capsules_clair", content = list)

    @Test
    @Config(qualifiers = "+night")
    fun capsulesDark() = capture("capsules_sombre", darkTheme = true, content = list)

    @Test
    @Config(fontScale = 2f)
    fun capsulesFontScale200() = capture("capsules_police_200", content = list)

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = darkTheme) { content() }
        }
    }
}
