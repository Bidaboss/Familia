package fr.familia.app.feature.weeklyquestion

import fr.familia.app.core.model.WeeklyAnswer
import fr.familia.app.core.model.WeeklyQuestion
import java.time.Instant

sealed interface WeeklyQuestionUiState {
    data object Loading : WeeklyQuestionUiState

    /** No question yet (before Monday 30 November), or the one asked does not exist. */
    data object None : WeeklyQuestionUiState

    /**
     * [answers] are the others' real answers (no « Passer »), shown once [canSeeAnswers]: after
     * answering or passing, or for everyone from revealAt (avis [EXPERT] n° 8).
     */
    data class Success(
        val question: WeeklyQuestion,
        val myAnswer: WeeklyAnswer?,
        val answers: List<WeeklyAnswer>,
        val canSeeAnswers: Boolean,
        val now: Instant,
        val isBusy: Boolean = false,
        val failed: Boolean = false,
    ) : WeeklyQuestionUiState
}
