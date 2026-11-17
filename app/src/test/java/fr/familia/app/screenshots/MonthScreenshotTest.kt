package fr.familia.app.screenshots

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.album.FilmActions
import fr.familia.app.core.album.FilmState
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.month.MonthSlideshowScreen
import fr.familia.app.feature.month.previewMonthSlideshow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/** The slideshow of the month (récit 42) in light, dark and at 200 % font size. Fictional data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class MonthScreenshotTest {

    private val camille = Member("c", "Camille", null, LocalDate.of(1990, 8, 12), "h1", MemberProfile.Adult, false, emptyList(), null)
    private val month = YearMonth.of(2027, 8)

    // Idle film only: an endless progress bar keeps the capture waiting.
    private val screen: @Composable () -> Unit = {
        Surface { MonthSlideshowScreen(month, previewMonthSlideshow(listOf(camille)), FilmState.Idle, FilmActions(), {}) }
    }

    @Test
    fun monthLight() = capture("mois_clair", content = screen)

    @Test
    @Config(qualifiers = "+night")
    fun monthDark() = capture("mois_sombre", darkTheme = true, content = screen)

    @Test
    @Config(fontScale = 2f)
    fun monthFontScale200() = capture("mois_police_200", content = screen)

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = darkTheme) { content() }
        }
    }
}
