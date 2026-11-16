package fr.familia.app.screenshots

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.feature.joinme.JoinMeHomeContent
import fr.familia.app.feature.joinme.JoinMeScreen
import fr.familia.app.feature.joinme.previewJoinMe
import fr.familia.app.feature.joinme.previewJoinMeForm
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneId

/** « Rejoignez-moi » (récit 33): the form and the card of the Accueil, light, dark and 200 %. Fictional data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class JoinMeScreenshotTest {

    private val form: @Composable () -> Unit = {
        JoinMeScreen(previewJoinMeForm(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }

    private val card: @Composable () -> Unit = {
        Surface {
            ClayCard { JoinMeHomeContent(previewJoinMe(Instant.parse("2026-11-21T16:00:00Z")), false, {}, zone = ZoneId.of("Europe/Paris")) }
        }
    }

    @Test
    fun formLight() = capture("rejoignez_moi_clair", content = form)

    @Test
    @Config(qualifiers = "+night")
    fun formDark() = capture("rejoignez_moi_sombre", darkTheme = true, content = form)

    @Test
    @Config(fontScale = 2f)
    fun formFontScale200() = capture("rejoignez_moi_police_200", content = form)

    @Test
    fun cardLight() = capture("accueil_rejoignez_moi_clair", content = card)

    @Test
    @Config(qualifiers = "+night")
    fun cardDark() = capture("accueil_rejoignez_moi_sombre", darkTheme = true, content = card)

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = darkTheme) { content() }
        }
    }
}
