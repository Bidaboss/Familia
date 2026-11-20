package fr.familia.app.feature.weeklyquestion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import fr.familia.app.core.ui.FamiliaTopBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.WeeklyQuestion
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.ShareGoodNewsButton
import fr.familia.app.core.ui.LoadingState

/** « Nos réponses »: every question already asked, newest first (récit 37). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyArchiveScreen(state: WeeklyArchiveUiState, onOpen: (String) -> Unit, onBack: () -> Unit, onShareGoodNews: (() -> Unit)? = null) {
    Scaffold(
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.weekly_archive_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                WeeklyArchiveUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                WeeklyArchiveUiState.Empty -> EmptyState(
                    icon = Icons.Outlined.QuestionAnswer,
                    title = stringResource(R.string.weekly_none_title),
                    body = stringResource(R.string.weekly_none_body),
                    action = { if (onShareGoodNews != null) ShareGoodNewsButton(onShareGoodNews) },
                )
                WeeklyArchiveUiState.Error -> EmptyState(
                    icon = Icons.Outlined.QuestionAnswer,
                    title = stringResource(R.string.weekly_archive_error_title),
                    body = stringResource(R.string.weekly_archive_error_body),
                )
                is WeeklyArchiveUiState.Ready -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.questions, key = { it.id }) { question -> ArchiveRow(question, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun ArchiveRow(question: WeeklyQuestion, onOpen: (String) -> Unit) {
    ClayCard(onClick = { onOpen(question.id) }, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.weekly_week_of, weekLabel(question.publishedAt)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(question.text, style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(R.plurals.weekly_answer_count, question.answerCount, question.answerCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@FamiliaPreviews
@Composable
private fun WeeklyArchivePreview() {
    val latest = previewWeeklyQuestion().question
    FamiliaTheme {
        Surface {
            WeeklyArchiveScreen(
                state = WeeklyArchiveUiState.Ready(
                    listOf(latest, latest.copy(id = "2026-11-23", text = "Ton plat préféré quand tu étais petit ?", answerCount = 9)),
                ),
                onOpen = {},
                onBack = {},
            )
        }
    }
}
