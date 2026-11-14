package fr.familia.app.screenshots

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.navigation.FamiliaApp
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.birthdays.BirthdaysScreen
import fr.familia.app.feature.members.MembersScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the screens on the JVM and saves PNG files, so each pull request carries light and dark
 * screenshots without a phone. CI runs `recordRoborazziDebug` and uploads build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class ScreenshotTest {

    @Test
    fun planningLight() = capture("planning_clair") { FamiliaApp() }

    @Test
    @Config(qualifiers = "+night")
    fun planningDark() = capture("planning_sombre", darkTheme = true) { FamiliaApp() }

    @Test
    @Config(fontScale = 2f)
    fun planningFontScale200() = capture("planning_police_200") { FamiliaApp() }

    @Test
    fun birthdaysLight() = capture("anniversaires_clair") { Surface { BirthdaysScreen() } }

    @Test
    @Config(qualifiers = "+night")
    fun birthdaysDark() = capture("anniversaires_sombre", darkTheme = true) { Surface { BirthdaysScreen() } }

    @Test
    fun membersLight() = capture("famille_clair") { Surface { MembersScreen(versionName = "0.1.0") } }

    @Test
    @Config(qualifiers = "+night")
    fun membersDark() = capture("famille_sombre", darkTheme = true) {
        Surface { MembersScreen(versionName = "0.1.0") }
    }

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = darkTheme) { content() }
        }
    }
}
