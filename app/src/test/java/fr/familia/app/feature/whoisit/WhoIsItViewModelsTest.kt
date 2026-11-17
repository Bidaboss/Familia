package fr.familia.app.feature.whoisit

import androidx.lifecycle.SavedStateHandle
import fr.familia.app.core.data.WhoIsItRepository
import fr.familia.app.core.model.WhoIsItAnswer
import fr.familia.app.core.model.WhoIsItGuess
import fr.familia.app.core.model.WhoIsItPost
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Plays the rules like Firestore: the answer and the guesses show to the author and to those who
 * guessed, and a listener refused at the start stays empty for good (it is never reopened).
 */
private class FakeWhoIsIt(private val me: String) : WhoIsItRepository {
    val posts = MutableStateFlow(listOf(WhoIsItPost("w1", "adult2", "Inès", "Un mariage", null)))
    val answers = MutableStateFlow(mapOf("w1" to WhoIsItAnswer("Mamie Jeanne", 1961)))
    val guesses = MutableStateFlow<Map<String, List<WhoIsItGuess>>>(emptyMap())
    val published = mutableListOf<Pair<WhoIsItPost, WhoIsItAnswer>>()
    val deleted = mutableListOf<String>()

    private fun mayRead(id: String) = posts.value.firstOrNull { it.id == id }?.authorId == me || guesses.value[id].orEmpty().any { it.memberId == me }

    override fun observeLatest(count: Int): Flow<List<WhoIsItPost>> = posts.map { it.take(count) }
    override fun observePost(id: String): Flow<WhoIsItPost?> = posts.map { list -> list.firstOrNull { it.id == id } }
    override fun observeAnswer(id: String): Flow<WhoIsItAnswer?> = if (mayRead(id)) answers.map { it[id] } else flowOf(null)
    override fun observeGuesses(id: String): Flow<List<WhoIsItGuess>> = if (mayRead(id)) guesses.map { it[id].orEmpty() } else flowOf(emptyList())
    override fun newPostId(): String = "new"
    override suspend fun publish(post: WhoIsItPost, answer: WhoIsItAnswer) {
        published += post to answer
    }
    override suspend fun setAnswer(id: String, answer: WhoIsItAnswer) = Unit
    override suspend fun guess(id: String, guess: WhoIsItGuess) {
        guesses.value = guesses.value + (id to guesses.value[id].orEmpty() + guess)
    }
    override suspend fun delete(id: String) {
        deleted += id
    }
}

