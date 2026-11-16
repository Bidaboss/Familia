package fr.familia.app.screenshots

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.recipes.RecipeDetailScreen
import fr.familia.app.feature.recipes.RecipeEditScreen
import fr.familia.app.feature.recipes.RecipesScreen
import fr.familia.app.feature.recipes.previewRecipeDetail
import fr.familia.app.feature.recipes.previewRecipeEdit
import fr.familia.app.feature.recipes.previewRecipesState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The recipe book (récit 38) in light, dark and at 200 % font size. Fictional data, no loading state. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class RecipeScreenshotTest {

    private val list: @Composable () -> Unit = { Surface { RecipesScreen(previewRecipesState(), {}, {}, {}, {}) } }
    private val detail: @Composable () -> Unit = { Surface { RecipeDetailScreen(previewRecipeDetail(), {}, {}, {}) } }
    private val edit: @Composable () -> Unit = { Surface { RecipeEditScreen(previewRecipeEdit(), {}, {}, {}) } }

    @Test
    fun recipesLight() = capture("recettes_clair", content = list)

    @Test
    @Config(qualifiers = "+night")
    fun recipesDark() = capture("recettes_sombre", darkTheme = true, content = list)

    @Test
    @Config(fontScale = 2f)
    fun recipesFontScale200() = capture("recettes_police_200", content = list)

    @Test
    fun recipeLight() = capture("recette_clair", content = detail)

    @Test
    @Config(qualifiers = "+night")
    fun recipeDark() = capture("recette_sombre", darkTheme = true, content = detail)

    @Test
    @Config(fontScale = 2f)
    fun recipeFontScale200() = capture("recette_police_200", content = detail)

    @Test
    fun recipeEditLight() = capture("recette_modifier_clair", content = edit)

    @Test
    @Config(fontScale = 2f)
    fun recipeEditFontScale200() = capture("recette_modifier_police_200", content = edit)

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = darkTheme) { content() }
        }
    }
}
