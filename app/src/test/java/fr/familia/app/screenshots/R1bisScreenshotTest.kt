package fr.familia.app.screenshots

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.R
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.theme.BodyFont
import fr.familia.app.core.theme.FamiliaThemeId
import fr.familia.app.core.theme.TitleFont
import fr.familia.app.feature.all.AllFamiliaScreen
import fr.familia.app.feature.all.AllModule
import fr.familia.app.feature.all.ModuleFamily
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.home.DayItem
import fr.familia.app.feature.home.DayOpen
import fr.familia.app.feature.home.DayTone
import fr.familia.app.feature.home.HomeActions
import fr.familia.app.feature.home.HomeDay
import fr.familia.app.feature.home.HomeR1bisActions
import fr.familia.app.feature.home.HomeR1bisScreen
import fr.familia.app.feature.home.HomeTodo
import fr.familia.app.feature.home.HomeUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime

/**
 * The R1 bis presentation (spec DESIGN du 17/11): the Accueil in Classique with its « pépins », in the
 * two dark themes (Arcade, Cosmos), at 200 % font size and on a tablet, then Tout Familia. Fictional data.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class R1bisScreenshotTest {

    private val today = LocalDate.of(2026, 11, 17)
    private val judo = TestFamily.event("judo", TestFamily.adult, date = today.plusDays(1)).copy(title = "Judo de Noé", time = LocalTime.of(17, 30))

    private val days = (0L until 7L).map { offset ->
        val date = today.plusDays(offset)
        HomeDay(
            date,
            when (offset) {
                0L -> listOf(DayItem(LocalTime.of(19, 0), "Quiz en famille", "Camille", "Quiz", DayTone.Organise, DayOpen.Event("quiz")))
                1L -> listOf(
                    DayItem(LocalTime.of(17, 30), "Judo de Noé", "Inès", "Judo", DayTone.Organise, DayOpen.Event("judo")),
                    DayItem(LocalTime.of(18, 0), "Porter les courses", "Sam", "Courses", DayTone.Help, DayOpen.Help("help")),
                )
                4L -> listOf(DayItem(null, "Anniversaire de Lou", null, "Lou", DayTone.Party, DayOpen.Birthday("lou")))
                else -> emptyList()
            },
        )
    }
    private val state = HomeUiState.Ready(today = today, todos = listOf(HomeTodo.Answer(judo)), days = days)

    private val home: @Composable () -> Unit = {
        Surface { HomeR1bisScreen(state, HomeActions(), HomeR1bisActions(), allBadge = 2) }
    }

    private val all: @Composable () -> Unit = {
        Surface {
            AllFamiliaScreen(
                listOf(
                    AllModule("planning", R.string.all_planning, Icons.Outlined.CalendarMonth, ModuleFamily.Organise) {},
                    AllModule("help", R.string.all_help, Icons.Outlined.VolunteerActivism, ModuleFamily.Organise, badge = 2) {},
                    AllModule("gifts", R.string.all_gifts, Icons.Outlined.CardGiftcard, ModuleFamily.Treat) {},
                    AllModule("albums", R.string.all_albums, Icons.Outlined.PhotoLibrary, ModuleFamily.Remember) {},
                ),
                onBack = {},
            )
        }
    }

    @Test
    fun homeClassic() = capture("r1bis_accueil_classique", content = home)

    @Test
    fun homeOlive() = capture("r1bis_accueil_olivier", theme = FamiliaThemeId.Olive, content = home)

    @Test
    fun homeNight() = capture("r1bis_accueil_nuit", theme = FamiliaThemeId.Night, content = home)

    /** Olivier and Nuit at 130 %, with the two other fonts (DESIGN-43, 45 and 46). */
    @Test
    @Config(fontScale = 1.3f)
    fun homeOliveFontScale130() = capture("r1bis_accueil_olivier_130", theme = FamiliaThemeId.Olive, titleFont = TitleFont.Elegant, bodyFont = BodyFont.Legible, content = home)

    @Test
    @Config(fontScale = 1.3f)
    fun homeNightFontScale130() = capture("r1bis_accueil_nuit_130", theme = FamiliaThemeId.Night, titleFont = TitleFont.Sober, bodyFont = BodyFont.Legible, content = home)

    @Test
    @Config(fontScale = 2f)
    fun homeFontScale200() = capture("r1bis_accueil_police_200", content = home)

    @Test
    @Config(qualifiers = "w960dp-h600dp-xhdpi")
    fun homeTablet() = capture("r1bis_accueil_tablette", content = home)

    @Test
    fun allFamilia() = capture("r1bis_tout_familia", content = all)

    @Test
    @Config(fontScale = 2f)
    fun allFamiliaFontScale200() = capture("r1bis_tout_familia_police_200", content = all)

    private fun capture(
        name: String,
        theme: FamiliaThemeId = FamiliaThemeId.Classic,
        titleFont: TitleFont = TitleFont.Round,
        bodyFont: BodyFont = BodyFont.Normal,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme(darkTheme = false, theme = theme, seeds = true, titleFont = titleFont, bodyFont = bodyFont) { content() }
        }
    }
}