private class FakeMemory : WhoIsItMemory {
    val saved = mutableMapOf<String, Set<String>>()
    override fun load(memberId: String, kind: String): Set<String> = saved["$memberId-$kind"].orEmpty()
    override fun save(memberId: String, kind: String, ids: Set<String>) {
        saved["$memberId-$kind"] = ids
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WhoIsItViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the answer stays hidden until one guesses, then shows with every guess`() = runTest {
        val repo = FakeWhoIsIt("adult1")
        val memory = FakeMemory()
        val vm = WhoIsItPostViewModel(repo, "adult1", "Camille", "w1", isOwner = false, memory = memory, thisYear = 2026)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fun ready() = vm.uiState.value as WhoIsItPostUiState.Ready
        assertFalse(ready().revealed)
        assertTrue(ready().canGuess)
        assertFalse(ready().canDelete)

        vm.setWho("Mamie Jeanne")
        vm.setYear("1700")
        vm.guess()
        assertTrue(ready().yearInvalid)
        assertFalse(ready().revealed)

        vm.setYear("1965")
        vm.guess()
        assertTrue(ready().revealed)
        assertEquals("Mamie Jeanne", ready().answer?.who)
        assertEquals(listOf(WhoIsItGuess("adult1", "Camille", "Mamie Jeanne", 1965)), ready().guesses)
        assertEquals(setOf("w1"), memory.load("adult1", GUESSED))
    }

    @Test
    fun `« Je ne sais pas » shows the answer too, and the author never guesses`() = runTest {
        val repo = FakeWhoIsIt("adult1")
        val vm = WhoIsItPostViewModel(repo, "adult1", "Camille", "w1", isOwner = false)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.dontKnow()
        val guess = repo.guesses.value["w1"]!!.single()
        assertEquals("", guess.who)
        assertNull(guess.year)

        val author = WhoIsItPostViewModel(FakeWhoIsIt("adult2"), "adult2", "Inès", "w1", isOwner = false)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { author.uiState.collect {} }
        val ready = author.uiState.value as WhoIsItPostUiState.Ready
        assertTrue(ready.revealed)
        assertFalse(ready.canGuess)
        assertTrue(ready.canDelete)
    }

    @Test
    fun `in « Voir comme » one reads but never guesses nor removes`() = runTest {
        val repo = FakeWhoIsIt("adult1")
        val vm = WhoIsItPostViewModel(repo, "adult1", "Camille", "w1", isOwner = true, canWrite = false)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val ready = vm.uiState.value as WhoIsItPostUiState.Ready
        assertFalse(ready.canGuess)
        assertFalse(ready.canDelete)
        vm.dontKnow()
        vm.delete()
        assertTrue(repo.guesses.value.isEmpty())
        assertTrue(repo.deleted.isEmpty())
    }

    @Test
    fun `the Accueil offers the newest photo of someone else, until it is set aside`() = runTest {
        val repo = FakeWhoIsIt("adult1")
        repo.posts.value = listOf(
            WhoIsItPost("mine", "adult1", "Camille", "", null),
            WhoIsItPost("w2", "adult2", "Inès", "", null),
            WhoIsItPost("w1", "adult2", "Inès", "", null),
        )
        val memory = FakeMemory().apply { save("adult1", GUESSED, setOf("w2")) }
        val home = WhoIsItHomeViewModel(repo, "adult1", memory)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { home.postId.collect {} }
        assertEquals("w1", home.postId.value) // Not mine, not already guessed here.

        home.dismiss("w1")
        assertNull(home.postId.value)
        assertEquals(setOf("w1"), memory.load("adult1", DISMISSED))
    }

    @Test
    fun `a photo is published with its answer, and needs a photo and a name`() = runTest {
        val repo = FakeWhoIsIt("adult1")
        val uploads = mutableListOf<String>()
        val vm = WhoIsItEditViewModel(repo, { _, photo, thumb -> uploads += photo; uploads += thumb }, "adult1", "Camille", SavedStateHandle(), thisYear = 2026)
        vm.publish()
        val errors = (vm.uiState.value as WhoIsItEditUiState.Editing).errors
        assertEquals(setOf(WhoIsItFormError.PhotoMissing, WhoIsItFormError.WhoMissing), errors)

        vm.setPhoto("content://photo")
        vm.setWho("Mamie Jeanne")
        vm.setYear("1961")
        vm.setHint("Un mariage")
        vm.publish()
        assertEquals(WhoIsItEditUiState.Done("new"), vm.uiState.value)
        assertEquals(listOf("whoIsIt/adult1/new/photo.jpg", "whoIsIt/adult1/new/photo-thumb.jpg"), uploads)
        assertEquals(WhoIsItAnswer("Mamie Jeanne", 1961), repo.published.single().second)
        assertEquals("Un mariage", repo.published.single().first.hint)
    }

    @Test
    fun `the chosen photo survives Android stopping the app with the rest of the draft`() = runTest {
        val saved = SavedStateHandle()
        val first = WhoIsItEditViewModel(FakeWhoIsIt("adult1"), { _, _, _ -> }, "adult1", "Camille", saved, thisYear = 2026)
        first.setPhoto("content://photo")
        first.setWho("Tonton Paul")

        val form = (WhoIsItEditViewModel(FakeWhoIsIt("adult1"), { _, _, _ -> }, "adult1", "Camille", saved, thisYear = 2026).uiState.value as WhoIsItEditUiState.Editing).form

        assertEquals("content://photo", form.photo)
        assertEquals("Tonton Paul", form.who)
    }
}
