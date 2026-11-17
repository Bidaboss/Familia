package fr.familia.app.feature.genealogy.poster

import fr.familia.app.core.data.MemberPhoto
import fr.familia.app.core.data.MemberPhotoRepository
import fr.familia.app.core.model.KinLink
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.feature.genealogy.FakeGenealogy
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

/** « Faire l'affiche » (récit 53): the choices of the screen, then the printer. Fictional family. */
@OptIn(ExperimentalCoroutinesApi::class)
class TreePosterViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakePhotos(private val photos: Map<String, MemberPhoto>, private val fail: Boolean = false) : MemberPhotoRepository {
        override fun observePhotos(): Flow<Map<String, MemberPhoto>> = if (fail) flow { throw IOException("refused") } else MutableStateFlow(photos)
        override suspend fun savePhoto(memberId: String, jpeg: ByteArray) = Unit
        override suspend fun removePhoto(memberId: String) = Unit
    }

    // TestFamily: adult1 is the parent of kid1, adult2 of kid2.
    private val links = listOf(KinLink.parent("adult1", "kid1"), KinLink.parent("adult2", "kid2"))
    private val jpeg = byteArrayOf(1, 2, 3)

    private fun viewModel(
        links: List<KinLink> = this.links,
        photos: MemberPhotoRepository = FakePhotos(mapOf("kid1" to MemberPhoto("kid1", jpeg, 1), "kid2" to MemberPhoto("kid2", jpeg, 1))),
        print: suspend (PosterContent, (String) -> ByteArray?) -> File? = { _, _ -> File("arbre.pdf") },
    ) = TreePosterViewModel(FakeGenealogy(links = links), FakeMembers(), photos, "adult1", "L'arbre de la famille", 2026, print)

    @Test
    fun `the whole family in A4 with photos at first, and only people with children may start it`() = runTest {
        val state = viewModel().uiState.value as TreePosterUiState.Choosing
        assertEquals(PosterStart.Everyone, state.start)
        assertEquals(PosterFormat.A4, state.format)
        assertTrue(state.withPhotos)
        assertEquals("L'arbre de la famille", state.title)
        assertEquals(listOf("adult1", "adult2"), state.starts.map { it.id })
    }

    @Test
    fun `nobody joined yet, so the screen says to fill the tree first`() = runTest {
        assertEquals(TreePosterUiState.Empty, viewModel(links = emptyList()).uiState.value)
    }

    @Test
    fun `the choices go to the printer, with my rights on the children's photos`() = runTest {
        var printed: PosterContent? = null
        var bytes: ByteArray? = null
        val vm = viewModel(print = { content, photo ->
            printed = content
            bytes = photo("kid1")
            File("arbre.pdf")
        })
        vm.setTitle("  Notre arbre  ")
        vm.setStart(PosterStart.DescendantsOf("adult1"))
        vm.setFormat(PosterFormat.A3)
        vm.make()
        val content = printed!!
        assertEquals("Notre arbre", content.title)
        assertEquals(PosterFormat.A3, content.format)
        assertEquals(listOf(listOf("adult1"), listOf("kid1")), content.rows.map { row -> row.map { it.id } })
        assertEquals("kid1", content.rows[1][0].photoOf)
        assertArrayEquals(jpeg, bytes)
        assertEquals(File("arbre.pdf"), vm.made.value)
        vm.shared()
        assertNull(vm.made.value)
    }

    @Test
    fun `another family's child keeps the initial, and so does everyone without photos`() = runTest {
        var printed: PosterContent? = null
        val vm = viewModel(print = { content, _ -> printed = content; File("arbre.pdf") })
        vm.make()
        assertNull(printed!!.rows.flatten().first { it.id == "kid2" }.photoOf)
        vm.setWithPhotos(false)
        vm.make()
        assertEquals(listOf<String?>(null), printed!!.rows.flatten().map { it.photoOf }.distinct())
    }

    @Test
    fun `photos that cannot be read leave the initials, and a failed print says so`() = runTest {
        var printed: PosterContent? = null
        val vm = viewModel(photos = FakePhotos(emptyMap(), fail = true), print = { content, _ -> printed = content; null })
        vm.make()
        assertEquals(listOf<String?>(null), printed!!.rows.flatten().map { it.photoOf }.distinct())
        val state = vm.uiState.value as TreePosterUiState.Choosing
        assertTrue(state.failed)
        assertFalse(state.isMaking)
        vm.setTitle("Autre")
        assertFalse((vm.uiState.value as TreePosterUiState.Choosing).failed)
    }

    @Test
    fun `a blank title cannot make the poster, and a long one is cut`() = runTest {
        var calls = 0
        val vm = viewModel(print = { _, _ -> calls++; File("arbre.pdf") })
        vm.setTitle("   ")
        assertFalse((vm.uiState.value as TreePosterUiState.Choosing).canMake)
        vm.make()
        assertEquals(0, calls)
        vm.setTitle("x".repeat(80))
        assertEquals(POSTER_TITLE_MAX, (vm.uiState.value as TreePosterUiState.Choosing).title.length)
    }
}
