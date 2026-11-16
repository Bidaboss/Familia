package fr.familia.app.feature.weeklyquestion

import fr.familia.app.core.data.WeeklyQuestionRepository
import fr.familia.app.core.model.WeeklyAnswer
import fr.familia.app.core.model.WeeklyQuestion
import fr.familia.app.feature.events.FakeMembers
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** A question of the week in memory; the answers of others are given as the rules would. */
private class FakeWeeklyQuestions(questions: List<WeeklyQuestion>, answers: List<Pair<String, WeeklyAnswer>> = emptyList()) :
    WeeklyQuestionRepository {
    val questions = MutableStateFlow(questions)
    val answers = MutableStateFlow(answers)

    override fun observeLatest(count: Int): Flow<List<WeeklyQuestion>> =
        questions.map { list -> list.sortedByDescending { it.id }.take(count) }

    override fun observeQuestion(id: String): Flow<WeeklyQuestion?> = questions.map { list -> list.firstOrNull { it.id == id } }

    override fun observeMyAnswer(questionId: String, memberId: String): Flow<WeeklyAnswer?> =
        answers.map { list -> list.firstOrNull { it.first == questionId && it.second.memberId == memberId }?.second }

    override fun observeAnswers(questionId: String): Flow<List<WeeklyAnswer>> =
        answers.map { list -> list.filter { it.first == questionId }.map { it.second } }

    override suspend fun answer(questionId: String, memberId: String, memberName: String, text: String?) {
        answers.value = answers.value.filterNot { it.first == questionId && it.second.memberId == memberId } +
            (questionId to WeeklyAnswer(memberId, memberName, text, text == null, null))
    }

    override suspend fun removeAnswer(questionId: String, memberId: String) {
        answers.value = answers.value.filterNot { it.first == questionId && it.second.memberId == memberId }
    }
}

class WeeklyQuestionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val published = Instant.parse("2026-11-30T08:00:00Z")
    private val monday = WeeklyQuestion("2026-11-30", "Ton petit-déjeuner idéal ?", null, published, published.plusSeconds(48 * 3600), 1)
    private val older = monday.copy(id = "2026-11-23", text = "Ton plat préféré ?")
    private val ines = "2026-11-30" to WeeklyAnswer("adult2", "Adult2", "Des crêpes", false, published)

    private fun viewModel(repo: FakeWeeklyQuestions, at: Instant = published.plusSeconds(3600), questionId: String? = null) =
        WeeklyQuestionViewModel(repo, FakeMembers(), "adult1", questionId, clock = { at })

    @Test
    fun `before answering, the others' answers stay hidden until 48 h`() = runTest {
        val vm = viewModel(FakeWeeklyQuestions(listOf(older, monday), listOf(ines)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val state = vm.uiState.value as WeeklyQuestionUiState.Success
        assertEquals("2026-11-30", state.question.id)
        assertFalse(state.canSeeAnswers)
        assertTrue(state.answers.isEmpty())

        // The screen stays open past revealAt: the answers show without reopening it.
        advanceTimeBy(47 * 3600_000L + 1)
        val revealed = vm.uiState.value as WeeklyQuestionUiState.Success
        assertTrue(revealed.canSeeAnswers)
        assertEquals(listOf("adult2"), revealed.answers.map { it.memberId })
    }

    @Test
    fun `answering shows the others, never one's own twice`() = runTest {
        val repo = FakeWeeklyQuestions(listOf(monday), listOf(ines))
        val vm = viewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.answer("  Du pain perdu  ")
        val state = vm.uiState.value as WeeklyQuestionUiState.Success
        assertEquals("Du pain perdu", state.myAnswer?.text)
        assertEquals("Adult1", state.myAnswer?.memberName)
        assertTrue(state.canSeeAnswers)
        assertEquals(listOf("Des crêpes"), state.answers.map { it.text })
    }

    @Test
    fun `« Passer » shows the others too, and one can still answer or take it back`() = runTest {
        val repo = FakeWeeklyQuestions(listOf(monday), listOf(ines))
        val vm = viewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.skip()
        assertTrue((vm.uiState.value as WeeklyQuestionUiState.Success).myAnswer!!.skipped)
        assertTrue((vm.uiState.value as WeeklyQuestionUiState.Success).canSeeAnswers)
        vm.answer("Finalement : un croissant")
        assertFalse((vm.uiState.value as WeeklyQuestionUiState.Success).myAnswer!!.skipped)
        vm.removeAnswer()
        assertNull((vm.uiState.value as WeeklyQuestionUiState.Success).myAnswer)
    }

    @Test
    fun `48 h after the question, everyone reads the answers`() = runTest {
        val vm = viewModel(FakeWeeklyQuestions(listOf(monday), listOf(ines)), at = published.plusSeconds(49 * 3600))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue((vm.uiState.value as WeeklyQuestionUiState.Success).canSeeAnswers)
    }

    @Test
    fun `an older question opens by its id, and none before the first`() = runTest {
        val vm = viewModel(FakeWeeklyQuestions(listOf(older, monday)), questionId = "2026-11-23")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals("Ton plat préféré ?", (vm.uiState.value as WeeklyQuestionUiState.Success).question.text)
        val empty = viewModel(FakeWeeklyQuestions(emptyList()))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { empty.uiState.collect {} }
        assertEquals(WeeklyQuestionUiState.None, empty.uiState.value)
    }

    @Test
    fun `an empty answer is not sent, a long one is cut`() {
        assertNull(cleanAnswer("   "))
        assertEquals(500, cleanAnswer("x".repeat(600))!!.length)
    }
}
