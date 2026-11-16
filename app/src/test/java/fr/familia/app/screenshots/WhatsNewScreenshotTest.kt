package fr.familia.app.screenshots

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.whatsnew.ShownCard
import fr.familia.app.core.whatsnew.WhatsNewScreen
import fr.familia.app.core.whatsnew.WhatsNewSheetContent
import fr.familia.app.core.whatsnew.previewWhatsNew
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** « Quoi de neuf ? » (récit 6 septies): the sheet and the history, in light, dark and at 200 % font size. Fictional data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class WhatsNewScreenshotTest {

    private val sheet: @Composable () -> Unit = {
        Surface {
            WhatsNewSheetContent(previewWhatsNew.cards.map { ShownCard(it, previewWhatsNew) }, child = false, onGo = {}, onLater = {}, onTry = {})
        }
    }
    private val history: @Composable () -> Unit = {
        WhatsNewScreen(listOf(previewWhatsNew to previewWhatsNew.cards), child = false, onOpen = {}, onBack = {})
    }

    @Test
    fun sheetLight() = capture("quoi_de_neuf_clair", content = sheet)

    @Test
    @Config(qualifiers = "+night")
    fun sheetDark() = capture("quoi_de_neuf_sombre", darkTheme = true, content = sheet)

    @Test
    @Config(fontScale = 2f)
    fun sheetFontScale200() = capture("quoi_de_neuf_police_200", content = sheet)

    @Test
    fun historyLight() = capture("quoi_de_neuf_historique_clair", content = history)

    @Test
    @Config(fontScale = 2f)
    fun historyFontScale200() = capture("quoi_de_neuf_historique_police_200", content = history)

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = darkTheme) { content() }
        }
    }
}
