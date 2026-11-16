package fr.familia.app.feature.weeklyquestion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member

/** Connects the question of the week to its ViewModel; [questionId] null is this week's. */
@Composable
fun WeeklyQuestionRoute(container: AppContainer?, currentMember: Member, questionId: String?, onBack: () -> Unit, onArchive: () -> Unit = {}) {
    val viewModel: WeeklyQuestionViewModel = viewModel(
        key = "weeklyQuestion-${questionId.orEmpty()}",
        factory = viewModelFactory {
            initializer {
                WeeklyQuestionViewModel(
                    container?.weeklyQuestionRepository,
                    container?.memberRepository,
                    currentMember.id,
                    questionId,
                    uploader = container?.photoUploader,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WeeklyQuestionScreen(
        state = state,
        onAnswer = viewModel::answer,
        onSkip = viewModel::skip,
        onRemove = viewModel::removeAnswer,
        onDismissError = viewModel::dismissError,
        onBack = onBack,
        onArchive = onArchive,
    )
}
