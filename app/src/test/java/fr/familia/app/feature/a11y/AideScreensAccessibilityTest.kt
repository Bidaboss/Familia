package fr.familia.app.feature.a11y

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import fr.familia.app.core.model.BirthdayCard
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.GoodDeal
import fr.familia.app.core.model.GoodDealKind
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.LendCategory
import fr.familia.app.core.model.LendItem
import fr.familia.app.core.model.WeeklyAnswer
import fr.familia.app.core.model.WeeklyQuestion
import fr.familia.app.core.ui.checkAccessibility
import fr.familia.app.feature.firststeps.FirstStep
import fr.familia.app.feature.firststeps.FirstStepLine
import fr.familia.app.feature.firststeps.FirstStepsCard
import fr.familia.app.feature.firststeps.FirstStepsCardContent
import fr.familia.app.feature.gooddeals.detail.GoodDealDetailScreen
import fr.familia.app.feature.gooddeals.detail.GoodDealDetailUiState
import fr.familia.app.feature.gooddeals.edit.GoodDealEditScreen
import fr.familia.app.feature.gooddeals.edit.GoodDealEditUiState
import fr.familia.app.feature.gooddeals.edit.GoodDealForm
import fr.familia.app.feature.gooddeals.list.GoodDealsScreen
import fr.familia.app.feature.gooddeals.list.previewGoodDeals
import fr.familia.app.feature.goodnews.detail.GoodNewsDetailScreen
import fr.familia.app.feature.goodnews.detail.GoodNewsDetailUiState
import fr.familia.app.feature.goodnews.edit.GoodNewsEditScreen
import fr.familia.app.feature.goodnews.edit.previewGoodNewsEdit
import fr.familia.app.feature.goodnews.feed.GoodNewsFeedScreen
import fr.familia.app.feature.goodnews.feed.GoodNewsFeedUiState
import fr.familia.app.feature.goodnews.previewGoodNewsItems
import fr.familia.app.feature.helprequests.detail.HelpRequestDetailScreen
import fr.familia.app.feature.helprequests.detail.previewHelpRequestDetail
import fr.familia.app.feature.helprequests.edit.HelpRequestEditScreen
import fr.familia.app.feature.helprequests.edit.previewHelpRequestEdit
import fr.familia.app.feature.helprequests.list.HelpRequestsScreen
import fr.familia.app.feature.helprequests.list.previewHelpRequests
import fr.familia.app.feature.lending.detail.LendItemDetailScreen
import fr.familia.app.feature.lending.detail.LendItemDetailUiState
import fr.familia.app.feature.lending.edit.LendItemEditScreen
import fr.familia.app.feature.lending.edit.LendItemEditUiState
import fr.familia.app.feature.lending.edit.LendItemForm
import fr.familia.app.feature.lending.list.LendItemsScreen
import fr.familia.app.feature.lending.list.previewLendItems
import fr.familia.app.feature.members.admin.AdministrationScreen
import fr.familia.app.feature.members.admin.previewAdministrationState
import fr.familia.app.feature.turns.TurnDetailActions
import fr.familia.app.feature.turns.TurnDetailScreen
import fr.familia.app.feature.turns.TurnEditScreen
import fr.familia.app.feature.turns.TurnEditUiState
import fr.familia.app.feature.turns.TurnForm
import fr.familia.app.feature.turns.TurnsScreen
import fr.familia.app.feature.turns.previewTurnDetail
import fr.familia.app.feature.turns.previewTurns
import fr.familia.app.feature.wishes.BirthdayCardsContent
import fr.familia.app.feature.wishes.BirthdayCardsState
import fr.familia.app.feature.yearinreview.ReviewPage
import fr.familia.app.feature.yearinreview.YearInReviewScreen
import fr.familia.app.feature.yearinreview.YearInReviewUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * Police 200 % and TalkBack in the CI for the screens of the coups de main (Entraide, Bons plans,
 * On se prête, À qui le tour ?, Bonnes nouvelles, Notre année, Mes premiers pas, vœux)
 * and Administration > Modules, with [checkAccessibility]. Fictional family only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2f)
class AideScreensAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 20)
    private val now = Instant.parse("2026-11-21T16:00:00Z")

    private fun check(content: @Composable () -> Unit) = compose.checkAccessibility(content)

    /** A section of a page, as it scrolls there. */
    private fun page(content: @Composable () -> Unit) = check { Column(Modifier.verticalScroll(rememberScrollState())) { content() } }

    @Test
    fun `Entraide`() = check { HelpRequestsScreen(previewHelpRequests(), {}, {}, {}) }

    @Test
    fun `a request of Entraide`() = check { HelpRequestDetailScreen(previewHelpRequestDetail(), {}, {}, {}, {}, {}, {}, {}, {}, {}) }

    @Test
    fun `my request of Entraide`() = check { HelpRequestDetailScreen(previewHelpRequestDetail(isMine = true), {}, {}, {}, {}, {}, {}, {}, {}, {}) }

    @Test
    fun `ask for help`() = check { HelpRequestEditScreen(previewHelpRequestEdit(), {}, {}, {}, {}, {}) }

    @Test
    fun `Bons plans`() = check { GoodDealsScreen(previewGoodDeals(), today, {}, {}, {}) }

    @Test
    fun `a bon plan`() = check {
        val deal = GoodDeal(
            "d1", GoodDealKind.Deal, "Fraises à cueillir soi-même", text = "Le champ ouvre samedi, c'est 4 € le kilo.",
            place = "Ferme des Lilas", link = "https://www.exemple.fr/cueillette", endsAt = Instant.parse("2026-11-28T23:00:00Z"),
            interestedIds = listOf("ines"), authorId = "camille", authorName = "Camille",
        )
        GoodDealDetailScreen(
            GoodDealDetailUiState.Ready(deal, isMine = false, canManage = false, iAmInterested = false, interestedNames = listOf("Inès")),
            today, {}, {}, {}, {},
        )
    }

    @Test
    fun `share a bon plan`() = check {
        GoodDealEditScreen(GoodDealEditUiState.Editing(GoodDealForm(title = "Fraises à cueillir", lastDay = today.plusDays(7)), isNew = true, today = today), {}, {}, {})
    }

    @Test
    fun `On se prête`() = check { LendItemsScreen(previewLendItems(), {}, {}, {}) }

    @Test
    fun `a thing to lend`() = check {
        val item = LendItem("i1", "Appareil à raclette", note = "À rendre propre.", category = LendCategory.Kitchen, ownerId = "camille", ownerName = "Camille")
        LendItemDetailScreen(
            LendItemDetailUiState.Ready(item, isMine = false, iAmBorrower = false, canManage = false, canAsk = true, canAnswer = false, canReturn = false, canDropAsk = false),
            LocalDate.of(2026, 12, 1), { _, _ -> }, {}, {}, {}, {}, {},
        )
    }

    @Test
    fun `lend a thing`() = check {
        LendItemEditScreen(LendItemEditUiState.Editing(LendItemForm(name = "Appareil à raclette", category = LendCategory.Kitchen), isNew = true), {}, {}, {})
    }

    @Test
    fun `À qui le tour ?`() = check { TurnsScreen(previewTurns(), {}, {}, {}) }

    @Test
    fun `a turn`() = check { TurnDetailScreen(previewTurnDetail(), TurnDetailActions(), {}) }

    @Test
    fun `a turn, with a swap asked`() = check { TurnDetailScreen(previewTurnDetail(swapForUs = true), TurnDetailActions(), {}) }

    @Test
    fun `a new turn`() = check {
        val households = listOf(Household("h1", "Foyer Camille"), Household("h2", "Foyer Inès"), Household("h3", "Foyer Noé"))
        TurnEditScreen(TurnEditUiState.Editing(TurnForm("Qui reçoit Noël", listOf("h2", "h1")), households, isNew = true), {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun `Bonnes nouvelles`() = check {
        GoodNewsFeedScreen(GoodNewsFeedUiState.Success(previewGoodNewsItems(), canLoadMore = true, now = now), {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun `a bonne nouvelle`() = check {
        GoodNewsDetailScreen(GoodNewsDetailUiState.Success(previewGoodNewsItems()[1], now), {}, {}, {}, {}, {}, {})
    }

    @Test
    fun `share a bonne nouvelle`() = check { GoodNewsEditScreen(previewGoodNewsEdit(), {}, {}, {}, {}, {}, {}, {}, {}) }

    @Test
    fun `Notre année`() = check {
        val question = WeeklyQuestion("q1", "Ton meilleur souvenir de l'été ?", null, Instant.parse("2026-07-06T08:00:00Z"), Instant.parse("2026-07-08T08:00:00Z"), 4)
        val answers = listOf(WeeklyAnswer("camille", "Camille", "Le pique-nique au bord du lac, avec les cousins.", false, null))
        YearInReviewScreen(YearInReviewUiState(loading = false, pages = listOf(ReviewPage.Question(question, answers), ReviewPage.Thanks)), year = 2026, onClose = {})
    }

    @Test
    fun `Mes premiers pas`() = page {
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

    @Test
    fun `the cartes de vœux`() = page {
        val mine = BirthdayCard("noe", "camille", "Camille", "Joyeux anniversaire, profite bien de ta journée !", 2026)
        BirthdayCardsContent(
            BirthdayCardsState(cards = listOf(mine, BirthdayCard("noe", "ines", "Inès", "Plein de bisous et une belle année !", 2026)), canWrite = true, mine = mine),
            "Noé", {}, {},
        )
    }

    @Test
    fun `Administration, with every module`() = check {
        AdministrationScreen(
            previewAdministrationState(),
            onBack = {},
            onRetry = {},
            onSetChef = { _, _ -> },
            onAddHousehold = {},
            onDeleteHousehold = {},
            onOpenExport = {},
            features = Feature.entries.associate { it.key to true },
            onSetFeature = { _, _ -> },
        )
    }
}
