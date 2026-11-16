package fr.familia.app.feature.events.comments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.EventComment
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The comments inside an event (PRD, récit 28), with their own ViewModel. */
@Composable
fun EventCommentsSection(container: AppContainer?, currentMember: Member, eventId: String) {
    val viewModel: EventCommentsViewModel = viewModel(
        key = "event-comments-$eventId",
        factory = viewModelFactory {
            initializer {
                EventCommentsViewModel(
                    container?.eventCommentRepository,
                    container?.memberRepository,
                    currentMember.id,
                    eventId,
                    createSavedStateHandle(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EventCommentsContent(
        state = state,
        onType = viewModel::type,
        onSend = viewModel::send,
        onShowMore = viewModel::showMore,
        onEdit = viewModel::edit,
        onRemove = viewModel::remove,
        onMute = viewModel::setMuted,
    )
}

/** Stateless: every change goes through the callbacks. */
@Composable
fun EventCommentsContent(
    state: EventCommentsUiState,
    onType: (String) -> Unit,
    onSend: () -> Unit,
    onShowMore: () -> Unit,
    onEdit: (EventCommentRow, String) -> Boolean,
    onRemove: (EventCommentRow) -> Unit,
    onMute: (Boolean) -> Unit,
) {
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.event_comments_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (state.hasMore) {
            TextButton(onClick = onShowMore, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.event_comments_more)) }
        }
        if (!state.isLoading && state.rows.isEmpty()) {
            Text(stringResource(R.string.event_comments_empty), style = MaterialTheme.typography.bodyLarge, color = muted)
        }
        state.rows.forEach { row ->
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Text(row.comment.authorName, style = MaterialTheme.typography.titleSmall)
                Text(row.comment.text, style = MaterialTheme.typography.bodyLarge)
                commentWhen(row.comment)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
                if (row.canEdit || row.canRemove) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (row.canEdit) {
                            TextButton(
                                onClick = { editing = row.comment.id; editText = row.comment.text },
                                modifier = Modifier.heightIn(min = 56.dp),
                            ) { Text(stringResource(R.string.event_comment_edit)) }
                        }
                        if (row.canRemove) {
                            TextButton(onClick = { removing = row.comment.id }, modifier = Modifier.heightIn(min = 56.dp)) {
                                Text(stringResource(R.string.recipe_comment_remove))
                            }
                        }
                    }
                }
            }
        }
        state.problem?.let {
            Text(stringResource(problemText(it)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
            value = state.draft,
            onValueChange = onType,
            label = { Text(stringResource(R.string.recipe_comment_field)) },
            supportingText = {
                Column {
                    // Carte RELECTURE (#110) : a surprise is easily spoilt under a birthday.
                    Text(stringResource(R.string.event_comments_public))
                    Text(stringResource(R.string.recipe_comment_count, state.draft.length, EventComment.MAX_TEXT))
                }
            },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = onSend, enabled = state.canSend, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.recipe_comment_send))
        }
        TextButton(onClick = { onMute(!state.muted) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(if (state.muted) R.string.event_comments_unmute else R.string.event_comments_mute))
        }
    }
    state.rows.firstOrNull { it.comment.id == removing }?.let { target ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.recipe_comment_remove_title)) },
            text = { Text(target.comment.text) },
            confirmButton = {
                TextButton(onClick = { removing = null; onRemove(target) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.recipe_remove_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_keep)) }
            },
        )
    }
    state.rows.firstOrNull { it.comment.id == editing }?.let { target ->
        val tooLong = editText.trim().length > EventComment.MAX_TEXT
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.event_comment_edit_title)) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it.take(EventComment.MAX_TEXT) },
                    isError = tooLong,
                    supportingText = { Text(stringResource(R.string.recipe_comment_count, editText.length, EventComment.MAX_TEXT)) },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (onEdit(target, editText)) editing = null },
                    enabled = editText.isNotBlank() && !tooLong,
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.event_comment_edit_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun problemText(problem: EventCommentProblem): Int = when (problem) {
    EventCommentProblem.SendFailed -> R.string.recipe_comment_send_failed
    EventCommentProblem.EditFailed -> R.string.event_comment_edit_failed
    EventCommentProblem.RemoveFailed -> R.string.recipe_comment_remove_failed
    EventCommentProblem.MuteFailed -> R.string.event_comments_mute_failed
}

private val commentTimeFormat = DateTimeFormatter.ofPattern("d MMMM 'à' HH'h'mm", Locale.FRANCE)

/** « 12 novembre à 14h05 », then « · modifié » when its author changed it. Null while being sent. */
@Composable
private fun commentWhen(comment: EventComment): String? {
    val at = comment.createdAt ?: return null
    val day = commentTimeFormat.format(at.atZone(ZoneId.systemDefault()))
    return if (comment.editedAt != null) stringResource(R.string.event_comment_when_edited, day) else day
}

/** Fictional comments for previews and screenshots. */
internal fun previewEventComments(): EventCommentsUiState = EventCommentsUiState(
    rows = listOf(
        EventCommentRow(
            EventComment("c1", "Je prends le gâteau, quelqu'un a une grande nappe ?", "b", "Inès", Instant.parse("2026-11-12T13:05:00Z")),
            canEdit = false,
            canRemove = false,
        ),
        EventCommentRow(
            EventComment("c2", "Moi ! Je l'apporte.", "a", "Camille", Instant.parse("2026-11-12T13:20:00Z"), Instant.parse("2026-11-12T13:21:00Z")),
            canEdit = true,
            canRemove = true,
        ),
    ),
    isLoading = false,
)

@Preview(showBackground = true)
@Composable
private fun EventCommentsPreview() {
    FamiliaTheme {
        EventCommentsContent(previewEventComments(), {}, {}, {}, { _, _ -> true }, {}, {})
    }
}
