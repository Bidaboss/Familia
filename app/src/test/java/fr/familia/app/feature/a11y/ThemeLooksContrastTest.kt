package fr.familia.app.feature.a11y

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import fr.familia.app.core.theme.FamiliaThemeId
import fr.familia.app.core.ui.Look
import fr.familia.app.core.ui.allLooks
import fr.familia.app.core.ui.checkContrast
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * The contrast pass (avant le 21) on a list of [screens], in Classique light and dark and in each
 * theme to choose from: every text 4.5:1 (3:1 when large), every small icon 3:1, measured on the
 * drawn screen by [checkContrast]. A subclass gives the screens, with `@GraphicsMode(NATIVE)`.
 */
abstract class ThemeLooksContrastTest(private val screens: List<Pair<String, @Composable () -> Unit>>) {

    @get:Rule
    val compose = createComposeRule()

    private fun check(look: Look) = compose.checkContrast(look, screens)

    private fun look(theme: FamiliaThemeId) = allLooks.first { it.theme == theme && !it.dark }

    @Test
    fun `Classique, light`() = check(look(FamiliaThemeId.Classic))

    @Test
    @Config(qualifiers = "+night")
    fun `Classique, dark`() = check(allLooks.first { it.dark })

    @Test
    fun olive() = check(look(FamiliaThemeId.Olive))

    @Test
    fun lavender() = check(look(FamiliaThemeId.Lavender))

    @Test
    fun night() = check(look(FamiliaThemeId.Night))

    @Test
    fun winter() = check(look(FamiliaThemeId.Winter))

    @Test
    fun spain() = check(look(FamiliaThemeId.Spain))

    @Test
    fun `high contrast`() = check(look(FamiliaThemeId.HighContrast))
}
