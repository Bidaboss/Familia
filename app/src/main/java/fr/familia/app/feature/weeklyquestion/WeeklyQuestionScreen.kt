package fr.familia.app.feature.weeklyquestion

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.familia.app.R
import fr.familia.app.core.photo.keepPhotoAccess
import fr.familia.app.core.model.WEEKLY_ANSWER_MAX
import fr.familia.app.core.model.WeeklyAnswer
import fr.familia.app.core.model.WeeklyQuestion
import fr.familia.app.core.model.weeklyPhotoPath
import fr.familia.app.core.model.weeklyThumbnailPath
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.ShareGoodNewsButton
import fr.familia.app.core.ui.formatTime
import fr.familia.app.core.ui.frenchDate
import java.time.Instant
import java.time.ZoneId

/** The question of the week, my answer and, once allowed, everyone's (récit 37). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyQuestionScreen(
    state: WeeklyQuestionUiState,
    onAnswer: (text: String, newPhoto: String?, keepPhoto: Boolean) -> Unit,
    onSkip: () -> Unit,
    onRemove: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    onArchive: () -> Unit = {},
    /** No question yet: the fallback « Partager une bonne nouvelle en attendant »; null while Bonnes nouvelles is off. */
    onShareGoodNews: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.weekly_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    TextButton(onClick = onArchive, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.weekly_archive_title))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                WeeklyQuestionUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                WeeklyQuestionUiState.None -> EmptyState(
                    icon = Icons.Outlined.QuestionAnswer,
                    title = stringResource(R.string.weekly_none_title),
                    body = stringResource(R.string.weekly_none_body),
                    action = { if (onShareGoodNews != null) ShareGoodNewsButton(onShareGoodNews) },
                )
                is WeeklyQuestionUiState.Success -> Content(state, onAnswer, onSkip, onRemove)
            }
        }
    }
    if ((state as? WeeklyQuestionUiState.Success)?.failed == true) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(R.string.weekly_failed_title)) },
            text = { Text(stringResource(R.string.weekly_failed_body)) },
            confirmButton = {
                TextButton(onClick = onDismissError, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@Composable
private fun Content(
    state: WeeklyQuestionUiState.Success,
    onAnswer: (String, String?, Boolean) -> Unit,
    onSkip: () -> Unit,
    onRemove: () -> Unit,
) {
    val question = state.question
    // Reopened with the current answer when « Modifier » is touched.
    var editing by rememberSaveable(question.id) { mutableStateOf(false) }
    var draft by rememberSaveable(question.id) { mutableStateOf("") }
    // The photo chosen on the phone, not sent yet; keepPhoto says whether the one sent stays.
    var newPhoto by rememberSaveable(question.id) { mutableStateOf<String?>(null) }
    var keepPhoto by rememberSaveable(question.id) { mutableStateOf(true) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.weekly_week_of, weekLabel(question.publishedAt)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(question.text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        question.proposedByName?.let {
            Text(stringResource(R.string.weekly_proposed_by, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val mine = state.myAnswer
        if (mine == null || editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(WEEKLY_ANSWER_MAX) },
                label = { Text(stringResource(R.string.weekly_answer_label)) },
                minLines = 3,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            PhotoPicker(
                questionId = question.id,
                memberId = state.myAnswer?.memberId,
                sentId = mine?.photoId?.takeIf { keepPhoto },
                newPhoto = newPhoto,
                enabled = !state.isBusy,
                onPick = { newPhoto = it },
                onRemove = { newPhoto = null; keepPhoto = false },
            )
            Button(
                onClick = { onAnswer(draft, newPhoto, keepPhoto); editing = false; newPhoto = null; keepPhoto = true },
                enabled = !state.isBusy && draft.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.weekly_answer)) }
            if (mine == null) {
                OutlinedButton(onClick = onSkip, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.weekly_skip))
                }
            } else {
                TextButton(onClick = { editing = false; newPhoto = null; keepPhoto = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        } else {
            ClayCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.weekly_my_answer), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    if (mine.skipped) stringResource(R.string.weekly_i_skipped) else mine.text.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                mine.photoId?.let { AnswerPhoto(question.id, mine.memberId, it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { draft = mine.text.orEmpty(); editing = true },
                        enabled = !state.isBusy,
                        modifier = Modifier.heightIn(min = 56.dp),
                    ) { Text(stringResource(if (mine.skipped) R.string.weekly_answer_after_all else R.string.weekly_edit)) }
                    TextButton(onClick = onRemove, enabled = !state.isBusy, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.weekly_remove))
                    }
                }
            }
        }
        if (state.isBusy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))

        Text(
            pluralStringResource(R.plurals.weekly_answer_count, question.answerCount, question.answerCount),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        when {
            !state.canSeeAnswers -> Text(
                stringResource(R.string.weekly_hidden_until, revealLabel(question.revealAt)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.answers.isEmpty() -> Text(
                stringResource(R.string.weekly_no_other_answer),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> state.answers.forEach { AnswerCard(question.id, it) }
        }
    }
}

@Composable
private fun AnswerCard(questionId: String, answer: WeeklyAnswer) {
    ClayCard(Modifier.fillMaxWidth()) {
        Text(answer.memberName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(answer.text.orEmpty(), style = MaterialTheme.typography.bodyLarge)
        answer.photoId?.let { AnswerPhoto(questionId, answer.memberId, it) }
    }
}

/** The photo of an answer, in full width (the large file: it is read only once shown). */
@Composable
private fun AnswerPhoto(questionId: String, memberId: String, photoId: String) {
    StorageImage(
        path = weeklyPhotoPath(questionId, memberId, photoId),
        contentDescription = stringResource(R.string.weekly_photo_description),
        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.medium),
    )
}

/**
 * One optional photo: the one already sent ([sentId], by [memberId]), or the one just chosen ([newPhoto]), with
 * « Retirer la photo »; otherwise « Ajouter une photo », which opens Android's photo picker.
 */
@Composable
private fun PhotoPicker(
    questionId: String,
    memberId: String?,
    sentId: String?,
    newPhoto: String?,
    enabled: Boolean,
    onPick: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { keepPhotoAccess(context, listOf(it)) }
        uri?.let { onPick(it.toString()) }
    }
    val description = stringResource(R.string.weekly_photo_description)
    val thumb = Modifier.size(96.dp).clip(MaterialTheme.shapes.medium)
    if (newPhoto != null || (sentId != null && memberId != null)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (newPhoto != null) {
                AsyncImage(model = newPhoto, contentDescription = description, modifier = thumb, contentScale = ContentScale.Crop)
            } else if (sentId != null && memberId != null) {
                StorageImage(weeklyThumbnailPath(questionId, memberId, sentId), description, thumb)
            }
            TextButton(onClick = onRemove, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.weekly_photo_remove))
            }
        }
    } else {
        OutlinedButton(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
            Text(stringResource(R.string.weekly_photo_add), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private val paris: ZoneId = ZoneId.of("Europe/Paris")

/** « lundi 30 novembre ». */
internal fun weekLabel(publishedAt: Instant): String = frenchDate(publishedAt.atZone(paris).toLocalDate(), "EEEE d MMMM")

/** « mercredi 2 décembre à 9 h 00 ». */
internal fun revealLabel(revealAt: Instant): String {
    val at = revealAt.atZone(paris)
    return "${frenchDate(at.toLocalDate(), "EEEE d MMMM")} à ${formatTime(at.toLocalTime())}"
}

/** Fictional data for previews and screenshots. */
internal fun previewWeeklyQuestion(answered: Boolean = true): WeeklyQuestionUiState.Success {
    val published = Instant.parse("2026-11-30T08:00:00Z")
    val question = WeeklyQuestion(
        "2026-11-30", "Ton meilleur souvenir avec quelqu'un de plus âgé de la famille ?", null,
        published, published.plusSeconds(48 * 3600), 3,
    )
    val others = listOf(
        WeeklyAnswer("ines", "Inès", "Les crêpes du dimanche chez Mamie.", false, published),
        WeeklyAnswer("noe", "Noé", "La pêche avec Papi, quand j'ai attrapé un gros poisson !", false, published),
    )
    return WeeklyQuestionUiState.Success(
        question = question,
        myAnswer = if (answered) WeeklyAnswer("jules", "Jules", "Le jardin de mon grand-père.", false, published) else null,
        answers = if (answered) others else emptyList(),
        canSeeAnswers = answered,
        now = published.plusSeconds(3600),
    )
}

@FamiliaPreviews
@Composable
private fun WeeklyQuestionScreenPreview() {
    FamiliaTheme { Surface { WeeklyQuestionScreen(previewWeeklyQuestion(), { _, _, _ -> }, {}, {}, {}, {}) } }
}
