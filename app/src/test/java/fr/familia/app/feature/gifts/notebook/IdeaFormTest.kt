package fr.familia.app.feature.gifts.notebook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import fr.familia.app.core.data.LinkPreview
import fr.familia.app.core.data.LinkPreviewRepository
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.checkAccessibility
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.events.TestFamily
import fr.familia.app.feature.gifts.item.GiftItemFormError
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * « Ajouter une idée » after the prototype (correctif E11 à E13): « Une idée pour X », the 🔒, the
 * link with « Coller » and its picture, « Où trouver le cadeau ? ». Fictional family of TestFamily.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2000dp-xxhdpi")
class IdeaFormTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val image = "https://cdn.boutique-exemple.fr/casque.jpg"
    private val previews = LinkPreviewRepository { LinkPreview("Casque audio Bluetooth", image, 49.9) }

    private suspend fun NotebookIdeaViewModel.editing() =
        uiState.first { it is NotebookIdeaUiState.Editing } as NotebookIdeaUiState.Editing

    @Test
    fun `« Coller » fills the idea and keeps the picture of its link`() = runTest {
        val notebook = FakeNotebook()
        val vm = NotebookIdeaViewModel(notebook, FakeMembers(), "adult1", null, startPersonId = "adult2", previews = previews)
        vm.editing()
        vm.pasteLink("https://boutique-exemple.fr/casque")
        val state = vm.uiState.first { it is NotebookIdeaUiState.Editing && it.imageUrl != null } as NotebookIdeaUiState.Editing
        assertEquals("Casque audio Bluetooth", state.form.title)
        assertEquals("49,90", state.form.price)
        assertEquals("En ligne · boutique-exemple.fr", state.form.whereToFind)
        vm.save()
        vm.uiState.first { it == NotebookIdeaUiState.Done }
        val saved = notebook.entries.value.single()
        assertEquals(image, saved.imageUrl)
        assertEquals("En ligne · boutique-exemple.fr", saved.whereToFind)
        assertEquals("adult2", saved.personId)
    }

    @Test
    fun `never more than 120 characters to say where`() = runTest {
        val vm = NotebookIdeaViewModel(FakeNotebook(), FakeMembers(), "adult1", null, startPersonId = "adult2")
        vm.editing()
        vm.update { it.copy(title = "Un pull", whereToFind = "x".repeat(121)) }
        vm.save()
        assertTrue(GiftItemFormError.WhereTooLong in vm.editing().itemErrors)
    }

    private fun fromMember(surprise: Boolean = false) = NotebookIdeaUiState.Editing(
        form = NotebookIdeaForm(personId = "adult2"),
        isNew = true,
        people = listOf(TestFamily.otherAdult, TestFamily.kid),
        fixedPerson = TestFamily.otherAdult,
        fromSurprise = surprise,
    )

    private fun count(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun `from a member, « Une idée pour » them, only I see it, no choice of person`() {
        compose.setContent { FamiliaTheme { NotebookIdeaScreen(fromMember(), {}, {}, {}, {}) } }
        assertEquals(1, count("Une idée pour Adult2"))
        assertEquals(1, count("Toi seul·e verras cette idée. Adult2 et les autres ne la voient pas."))
        assertEquals(1, count("Lien du cadeau"))
        assertEquals(1, count("Où trouver le cadeau ?"))
        assertEquals(1, count("Note pour moi"))
        assertEquals(1, count("Enregistrer l'idée"))
        assertEquals(0, count("Pour qui"))
        assertEquals(0, count("Où en est-il"))
    }

    @Test
    fun `from a surprise, mine until I add it`() {
        compose.setContent { FamiliaTheme { NotebookIdeaScreen(fromMember(surprise = true), {}, {}, {}, {}) } }
        assertEquals(1, count("tant que tu ne l'ajoutes pas à la surprise. Adult2 ne la voit jamais."))
    }

    @Test
    fun `every target of the idea is big enough and named`() = compose.checkAccessibility {
        NotebookIdeaScreen(fromMember(), {}, {}, {}, {})
    }
}
