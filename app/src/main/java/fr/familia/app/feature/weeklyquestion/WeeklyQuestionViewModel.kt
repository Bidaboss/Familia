package fr.familia.app.feature.weeklyquestion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.WeeklyQuestionRepository
import fr.familia.app.core.model.WEEKLY_ANSWER_MAX
import fr.familia.app.core.model.WeeklyAnswer
import fr.familia.app.core.model.WeeklyQuestion
import fr.familia.app.core.model.weeklyPhotoPath
import fr.familia.app.core.model.weeklyThumbnailPath
import fr.familia.app.core.photo.PhotoUploader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The question of the week (récit 37): this week's when [questionId] is null, or an older one
 * opened from « Nos réponses » or a notification.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyQuestionViewModel(
    private val questions: WeeklyQuestionRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val questionId: String?,
    private val clock: () -> Instant = Instant::now,
    private val uploader: PhotoUploader? = null,
) : ViewModel() {

    private data class Action(val busy: Boolean = false, val failed: Boolean = false)

    private val action = MutableStateFlow(Action())

    private val question: Flow<WeeklyQuestion?> = when {
        questions == null -> flowOf(null)
        questionId != null -> questions.observeQuestion(questionId)
        else -> questions.observeLatest(1).map { it.firstOrNull() }
    }

    private val source: Flow<WeeklyQuestionUiState> = question.flatMapLatest { q ->
        if (q == null || questions == null) {
            flowOf(WeeklyQuestionUiState.None)
        } else {
            combine(questions.observeMyAnswer(q.id, currentMemberId), revealed(q)) { mine, open -> mine to open }.flatMapLatest { (mine, open) ->
                val now = clock()
                val canSee = mine != null || open
                val others = if (canSee) questions.observeAnswers(q.id) else flowOf(emptyList())
                combine(others, action) { all, act ->
                    WeeklyQuestionUiState.Success(
                        question = q,
                        myAnswer = mine,
                        answers = visibleAnswers(all, currentMemberId),
                        canSeeAnswers = canSee,
                        now = now,
                        isBusy = act.busy,
                        failed = act.failed,
                    )
                }
            }
        }
    }.catch { emit(WeeklyQuestionUiState.None) }

    /** False until [WeeklyQuestion.revealAt], then true, even while the screen stays open. */
    private fun revealed(q: WeeklyQuestion): Flow<Boolean> = flow {
        val wait = q.revealAt.toEpochMilli() - clock().toEpochMilli()
        if (wait > 0) {
            emit(false)
            delay(wait)
        }
        emit(true)
    }

    val uiState: StateFlow<WeeklyQuestionUiState> =
        source.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklyQuestionUiState.Loading)

    /**
     * Answers, or changes one's answer. An empty text does nothing: « Passer » is its own button.
     * [newPhoto] (a content URI) is sent first and replaces the photo; otherwise [keepPhoto] keeps
     * the photo already there or drops it. The server deletes a photo no answer names any more.
     */
    fun answer(text: String, newPhoto: String? = null, keepPhoto: Boolean = true) {
        val clean = cleanAnswer(text) ?: return
        val kept = (uiState.value as? WeeklyQuestionUiState.Success)?.myAnswer?.photoId?.takeIf { keepPhoto }
        write { q, name ->
            val repo = questions ?: return@write
            val photoId = if (newPhoto != null) {
                val send = uploader ?: error("No photo uploader")
                repo.newPhotoId().also { id ->
                    send.upload(newPhoto, weeklyPhotoPath(q.id, currentMemberId, id), weeklyThumbnailPath(q.id, currentMemberId, id))
                }
            } else {
                kept
            }
            repo.answer(q.id, currentMemberId, name, clean, photoId)
        }
    }

    /** « Passer »: no answer this week, and the others' answers show. */
    fun skip() = write { q, name -> questions?.answer(q.id, currentMemberId, name, null) }

    /** Takes back one's answer or one's « Passer ». */
    fun removeAnswer() = write { q, _ -> questions?.removeAnswer(q.id, currentMemberId) }

    fun dismissError() {
        action.value = Action()
    }

    private fun write(block: suspend (WeeklyQuestion, String) -> Unit) {
        val state = uiState.value as? WeeklyQuestionUiState.Success ?: return
        val family = members ?: return
        if (action.value.busy) return
        viewModelScope.launch {
            action.value = Action(busy = true)
            action.value = try {
                // The rules check the first name against the member document.
                val name = family.observeFamily().first().members.first { it.id == currentMemberId }.firstName
                block(state.question, name)
                Action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Action(failed = true)
            }
        }
    }
}

/** The others' real answers, oldest first: never one's own (shown apart) nor a « Passer ». */
internal fun visibleAnswers(all: List<WeeklyAnswer>, currentMemberId: String): List<WeeklyAnswer> =
    all.filter { it.memberId != currentMemberId && !it.skipped && !it.text.isNullOrBlank() }

/** Trimmed, cut at [WEEKLY_ANSWER_MAX], null when empty. */
internal fun cleanAnswer(text: String): String? = text.trim().take(WEEKLY_ANSWER_MAX).ifEmpty { null }
