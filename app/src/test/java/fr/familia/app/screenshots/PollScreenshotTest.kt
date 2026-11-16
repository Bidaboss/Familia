package fr.familia.app.screenshots

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.model.Poll
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.polls.detail.PollDetailScreen
import fr.familia.app.feature.polls.detail.PollDetailUiState
import fr.familia.app.feature.polls.detail.previewPollDetail
import fr.familia.app.feature.polls.edit.PollEditScreen
import fr.familia.app.feature.polls.edit.previewPollEdit
import fr.familia.app.feature.polls.list.PollsScreen
import fr.familia.app.feature.polls.list.previewPolls
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The polls (récits 2 bis et 2 ter): list, form and one poll, in light, dark and at 200 % font size. Fictional data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class PollScreenshotTest {

    private val today = LocalDate.of(2026, 11, 20)

    private val list: @Composable () -> Unit = { Surface { PollsScreen(previewPolls(), {}, {}, {}, {}, today = today) } }
    private val form: @Composable () -> Unit = { Surface { PollEditScreen(previewPollEdit(), {}, {}, {}, {}, {}, {}, {}, today = today) } }
    private val formChoice: @Composable () -> Unit = {
        Surface { PollEditScreen(previewPollEdit(Poll.Kind.Choice), {}, {}, {}, {}, {}, {}, {}, today = today) }
    }
    private val date: @Composable () -> Unit = { Surface { detail(previewPollDetail()) } }
    private val choice: @Composable () -> Unit = { Surface { detail(previewPollDetail(Poll.Kind.Choice)) } }

    @Composable
    private fun detail(state: PollDetailUiState) =
        PollDetailScreen(state, { _, _, _ -> }, { _, _ -> }, {}, {}, { _, _ -> }, {}, {}, {}, {}, today = today)

    @Test
    fun pollsLight() = capture("sondages_liste_clair", content = list)

    @Test
    @Config(qualifiers = "+night")
    fun pollsDark() = capture("sondages_liste_sombre", darkTheme = true, content = list)

    @Test
    @Config(fontScale = 2f)
    fun pollsFontScale200() = capture("sondages_liste_police_200", content = list)

    @Test
    fun pollFormLight() = capture("sondage_nouveau_clair", content = form)

    @Test
    fun pollFormChoiceLight() = capture("sondage_nouveau_choix_clair", content = formChoice)

    @Test
    @Config(qualifiers = "+night")
    fun pollFormDark() = capture("sondage_nouveau_sombre", darkTheme = true, content = form)

    @Test
    fun pollDateLight() = capture("sondage_date_clair", content = date)

    @Test
    @Config(qualifiers = "+night")
    fun pollDateDark() = capture("sondage_date_sombre", darkTheme = true, content = date)

    @Test
    @Config(fontScale = 2f)
    fun pollDateFontScale200() = capture("sondage_date_police_200", content = date)

    @Test
    fun pollChoiceLight() = capture("sondage_choix_clair", content = choice)

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = darkTheme) { content() }
        }
    }
}
