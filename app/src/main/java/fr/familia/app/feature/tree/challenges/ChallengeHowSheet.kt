package fr.familia.app.feature.tree.challenges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.ChallengeMeasure
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.frenchDate

/** « Comment ça marche » (PRD récit 65), in a sheet from the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeHowSheet(sheet: ChallengeSheet, onDismiss: () -> Unit, onJoin: () -> Unit, onShareGoodNews: (() -> Unit)?) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ChallengeHowContent(
            sheet,
            onJoin = { onDismiss(); onJoin() },
            onShareGoodNews = onShareGoodNews?.let { share -> { onDismiss(); share() } },
        )
    }
}

/** What the sheet says; it scrolls, so that it holds at a font of 200 %. */
@Composable
fun ChallengeHowContent(sheet: ChallengeSheet, onJoin: () -> Unit, onShareGoodNews: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = ClaySpacing.screen, end = ClaySpacing.screen, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (sheet) {
            is ChallengeSheet.Challenge -> ChallengeHow(sheet.explanation, onJoin, onShareGoodNews)
            is ChallengeSheet.ChallengePrinciple -> {
                SheetTitle(stringResource(R.string.challenge_how_title))
                Text(stringResource(R.string.challenge_how_principle), style = MaterialTheme.typography.bodyLarge)
                sheet.startsOn?.let { Start(stringResource(R.string.challenge_how_principle_start, frenchDate(it, "d MMMM"))) }
            }
            is ChallengeSheet.QuestionPrinciple -> {
                SheetTitle(stringResource(R.string.question_how_title))
                Text(stringResource(R.string.question_how_principle), style = MaterialTheme.typography.bodyLarge)
                sheet.startsOn?.let { Start(stringResource(R.string.question_how_principle_start, frenchDate(it, "d MMMM"))) }
            }
        }
    }
}

@Composable
private fun ChallengeHow(e: ChallengeExplanation, onJoin: () -> Unit, onShareGoodNews: (() -> Unit)?) {
    Text(stringResource(R.string.challenge_how_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    SheetTitle("${e.emoji} ${e.title}")
    val counts = when (e.measure) {
        ChallengeMeasure.Photos -> R.string.challenge_how_counts_photos
        ChallengeMeasure.GoodNews -> R.string.challenge_how_counts_good_news
        ChallengeMeasure.Linked -> R.string.challenge_how_counts_linked
    }
    Text(stringResource(counts), style = MaterialTheme.typography.bodyLarge)
    e.hostNote?.let { Text(stringResource(R.string.challenge_how_host_note, it), style = MaterialTheme.typography.bodyLarge) }
    val progress = stringResource(R.string.tree_challenge_progress, e.progress, e.target)
    Text(stringResource(R.string.challenge_how_goal, progress), style = MaterialTheme.typography.titleMedium)
    LinearProgressIndicator(
        progress = { if (e.target <= 0) 0f else e.progress.toFloat() / e.target },
        drawStopIndicator = {},
        gapSize = 0.dp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).semantics { contentDescription = progress },
    )
    e.endsOn?.let { Text(stringResource(R.string.challenge_how_until, frenchDate(it, "d MMMM")), style = MaterialTheme.typography.bodyLarge) }
    Text(stringResource(R.string.challenge_how_win), style = MaterialTheme.typography.bodyLarge)
    when (e.action) {
        ChallengeAction.AddPhoto -> Action(stringResource(R.string.challenges_add_photo), onJoin)
        ChallengeAction.ShareGoodNews -> onShareGoodNews?.let { Action(stringResource(R.string.challenge_how_share), it) }
        ChallengeAction.Done -> Text("🌸 " + stringResource(R.string.challenge_how_done), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ChallengeAction.None -> Unit
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Start(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun Action(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(text) }
}
