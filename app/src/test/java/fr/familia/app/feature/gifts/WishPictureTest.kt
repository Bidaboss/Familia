package fr.familia.app.feature.gifts

import fr.familia.app.core.data.LinkPreview
import fr.familia.app.core.data.LinkPreviewRepository
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.gifts.item.GiftItemEditUiState
import fr.familia.app.feature.gifts.item.GiftItemEditViewModel
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * The picture of a wish (correctif E2 à E9): its photo, else the picture of its link, even when the
 * link was typed by hand, and only then 🎁. Fictional family of TestFamily.
 */
class WishPictureTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val list = GiftList("wishes-adult1", GiftListKind.Wishes, "Mes envies", null, null, "adult1", emptyList(), "adult1", "adult1")
    private val image = "https://cdn.boutique.example/toupie.jpg"

    @Test
    fun `a link is read once, a page without picture too`() = runTest {
        var asked = 0
        val images = LinkImages(LinkPreviewRepository { url -> asked++; LinkPreview(null, if (url.endsWith("toupie")) image else null, null) })
        assertEquals(image, images.imageOf("https://boutique.example/toupie"))
        assertEquals(image, images.imageOf("https://boutique.example/toupie"))
        assertNull(images.imageOf("https://boutique.example/rien"))
        assertNull(images.imageOf("https://boutique.example/rien"))
        assertEquals(2, asked)
        assertEquals(image, images.cached("https://boutique.example/toupie"))
    }

    private fun previews() = LinkPreviewRepository { LinkPreview("Une toupie", image, 40.72) }

    @Test
    fun `a link typed by hand gives its picture once typing stops`() = runTest {
        val vm = GiftItemEditViewModel(FakeGifts(list), FakeMembers(), "adult1", list.id, null, previews = previews())
        vm.uiState.first { it is GiftItemEditUiState.Editing }
        vm.update { it.copy(url = "https://boutique.example/toupie") }
        val state = vm.uiState.first { it is GiftItemEditUiState.Editing && it.imageUrl != null } as GiftItemEditUiState.Editing
        assertEquals(image, state.imageUrl)
        assertEquals("Une toupie", state.form.title)
    }

    @Test
    fun `a wish saved without its picture finds it when opened`() = runTest {
        val gifts = FakeGifts(list)
        gifts.items.value = listOf(
            GiftItem("t", "Toupie", "https://boutique.example/toupie", 40.0, null, "adult1", "Adult1", visibleToBeneficiary = true),
        )
        val vm = GiftItemEditViewModel(gifts, FakeMembers(), "adult1", list.id, "t", previews = previews())
        val state = vm.uiState.first { it is GiftItemEditUiState.Editing && it.imageUrl != null } as GiftItemEditUiState.Editing
        assertEquals(image, state.imageUrl)
        // What the member wrote stays.
        assertEquals("Toupie", state.form.title)
    }
}
