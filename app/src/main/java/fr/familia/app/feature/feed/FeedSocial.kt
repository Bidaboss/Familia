package fr.familia.app.feature.feed

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.EventCommentRepository
import fr.familia.app.core.data.GazetteReactions
import fr.familia.app.core.data.GazetteThread
import fr.familia.app.core.data.GazetteThreadRepository
import fr.familia.app.core.data.RecipeRepository
import fr.familia.app.core.model.Member
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/*
 * La gazette, mini réseau social (récit 115, brief §4): under every card, « ♡ J'aime » and « 💬
 * Commenter » with their counters, the 2 latest comments, and the field « Écrire un commentaire… »
 * that opens in the card itself. A touch on the counter of comments, or on the card, opens it.
 * The J'aime live on the thread of the card (gazetteThreads); the comments too, except for the
 * cards that already had theirs (an event, a recipe): one conversation per thing, never two.
 */

/** A comment as the card shows it. */
data class FeedCommentLine(val authorName: String, val text: String)

/** What the card shows of its comments: how many in all, and the 2 latest, oldest first. */
data class FeedCommentsState(val count: Int, val last: List<FeedCommentLine>)

/** The comments a card already had before the gazette (an event, a recipe). */
interface FeedOwnComments {
    fun observe(): Flow<FeedCommentsState>
    suspend fun add(text: String, me: Member)
}

