package fr.familia.app.feature.weeklyquestion

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.home.HomeChip

/**
 * The question of the week on the home screen (récit 37): the question and only the number of
 * answers, never who answered (carte EXPERT-3). Nothing shows before the first question. Placed by
 * the home screen, behind Feature.WeeklyQuestion.
 */
@Composable
fun WeeklyQuestionHomeCard(container: AppContainer?, currentMember: Member, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val state by weeklyQuestionHomeViewModel(container, currentMember).uiState.collectAsStateWithLifecycle()
    (state as? WeeklyQuestionUiState.Success)?.let { WeeklyQuestionHomeContent(it, onOpen, modifier) }
}

/** The chip of the question of the Accueil (DESIGN-11): it shows once there is a question, and opens it. */
@Composable
fun WeeklyQuestionHomeChip(container: AppContainer?, currentMember: Member, onOpen: () -> Unit) {
    val state by weeklyQuestionHomeViewModel(container, currentMember).uiState.collectAsStateWithLifecycle()
    val success = state as? WeeklyQuestionUiState.Success ?: return
    val label = if (success.myAnswer == null) {
        stringResource(R.string.weekly_chip_to_answer)
    } else {
        pluralStringResource(R.plurals.weekly_chip_answers, success.question.answerCount, success.question.answerCount)
    }
    HomeChip(label = label, onClick = onOpen)
}

@Composable
private fun weeklyQuestionHomeViewModel(container: AppContainer?, currentMember: Member): WeeklyQuestionViewModel = viewModel(
    key = "weeklyQuestionHome",
    factory = viewModelFactory {
        initializer { WeeklyQuestionViewModel(container?.weeklyQuestionRepository, container?.memberRepository, currentMember.id, null) }
    },
)

@Composable
fun WeeklyQuestionHomeContent(state: WeeklyQuestionUiState.Success, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val question = state.question
    ClayCard(modifier = modifier.fillMaxWidth(), onClick = onOpen, onClickLabel = stringResource(R.string.weekly_open)) {
        Text(stringResource(R.string.weekly_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(question.text, style = MaterialTheme.typography.bodyLarge)
        Text(
            pluralStringResource(R.plurals.weekly_answer_count, question.answerCount, question.answerCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.myAnswer == null) {
            Button(onClick = onOpen, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.weekly_answer)) }
        } else {
            OutlinedButton(onClick = onOpen, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.weekly_see_answers)) }
        }
    }
}

@FamiliaPreviews
@Composable
private fun WeeklyQuestionHomeCardPreview() {
    FamiliaTheme { Surface { WeeklyQuestionHomeContent(previewWeeklyQuestion(answered = false), {}) } }
}
