package fr.familia.app.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.R
import fr.familia.app.core.data.EventCommentRepository
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.model.EventComment
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Reaction
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.goodnews.nextReaction
import fr.familia.app.feature.goodnews.reactionName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * « Bonne nouvelle » (récit 67, maquette F2 bis): the good news of the window, read like their
 * own screen reads them (every member reads every one), without those I hid for myself. The
 * « Joyeux anniversaire » of Familia stays out: the card of the birthday says it already.
 * [comments] is the repository of their comments.
 */
class GoodNewsFeedSource(
    private val goodNews: GoodNewsRepository,
    private val comments: EventCommentRepository?,
    private val meId: String,
) : FeedSource {
    override fun cards(window: FeedWindow): Flow<List<FeedCard>> =
        goodNews.observeLatest(window.limit).map { list ->
            goodNewsForFeed(list, meId, window.since).map { GoodNewsFeedCard(it, goodNews, comments) }
        }
}

/** The good news of the fil: since [since], not hidden by [meId], not a birthday of Familia. */
fun goodNewsForFeed(list: List<GoodNews>, meId: String, since: Instant): List<GoodNews> = list.filter { news ->
    val at = news.createdAt
    at != null && !at.isBefore(since) && !news.isHiddenFor(meId) && news.birthdayOf == null
}

/** The reactions a compact card shows: those given, in their order; a heart to start with. */
fun feedReactions(news: GoodNews): List<Pair<Reaction, Int>> {
    val given = Reaction.entries.map { r -> r to news.reactions.values.count { it == r } }.filter { it.second > 0 }
    return given.ifEmpty { listOf(Reaction.Heart to 0) }
}

class GoodNewsFeedCard(
    val news: GoodNews,
    private val goodNews: GoodNewsRepository?,
    private val comments: EventCommentRepository?,
) : FeedCard {
    override val key: String = "goodNews:${news.id}"
    override val at: Instant = news.createdAt ?: Instant.EPOCH
    override val authorId: String = news.authorId

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val tone = FeedTone.Green
        val open = { navigation.onOpenGoodNews(news.id) }
        val last = remember(news.id, comments, news.commentCount > 0) {
            // No comment yet: no listener, no read.
            if (comments == null || news.commentCount == 0) {
                flowOf(null)
            } else {
                comments.observe(news.id, 1).map { it.comments.lastOrNull() }.catch { emit(null) }
            }
        }
        val lastComment by last.collectAsStateWithLifecycle(initialValue = null)
        val author = context.member(news.authorId)
        FeedCardShell(
            tone = tone,
            onOpen = open,
            header = {
                FeedHeader(
                    member = author,
                    name = author?.firstName ?: news.authorName.ifBlank { stringResource(R.string.feed_label_good_news) },
                    time = formatTime(at.atZone(FAMILY_ZONE).toLocalTime()),
                    label = stringResource(R.string.feed_label_good_news),
                    tone = tone,
                )
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                news.photoIds.firstOrNull()?.let { photo ->
                    StorageImage(
                        news.thumbnailPath(photo),
                        stringResource(R.string.feed_good_news_photo),
                        Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)),
                    )
                }
                Column(Modifier.weight(1f)) {
                    var cut by remember(news.id) { mutableStateOf(false) }
                    Text(
                        news.text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { cut = it.hasVisualOverflow },
                    )
                    if (cut) {
                        Text(
                            stringResource(R.string.feed_read_more),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tone.label,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                }
            }
            lastComment?.let { FeedLastComment(it.authorName, it.text) }
            GoodNewsFeedActions(news, context, goodNews, comments, onOpen = open)
        }
    }
}

/** « ❤️ 7  👏 4   💬 5  Commenter », then the field once « Commenter » is touched. */
@Composable
private fun GoodNewsFeedActions(
    news: GoodNews,
    context: FeedContext,
    goodNews: GoodNewsRepository?,
    comments: EventCommentRepository?,
    onOpen: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Shown at once, before Firestore answers: the touch is felt.
    var mine by remember(news.id, news.reactions[context.me.id]) { mutableStateOf(news.reactions[context.me.id]) }
    var writing by rememberSaveable(news.id) { mutableStateOf(false) }
    var draft by rememberSaveable(news.id) { mutableStateOf("") }
    var failed by remember(news.id) { mutableStateOf(false) }
    // On a weak network the send waits up to 4 s: one tap, one comment.
    var sending by remember(news.id) { mutableStateOf(false) }
    val canWrite = !context.readOnly
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val meId = context.me.id
        val live = mine?.let { news.reactions + (meId to it) } ?: (news.reactions - meId)
        feedReactions(news.copy(reactions = live)).forEach { (reaction, shown) ->
            val name = stringResource(reactionName(reaction))
            FilterChip(
                selected = mine == reaction,
                enabled = canWrite && goodNews != null,
                onClick = {
                    val next = nextReaction(mine, reaction)
                    val before = mine
                    mine = next
                    scope.launch {
                        try {
                            goodNews?.react(news.id, context.me.id, next)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            mine = before
                        }
                    }
                },
                label = { Text(if (shown > 0) "${reaction.emoji} $shown" else reaction.emoji) },
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = name
                },
            )
        }
        Spacer(Modifier.weight(1f))
        if (news.commentCount > 0) {
            val description = pluralStringResource(R.plurals.feed_comment_count, news.commentCount, news.commentCount)
            TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description }) {
                Text("💬 ${news.commentCount}")
            }
        }
        if (canWrite && comments != null && !writing) {
            TextButton(onClick = { writing = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.feed_comment))
            }
        }
    }
    if (canWrite && comments != null && writing) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(EventComment.MAX_TEXT); failed = false },
            label = { Text(stringResource(R.string.feed_comment_field)) },
            isError = failed,
            supportingText = if (failed) ({ Text(stringResource(R.string.recipe_comment_send_failed)) }) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { writing = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                enabled = draft.isNotBlank() && !sending,
                onClick = {
                    val text = draft.trim()
                    sending = true
                    scope.launch {
                        try {
                            // Offline, Firestore keeps it and sends it later.
                            withTimeoutOrNull(4_000) { comments.add(news.id, text, context.me) }
                            draft = ""
                            writing = false
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            sending = false
                        }
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.recipe_comment_send)) }
        }
    }
}