/** The comments of an event (récit 28): the 2 latest live, the count read once per change. */
class EventOwnComments(private val repository: EventCommentRepository, private val eventId: String) : FeedOwnComments {
    override fun observe(): Flow<FeedCommentsState> = repository.observe(eventId, FEED_COMMENTS_SHOWN).map { page ->
        val count = try {
            repository.count(eventId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        FeedCommentsState(count ?: page.comments.size, page.comments.map { FeedCommentLine(it.authorName, it.text) })
    }

    override suspend fun add(text: String, me: Member) = repository.add(eventId, text, me)
}

/** The comments of a recipe (récit 66), as the card already read them. */
class RecipeOwnComments(private val repository: RecipeRepository, private val recipeId: String) : FeedOwnComments {
    override fun observe(): Flow<FeedCommentsState> = repository.observeComments(recipeId).map { all ->
        FeedCommentsState(all.size, all.takeLast(FEED_COMMENTS_SHOWN).map { FeedCommentLine(it.authorName, it.text) })
    }

    override suspend fun add(text: String, me: Member) = repository.addComment(recipeId, text, me)
}

/**
 * How a card is liked and commented: its thread ([threadId], see gazetteThreadId), and where its
 * comments are: its [own] ones, the thread's when [commentable], or none (an event whose comments
 * are off in Administration: J'aime only).
 */
class FeedSocial(
    val threadId: String,
    val own: FeedOwnComments? = null,
    val commentable: Boolean = true,
    /** The title of its detail (brief §4, D1 à D9), opened by the counter of comments. */
    @StringRes val title: Int = R.string.feed_detail_title,
    /** Its detail lies on black (photos and films, brief §3): « un seul fond noir ». */
    val dark: Boolean = false,
)

/** What the bar needs from the app, provided by the gazette around its cards. */
class FeedSocialEnv(
    val threads: GazetteThreadRepository,
    val comments: EventCommentRepository?,
    val me: Member,
    /** « Voir comme »: the counters show, nothing is written. */
    val readOnly: Boolean,
    /** For the comments of a detail (their ViewModel reads the family); null in the previews. */
    val container: AppContainer? = null,
)

/** Null outside the gazette (or without Firebase): the cards draw no bar. */
val LocalFeedSocialEnv = staticCompositionLocalOf<FeedSocialEnv?> { null }

/** The social part of the card being drawn, set by the gazette around each card. */
val LocalFeedCardSocial = staticCompositionLocalOf<FeedSocial?> { null }

/** Opens the detail of the card being drawn, with all its comments; null: the card has no detail of its own. */
val LocalFeedCardDetail = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Two comments under a card (brief §4: « les 2 derniers commentaires »). */
const val FEED_COMMENTS_SHOWN = 2

/** A comment of the gazette: 1 to 500 characters (règles Firestore, isCommentText). */
const val FEED_COMMENT_MAX = 500

/** The J'aime of [thread], the most given first: what the bar shows beside the count. */
fun reactionSummary(thread: GazetteThread): List<String> =
    thread.reactions.values.groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { GazetteReactions.indexOf(it.key) })
        .map { it.key }

/** The bar under a card: J'aime, Commenter, the 2 latest comments, and the field once opened. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun FeedSocialBar(
    social: FeedSocial,
    env: FeedSocialEnv,
    onOpen: (() -> Unit)?,
    /** The detail of the card (its thread's comments); the cards with comments of their own open their page. */
    onDetail: (() -> Unit)? = null,
    /** In the detail itself: the J'aime only, the comments are under it in full. */
    inDetail: Boolean = false,
) {
    // Where the counter of comments leads (brief §4): the detail with all of them, or the page that has them.
    val openComments = if (social.own == null) onDetail ?: onOpen else onOpen
    val threadFlow = remember(social.threadId, env.threads) { env.threads.observe(social.threadId).catch { emit(GazetteThread()) } }
    val thread by threadFlow.collectAsStateWithLifecycle(initialValue = GazetteThread())
    val ownFlow: Flow<FeedCommentsState?> = remember(social) { social.own?.observe()?.catch { emit(FeedCommentsState(0, emptyList())) } ?: flowOf(null) }
    val own by ownFlow.collectAsStateWithLifecycle(initialValue = null)
    val comments = if (social.own != null) {
        own ?: FeedCommentsState(0, emptyList())
    } else {
        FeedCommentsState(thread.commentCount, thread.last.map { FeedCommentLine(it.authorName, it.text) })
    }
    val canComment = !env.readOnly && (social.own != null || (social.commentable && env.comments != null))
    val mine = thread.reactionOf(env.me.id)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val failed = stringResource(R.string.feed_social_failed)
    var choosing by remember { mutableStateOf(false) }
    var whoLiked by remember { mutableStateOf(false) }
    var writing by rememberSaveable(social.threadId) { mutableStateOf(false) }
    fun react(reaction: String?) {
        scope.launch {
            try {
                env.threads.react(social.threadId, env.me.id, reaction)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(context, failed, Toast.LENGTH_LONG).show()
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // J'aime, Commenter and « Qui a aimé ? » on one line, the last one going under when it is narrow.
        FlowRow(verticalArrangement = Arrangement.Center) {
            val likeLabel = stringResource(R.string.feed_social_like)
            val count = thread.reactions.size
            val spoken = if (count > 0) pluralStringResource(R.plurals.feed_social_likes, count, count) else likeLabel
            val chooseLabel = stringResource(R.string.feed_social_choose)
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .widthIn(min = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (env.readOnly) {
                                Modifier
                            } else {
                                // A touch gives (or takes back) 💛; a long press chooses among the four (119 f).
                                Modifier.combinedClickable(
                                    onClick = { react(if (mine == null) GazetteReactions.first() else null) },
                                    onLongClick = { choosing = true },
                                )
                            },
                        )
                        .padding(horizontal = 8.dp)
                        .clearAndSetSemantics {
                            contentDescription = spoken
                            if (!env.readOnly) {
                                role = Role.Button
                                stateDescription = if (mine != null) mine else ""
                                customActions = listOf(CustomAccessibilityAction(chooseLabel) { choosing = true; true })
                            }
                        },
                ) {
                    // « ♡ 8 J'aime » (prototype): 18 sp, bold, dark brown, the count from 1.
                    Text(mine ?: "♡", style = BarStyle, color = BarInk)
                    Spacer(Modifier.width(8.dp))
                    if (count > 0) {
                        Text("$count", style = BarStyle, color = BarInk)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(likeLabel, style = BarStyle, color = BarInk)
                }
                DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                    Row {
                        GazetteReactions.forEach { reaction ->
                            DropdownMenuItem(
                                text = { Text(reaction, style = MaterialTheme.typography.headlineSmall) },
                                onClick = {
                                    choosing = false
                                    react(if (reaction == mine) null else reaction)
                                },
                                modifier = Modifier.widthIn(min = 56.dp).heightIn(min = 56.dp),
                            )
                        }
                    }
                }
            }
            if (!inDetail && (social.own != null || social.commentable)) {
                val commentLabel = stringResource(R.string.feed_comment)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            when {
                                // 💬 opens the field in the card (brief §4); without it, the card.
                                canComment -> Modifier.combinedClickable(onClick = { writing = true })
                                openComments != null -> Modifier.combinedClickable(onClick = openComments)
                                else -> Modifier
                            },
                        )
                        .padding(horizontal = 8.dp)
                        .clearAndSetSemantics {
                            contentDescription = commentLabel
                            role = Role.Button
                        },
                ) {
                    // « 💬 2 Commenter » (prototype).
                    Text("💬", style = BarStyle, color = BarInk)
                    Spacer(Modifier.width(8.dp))
                    if (comments.count > 0) {
                        Text("${comments.count}", style = BarStyle, color = BarInk)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(commentLabel, style = BarStyle, color = BarInk)
                }
            }
            if (!inDetail && count > 0) {
                // « Qui a aimé ? » (prototype): who gave which J'aime.
                Text(
                    stringResource(R.string.feed_who_liked),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = BarInk,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(onClick = { whoLiked = true })
                        .padding(horizontal = 8.dp, vertical = 18.dp)
                        .semantics { role = Role.Button },
                )
            }
        }
        if (!inDetail) {
            // The 2 last comments; a touch opens the card with all of them (brief §4).
            val shown = comments.last.takeLast(FEED_COMMENTS_SHOWN)
            if (shown.isNotEmpty()) {
                val allLabel = pluralStringResource(R.plurals.feed_comment_count, comments.count, comments.count)
                Column(
                    if (openComments != null) {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(onClickLabel = allLabel, onClick = openComments)
                    } else {
                        Modifier
                    },
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) { shown.forEach { FeedCommentText(it) } }
            }
        }
        if (whoLiked) WhoLikedSheet(thread, onClose = { whoLiked = false })
        if (!inDetail && writing && canComment) {
            FeedCommentField(
                onSend = { text ->
                    try {
                        if (social.own != null) {
                            social.own.add(text, env.me)
                        } else {
                            env.comments?.add(social.threadId, text, env.me)
                        }
                        writing = false
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        Toast.makeText(context, failed, Toast.LENGTH_LONG).show()
                        false
                    }
                },
            )
        }
    }
}

