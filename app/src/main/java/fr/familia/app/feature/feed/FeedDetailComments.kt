package fr.familia.app.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.mention.MentionText
import fr.familia.app.core.mention.MentionTextField
import fr.familia.app.core.mention.mentionGroups
import fr.familia.app.core.model.EventComment
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.frenchDateTime
import fr.familia.app.feature.events.comments.EventCommentRow
import fr.familia.app.feature.events.comments.EventCommentsUiState
import fr.familia.app.feature.events.comments.EventCommentsViewModel
import java.time.Duration
import java.time.Instant

/*
 * Les commentaires du détail sur noir (fiche D1, retour du 26/11) : « COMMENTAIRES (2) » en blanc,
 * chaque commentaire avec son avatar, le prénom en blanc gras, « Il y a 2 h » en crème et le texte en
 * blanc ; puis, fixé en bas, « 🔔 … seront prévenus de ton commentaire. », un champ arrondi et un rond
 * d'envoi. Le même modèle que les commentaires d'un événement (EventCommentsViewModel).
 */

/** The very dark grey of the field and of the J'aime of the detail. */
private val DetailField = Color(0xFF2A2622)

/** The comments of the thread of [social], read and written as the gazette writes them. */
@Composable
internal fun rememberDetailComments(env: FeedSocialEnv, social: FeedSocial): EventCommentsViewModel = viewModel(
    key = "gazette-comments-${social.threadId}",
    factory = viewModelFactory {
        initializer {
            EventCommentsViewModel(
                env.comments, env.container?.memberRepository, env.me.id, social.threadId, createSavedStateHandle(),
                canRemove = ::canRemoveGazetteComment,
            )
        }
    },
)

/** « Il y a 2 h », « Il y a 5 min », « À l'instant », then the date once a day has gone. */
internal fun commentAgo(at: Instant, now: Instant, justNow: String, minutes: (Long) -> String, hours: (Long) -> String, date: (Instant) -> String): String {
    val gone = Duration.between(at, now)
    return when {
        gone.toMinutes() < 1 -> justNow
        gone.toMinutes() < 60 -> minutes(gone.toMinutes())
        gone.toHours() < 24 -> hours(gone.toHours())
        else -> date(at)
    }
}

/** The list of the comments on black, with « Modifier » and « Supprimer » where the rules allow. */
@Composable
internal fun DetailCommentList(state: EventCommentsUiState, viewModel: EventCommentsViewModel, context: FeedContext, readOnly: Boolean) {
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
    var editMentions by rememberSaveable { mutableStateOf(listOf<String>()) }
    val justNow = stringResource(R.string.feed_comment_just_now)
    val minutesAgo = stringResource(R.string.feed_comment_minutes_ago)
    val hoursAgo = stringResource(R.string.feed_comment_hours_ago)
    val now = Instant.now()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.feed_detail_comments_title, state.rows.size).uppercase(),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        if (state.hasMore) {
            TextButton(onClick = viewModel::showMore, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.event_comments_more), color = DetailCream)
            }
        }
        if (!state.isLoading && state.rows.isEmpty()) {
            Text(stringResource(R.string.feed_detail_no_comment), color = DetailCream, fontSize = 15.sp)
        }
        state.rows.forEach { row -> DetailComment(row, context, readOnly, now, justNow, minutesAgo, hoursAgo, onEdit = {
            editing = row.comment.id
            editText = row.comment.text
            editMentions = row.comment.mentionIds
        }, onRemove = { removing = row.comment.id }) }
    }
    state.rows.firstOrNull { it.comment.id == removing }?.let { target ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.recipe_comment_remove_title)) },
            text = { Text(target.comment.text) },
            confirmButton = {
                TextButton(onClick = { removing = null; viewModel.remove(target) }, modifier = Modifier.heightIn(min = 56.dp)) {
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
                    groups = mentionGroups(context.me),
                    label = { Text(stringResource(R.string.recipe_comment_field)) },
                    isError = tooLong,
                    minLines = 2,
                    maxLength = EventComment.MAX_TEXT,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (viewModel.edit(target, editText, editMentions)) editing = null },
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

@Composable
private fun DetailComment(
    row: EventCommentRow,
    context: FeedContext,
    readOnly: Boolean,
    now: Instant,
    justNow: String,
    minutesAgo: String,
    hoursAgo: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val comment = row.comment
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val author = context.member(comment.authorId)
        if (author != null) MemberAvatar(author, size = 32.dp) else Box(Modifier.size(32.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val ago = comment.createdAt?.let { at ->
                commentAgo(at, now, justNow, { minutesAgo.format(it) }, { hoursAgo.format(it) }, { frenchDateTime(it) })
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)) { append(comment.authorName) }
                    if (ago != null) withStyle(SpanStyle(color = DetailCream, fontSize = 13.sp)) { append("  $ago") }
                },
            )
            MentionText(comment.text, comment.mentionIds, style = MaterialTheme.typography.bodyLarge.copy(color = Color.White))
            if (!readOnly && (row.canEdit || row.canRemove)) {
                Row {
                    if (row.canEdit) {
                        TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.event_comment_edit), color = DetailCream)
                        }
                    }
                    if (row.canRemove) {
                        TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.recipe_comment_remove), color = DetailCream)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fixed at the bottom of the detail: who will be told (the author of the card and those who already
 * commented, as the server tells them), then the rounded field and the round of « Envoyer ».
 */
@Composable
internal fun DetailCommentField(state: EventCommentsUiState, viewModel: EventCommentsViewModel, context: FeedContext, cardAuthorId: String?) {
    val told = (listOfNotNull(cardAuthorId) + state.rows.map { it.comment.authorId })
        .distinct()
        .filter { it != context.me.id }
        .mapNotNull { context.member(it)?.firstName }
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF15110E)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (told.isNotEmpty()) {
            Text(
                "🔔 " + stringResource(if (told.size == 1) R.string.feed_detail_told_one else R.string.feed_detail_told_many, told.joinToString(", ")),
                color = DetailCream,
                fontSize = 14.sp,
            )
        }
        state.problem?.let {
            Text(stringResource(R.string.recipe_comment_send_failed), color = Color(0xFFFFB4A9), fontSize = 14.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                MentionTextField(
                    text = state.draft,
                    mentionIds = state.draftMentionIds,
                    onChange = viewModel::type,
                    groups = mentionGroups(context.me),
                    label = null,
                    placeholder = { Text(stringResource(R.string.feed_social_field), color = DetailCream) },
                    maxLength = EventComment.MAX_TEXT,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DetailField,
                        unfocusedContainerColor = DetailField,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = DetailCream,
                        unfocusedBorderColor = Color(0xFF4A433C),
                        cursorColor = Color.White,
                    ),
                )
            }
            IconButton(
                onClick = viewModel::send,
                enabled = state.canSend,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF2B1D12),
                    disabledContainerColor = DetailField,
                    disabledContentColor = Color(0xFF8A8078),
                ),
                modifier = Modifier.size(56.dp).background(Color.Transparent, CircleShape),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.feed_social_send))
            }
        }
    }
}
