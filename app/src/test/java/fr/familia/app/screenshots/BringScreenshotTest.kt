package fr.familia.app.screenshots

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.events.bring.BringContent
import fr.familia.app.feature.events.bring.previewBring
import fr.familia.app.feature.events.detail.EventDetailScreen
import fr.familia.app.feature.events.detail.previewEventDetail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** « Qui apporte quoi » (récit 4 bis) inside an event, in light, dark and at 200 % font size. Fictional data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class BringScreenshotTest {

    private val event: @Composable () -> Unit = {
        Surface {
            EventDetailScreen(previewEventDetail(), { _, _ -> }, {}, {}, {}) {
                BringContent(previewBring(), { _, _ -> true }, {}, {}, {}, {})
            }
        }
    }

    @Test
    fun bringLight() = capture("evenement_apporte_clair", content = event)

    @Test
    @Config(qualifiers = "+night")
    fun bringDark() = capture("evenement_apporte_sombre", darkTheme = true, content = event)

    @Test
    @Config(fontScale = 2f)
    fun bringFontScale200() = capture("evenement_apporte_police_200", content = event)

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = darkTheme) { content() }
        }
    }
}
