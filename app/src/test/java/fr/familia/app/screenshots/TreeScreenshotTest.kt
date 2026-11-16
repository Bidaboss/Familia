package fr.familia.app.screenshots

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.tree.TreeHomeContent
import fr.familia.app.feature.tree.previewTreeState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** L'arbre de la famille (récit 35) in light, dark and at 200 % font size. Fictional data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class TreeScreenshotTest {

    private val growing: @Composable () -> Unit = { Surface { TreeHomeContent(previewTreeState()) } }
    private val bloomed: @Composable () -> Unit = { Surface { TreeHomeContent(previewTreeState(achieved = true)) } }

    @Test
    fun treeLight() = capture("arbre_clair", content = growing)

    @Test
    @Config(qualifiers = "+night")
    fun treeDark() = capture("arbre_sombre", darkTheme = true, content = growing)

    @Test
    @Config(fontScale = 2f)
    fun treeFontScale200() = capture("arbre_police_200", content = growing)

    @Test
    fun treeBloomedLight() = capture("arbre_fleuri_clair", content = bloomed)

    @Test
    @Config(qualifiers = "+night")
    fun treeBloomedDark() = capture("arbre_fleuri_sombre", darkTheme = true, content = bloomed)

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = darkTheme) { content() }
        }
    }
}