/** The ink and the size of the bar (prototype: 18 sp, 700, #3A2A1A). */
private val BarInk = androidx.compose.ui.graphics.Color(0xFF3A2A1A)
private val BarStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)

/** The first name of a member of the family, for « Qui a aimé ? »; given by the gazette. */
val LocalFeedMemberName = staticCompositionLocalOf<(String) -> String?> { { null } }

/** « Qui a aimé ? »: each J'aime with who gave it, the most given first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhoLikedSheet(thread: GazetteThread, onClose: () -> Unit) {
    val nameOf = LocalFeedMemberName.current
    val order = reactionSummary(thread)
    val rows = thread.reactions.entries
        .mapNotNull { (id, emoji) -> nameOf(id)?.let { emoji to it } }
        .sortedWith(compareBy<Pair<String, String>> { order.indexOf(it.first) }.thenBy { it.second })
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.feed_who_liked_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
            )
            rows.forEach { (emoji, name) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(emoji, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(12.dp))
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** « Camille Trop beau ! »: who in bold, then what, 2 lines at most (brief §4, « texte tronqué »). */
@Composable
fun FeedCommentText(line: FeedCommentLine) {
    Text(
        buildAnnotatedString {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append(line.authorName)
            pop()
            append(" ")
            append(line.text.replace(Regex("\\s+"), " "))
        },
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** « Écrire un commentaire… » in the card, focused at once, with its send button (56 dp). */
@Composable
private fun FeedCommentField(onSend: suspend (String) -> Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val send = {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty() && !sending) {
            sending = true
            scope.launch {
                if (onSend(trimmed)) text = ""
                sending = false
            }
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { if (it.length <= FEED_COMMENT_MAX) text = it },
        placeholder = { Text(stringResource(R.string.feed_social_field)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { send() }),
        trailingIcon = {
            IconButton(onClick = { send() }, enabled = text.isNotBlank() && !sending, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.feed_social_send))
            }
        },
        maxLines = 4,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).focusRequester(focus),
    )
}
