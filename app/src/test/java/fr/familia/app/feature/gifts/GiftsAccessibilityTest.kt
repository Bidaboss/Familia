package fr.familia.app.feature.gifts

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import fr.familia.app.core.model.MemberCard
import fr.familia.app.core.ui.checkAccessibility
import fr.familia.app.feature.gifts.detail.GiftListActions
import fr.familia.app.feature.gifts.detail.GiftListScreen
import fr.familia.app.feature.gifts.detail.previewGiftList
import fr.familia.app.feature.gifts.edit.GiftListEditScreen
import fr.familia.app.feature.gifts.edit.previewGiftListForm
import fr.familia.app.feature.gifts.item.GiftItemEditScreen
import fr.familia.app.feature.gifts.item.previewGiftItemForm
import fr.familia.app.feature.gifts.notebook.NotebookActions
import fr.familia.app.feature.gifts.notebook.NotebookIdeaScreen
import fr.familia.app.feature.gifts.notebook.NotebookScreen
import fr.familia.app.feature.gifts.notebook.ProposeIdeaActions
import fr.familia.app.feature.gifts.notebook.ProposeIdeaScreen
import fr.familia.app.feature.gifts.notebook.previewNotebook
import fr.familia.app.feature.gifts.notebook.previewNotebookIdea
import fr.familia.app.feature.gifts.notebook.previewProposeIdea
import fr.familia.app.feature.members.card.MemberCardContent
import fr.familia.app.feature.members.card.MemberCardUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Police 200 % and TalkBack on the screens of the Cadeaux (passe d'accessibilité avant le 21), with
 * the preview states of the screens: at twice the text size, everything one can touch says what it
 * is (a text or a description, so TalkBack reads it) and is at least 48 dp high.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2f)
class GiftsAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private fun check(content: @Composable () -> Unit) = compose.checkAccessibility(content)

    @Test
    fun `the Cadeaux tab`() = check { GiftsScreen(previewGifts(), {}, {}) }

    /** A surprise in progress, a birthday soon (récit 91). */
    private fun withSurprise() = previewGifts().copy(surpriseButton = true, surprisePreparedFor = setOf("b"))

    @Test
    fun `the Cadeaux tab with a surprise`() = check { GiftsScreen(withSurprise(), {}, {}, onOpenSurprisePicker = {}, onOpenMyPage = {}) }

    @Test
    fun `the Cadeaux tab of a child`() = check {
        GiftsScreen(withSurprise().copy(canPrepareSurprise = false), {}, {}, onOpenSurprisePicker = {}, onOpenMyPage = {})
    }

    @Test
    fun `a list, or « Mes envies »`() = check { GiftListScreen(previewGiftList(), GiftListActions()) }

    @Test
    fun `my own list`() = check { GiftListScreen(previewGiftList(keptFromMe = true), GiftListActions()) }

    @Test
    fun `the form of a list`() = check { GiftListEditScreen(previewGiftListForm(), {}, {}, {}, {}) }

    @Test
    fun `the form of a wish`() = check { GiftItemEditScreen(previewGiftItemForm(), {}, {}, {}, {}) }

    @Test
    fun `the notebook and its budget`() = check { NotebookScreen(previewNotebook(), NotebookActions()) }

    @Test
    fun `an idea of the notebook`() = check { NotebookIdeaScreen(previewNotebookIdea(), {}, {}, {}, {}) }

    @Test
    fun `propose an idea`() = check { ProposeIdeaScreen(previewProposeIdea(), ProposeIdeaActions(), today = LocalDate.of(2026, 11, 16)) }

    @Test
    fun `« Goûts et passions » on the page of a member`() = check {
        MemberCardContent(
            MemberCardUiState.Ready(
                MemberCard("a", shoes = "39", tastes = "Le chocolat noir, les polars", hobbies = "La randonnée, le jardin"),
                firstName = "Camille",
                isMe = true,
                canEdit = true,
            ),
            onSave = { true },
        )
    }
}
