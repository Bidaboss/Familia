package fr.familia.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import fr.familia.app.core.model.BirthdayCard
import fr.familia.app.core.model.WeeklyAnswer
import fr.familia.app.core.model.WeeklyQuestion
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.feature.firststeps.FirstStep
import fr.familia.app.feature.firststeps.FirstStepLine
import fr.familia.app.feature.firststeps.FirstStepsCard
import fr.familia.app.feature.firststeps.FirstStepsCardContent
import fr.familia.app.feature.gooddeals.list.GoodDealsScreen
import fr.familia.app.feature.gooddeals.list.previewGoodDeals
import fr.familia.app.feature.lending.list.LendItemsScreen
import fr.familia.app.feature.lending.list.previewLendItems
import fr.familia.app.feature.turns.TurnDetailActions
import fr.familia.app.feature.turns.TurnDetailScreen
import fr.familia.app.feature.turns.TurnsScreen
import fr.familia.app.feature.turns.previewTurnDetail
import fr.familia.app.feature.turns.previewTurns
import fr.familia.app.feature.wishes.BirthdayCardsContent
import fr.familia.app.feature.wishes.BirthdayCardsState
import fr.familia.app.feature.yearinreview.ReviewPage
import fr.familia.app.feature.yearinreview.YearInReviewScreen
import fr.familia.app.feature.yearinreview.YearInReviewUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

/**
 * The 200 % font pass of the small modules (PRODUIT, 17/11): Bons plans, On se prête, À qui le tour ?,
 * Notre année, Mes premiers pas and the cartes de vœux. Entraide and Bonnes nouvelles
 * already have theirs in [ScreenshotTest]. Fictional data.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2f)
class EntraideA11yScreenshotTest {

    private val today = LocalDate.of(2026, 11, 20)

    @Test
    fun goodDealsFontScale200() = capture("bons_plans_police_200") {
        Surface { GoodDealsScreen(previewGoodDeals(), today, {}, {}, {}) }
    }

    @Test
    fun lendItemsFontScale200() = capture("on_se_prete_police_200") {
        Surface { LendItemsScreen(previewLendItems(), {}, {}, {}) }
    }

    @Test
    fun turnsFontScale200() = capture("a_qui_le_tour_police_200") {
        Surface { TurnsScreen(previewTurns(), {}, {}, {}) }
    }

    @Test
    fun turnDetailFontScale200() = capture("a_qui_le_tour_detail_police_200") {
        Surface { TurnDetailScreen(previewTurnDetail(), TurnDetailActions(), {}) }
    }

    @Test
    fun turnSwapFontScale200() = capture("a_qui_le_tour_echange_police_200") {
        Surface { TurnDetailScreen(previewTurnDetail(swapForUs = true), TurnDetailActions(), {}) }
    }

    @Test
    fun yearQuestionFontScale200() = capture("notre_annee_question_police_200") {
        val question = WeeklyQuestion("q1", "Ton meilleur souvenir de l'été ?", null, Instant.parse("2026-07-06T08:00:00Z"), Instant.parse("2026-07-08T08:00:00Z"), 4)
        val answers = listOf(
            WeeklyAnswer("camille", "Camille", "Le pique-nique au bord du lac, avec les cousins.", false, null),
            WeeklyAnswer("ines", "Inès", "La nuit sous la tente dans le jardin.", false, null),
            WeeklyAnswer("noe", "Noé", "La fête de la musique, quand tout le monde a chanté.", false, null),
            WeeklyAnswer("lou", "Lou", "Les glaces tous les soirs de la semaine à la mer.", false, null),
        )
        YearInReviewScreen(YearInReviewUiState(loading = false, pages = listOf(ReviewPage.Question(question, answers), ReviewPage.Thanks)), year = 2026, onClose = {})
    }

    @Test
    fun firstStepsFontScale200() = capture("mes_premiers_pas_police_200") {
        Page {
            FirstStepsCardContent(
                FirstStepsCard.Steps(
                    listOf(
                        FirstStepLine(FirstStep.Photo, done = true),
                        FirstStepLine(FirstStep.Tastes, done = false),
                        FirstStepLine(FirstStep.Reaction, done = false),
                    ),
                ),
                {}, {}, {}, {}, {},
            )
        }
    }

    @Test
    fun wishesFontScale200() = capture("cartes_de_voeux_police_200") {
        val mine = BirthdayCard("noe", "camille", "Camille", "Joyeux anniversaire, profite bien de ta journée !", 2026)
        val state = BirthdayCardsState(
            cards = listOf(mine, BirthdayCard("noe", "ines", "Inès", "Plein de bisous et une belle année !", 2026)),
            canWrite = true,
            mine = mine,
        )
        Page { BirthdayCardsContent(state, "Noé", {}, {}) }
    }

    @Composable
    private fun Page(content: @Composable () -> Unit) {
        Surface {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { content() }
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            FamiliaTheme { content() }
        }
    }
}
