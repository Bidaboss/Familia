package fr.familia.app.feature.a11y

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.familia.app.core.model.ChallengeMeasure
import fr.familia.app.core.model.FamilyTree
import fr.familia.app.core.model.MonthChallenge
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.checkAccessibility
import fr.familia.app.feature.tree.TreeUiState
import fr.familia.app.feature.tree.challenges.ChallengeHowContent
import fr.familia.app.feature.tree.challenges.ChallengeSheet
import fr.familia.app.feature.tree.challenges.ChallengesScreen
import fr.familia.app.feature.tree.challenges.challengePrinciple
import fr.familia.app.feature.tree.challenges.explain
import fr.familia.app.feature.tree.challenges.questionPrinciple
import fr.familia.app.feature.weeklyquestion.WeeklyQuestionUiState
import fr.familia.app.feature.weeklyquestion.previewWeeklyQuestion
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/** Police 200 % and TalkBack for « Défis » with « Comment ça marche » (PRD récit 65). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2f)
class ChallengeScreensAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val december = YearMonth.of(2026, 12)
    private val today = LocalDate.of(2026, 12, 9)
    private val christmas = MonthChallenge(december, "20 photos de Noël", "🎄", 20, 7, false, measure = ChallengeMeasure.Photos)
    private val tree = TreeUiState(FamilyTree(leaves = 74, bloomedMonths = listOf("2026-11")), christmas, december)

    @Test
    fun `Défis with the cards to touch`() = compose.checkAccessibility {
        ChallengesScreen(tree, previewWeeklyQuestion(answered = false), {}, {}, {}, {}, onShareGoodNews = {}, details = true, today = today)
    }

    @Test
    fun `the whole card of the question opens it, even without the switch`() {
        var opened = false
        compose.setContent {
            FamiliaTheme { Surface { ChallengesScreen(tree, previewWeeklyQuestion(answered = true), {}, {}, {}, { opened = true }, today = today) } }
        }
        compose.onNodeWithText(previewWeeklyQuestion(answered = true).question.text, substring = true).performClick()
        assertTrue(opened)
    }

    @Test
    fun `the challenge card opens « Comment ça marche »`() {
        compose.setContent {
            FamiliaTheme { Surface { ChallengesScreen(tree, WeeklyQuestionUiState.None, {}, {}, {}, {}, onShareGoodNews = {}, details = true, today = today) } }
        }
        compose.onNodeWithText("20 photos de Noël", substring = true).assertHasClickAction()
    }

    @Test
    fun `Comment ça marche, for a challenge of photos`() = compose.checkAccessibility {
        ChallengeHowContent(ChallengeSheet.Challenge(explain(christmas, today, null, canShareGoodNews = true)), {}, {})
    }

    @Test
    fun `Comment ça marche, for the challenge of a foyer with its word`() = compose.checkAccessibility {
        val hosted = christmas.copy(hostHouseholdId = "h2", measure = ChallengeMeasure.Linked)
        ChallengeHowContent(ChallengeSheet.Challenge(explain(hosted, today, "Vos plus beaux sapins, décorés par les enfants.", true)), {}, {})
    }

    @Test
    fun `before the first challenge and question, the day they start`() {
        val before = LocalDate.of(2026, 11, 21)
        compose.setContent {
            FamiliaTheme {
                Surface {
                    androidx.compose.foundation.layout.Column {
                        ChallengeHowContent(challengePrinciple(before), {}, null)
                        ChallengeHowContent(questionPrinciple(before), {}, null)
                    }
                }
            }
        }
        compose.onNodeWithText("Premier défi le 1er décembre.").assertExists()
        compose.onNodeWithText("Première question le lundi 30 novembre.").assertExists()
    }
}
