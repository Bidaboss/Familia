package fr.familia.app.feature.events.comments

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.mention.MentionText
import fr.familia.app.core.mention.MentionTextField
import fr.familia.app.core.mention.mentionGroups
import fr.familia.app.core.ui.PickGroup
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.EventCommentRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.EventComment
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.form.FormTileHeading
import fr.familia.app.core.ui.frenchDateTime
import java.time.Instant

/** The counter under a comment field is shown from this many characters on (Kévin, 19/11); the limit stays. */
internal const val COMMENT_COUNT_FROM = 450

/** Whether the « … sur 500 caractères » counter shows for a comment of [length] characters. */
internal fun showsCommentCount(length: Int): Boolean = length >= COMMENT_COUNT_FROM

/** The counter of a comment field, or null (no line at all) while the comment is short. */
internal fun commentCount(length: Int, max: Int): (@Composable () -> Unit)? =
    if (showsCommentCount(length)) ({ Text(stringResource(R.string.recipe_comment_count, length, max)) }) else null

/** The comments inside an event (PRD, récit 28), with their own ViewModel. */
@Composable
fun EventCommentsSection(container: AppContainer?, currentMember: Member, eventId: String) {
    CommentsSection(container?.eventCommentRepository, container, currentMember, "event-comments-$eventId", eventId)
}

/**
 * Comments under a document, from [repository]: an event, or a good news (récit 67, with its own
 * [canRemove]). [readOnly] in « Voir comme »: they are read, never written.
 */
@Composable
fun CommentsSection(
    repository: EventCommentRepository?,
    container: AppContainer?,
    currentMember: Member,
    key: String,
    parentId: String,
    readOnly: Boolean = false,
    canRemove: ((Family, EventComment, Member) -> Boolean)? = null,
    @StringRes empty: Int = R.string.event_comments_empty,
    /** False under a word of a step: no function warns anyone of its comments yet, so no setting to show. */
    canMute: Boolean = true,
) {
    val viewModel: EventCommentsViewModel = viewModel(
        key = key,
        factory = viewModelFactory {
            initializer {
                if (canRemove == null) {
                    EventCommentsViewModel(repository, container?.memberRepository, currentMember.id, parentId, createSavedStateHandle())
                } else {
                    EventCommentsViewModel(
                        repository, container?.memberRepository, currentMember.id, parentId, createSavedStateHandle(), canRemove = canRemove,
                    )
                }
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EventCommentsContent(
        state = state,
        groups = mentionGroups(currentMember),
        readOnly = readOnly,
        empty = empty,
        canMute = canMute,
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
    onType: (String, List<String>) -> Unit,
    onSend: () -> Unit,
    onShowMore: () -> Unit,
    onEdit: (EventCommentRow, String, List<String>) -> Boolean,
    onRemove: (EventCommentRow) -> Unit,
    onMute: (Boolean) -> Unit,
    /** Whom « @ » suggests (Kévin, 19/11); empty, « @ » suggests nobody. */
    groups: List<PickGroup> = emptyList(),
    readOnly: Boolean = false,
    @StringRes empty: Int = R.string.event_comments_empty,
    canMute: Boolean = true,
) {
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
    var editMentions by rememberSaveable { mutableStateOf(listOf<String>()) }
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FormTileHeading(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.event_comments_title))
        if (state.hasMore) {
            TextButton(onClick = onShowMore, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.event_comments_more)) }
        }
        if (!state.isLoading && state.rows.isEmpty()) {
            Text(stringResource(empty), style = MaterialTheme.typography.bodyLarge, color = muted)
        }
        state.rows.forEach { row ->
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Text(row.comment.authorName, style = MaterialTheme.typography.titleSmall)
                MentionText(row.comment.text, row.comment.mentionIds, style = MaterialTheme.typography.bodyLarge)
                commentWhen(row.comment)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
                if (!readOnly && (row.canEdit || row.canRemove)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (row.canEdit) {
                            TextButton(
                                onClick = { editing = row.comment.id; editText = row.comment.text; editMentions = row.comment.mentionIds },
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
        if (readOnly) return@Column
        MentionTextField(
            text = state.draft,
            mentionIds = state.draftMentionIds,
            onChange = onType,
            groups = groups,
            label = { Text(stringResource(R.string.recipe_comment_field)) },
            // Kévin, 19/11: nothing under the field until the end is near.
            supportingText = commentCount(state.draft.length, EventComment.MAX_TEXT),
            minLines = 2,
            maxLength = EventComment.MAX_TEXT,
        )
        OutlinedButton(onClick = onSend, enabled = state.canSend, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.recipe_comment_send))
        }
        if (canMute) {
            TextButton(onClick = { onMute(!state.muted) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(if (state.muted) R.string.event_comments_unmute else R.string.event_comments_mute))
            }
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
                MentionTextField(
                    text = editText,
                    mentionIds = editMentions,
                    onChange = { text, ids -> editText = text; editMentions = ids },
                    groups = groups,
                    label = { Text(stringResource(R.string.recipe_comment_field)) },
                    isError = tooLong,
                    supportingText = commentCount(editText.length, EventComment.MAX_TEXT),
                    minLines = 2,
                    maxLength = EventComment.MAX_TEXT,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (onEdit(target, editText, editMentions)) editing = null },
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

/** « 12 novembre à 14 h 05 », in Paris, then « · modifié » when its author changed it. Null while being sent. */
@Composable
private fun commentWhen(comment: EventComment): String? {
    val at = comment.createdAt ?: return null
    val day = frenchDateTime(at)
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
        EventCommentsContent(previewEventComments(), { _, _ -> }, {}, {}, { _, _, _ -> true }, {}, {})
    }
}
