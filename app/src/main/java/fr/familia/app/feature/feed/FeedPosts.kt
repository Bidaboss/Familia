package fr.familia.app.feature.feed

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.data.GazettePostRepository
import fr.familia.app.core.data.gazetteThreadId
import fr.familia.app.core.mention.MentionText
import fr.familia.app.core.model.GazettePost
import fr.familia.app.core.model.canRemoveGazettePost
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.formatTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/*
 * « Publication » et « Photos » de la gazette (récits 117 et 121, fiches C5, C6, M2, R4): what a
 * member published with « ＋ Publier », a word, up to 6 photos in a carousel « 1 / 3 », or both.
 * « ⋯ » opens « Ma publication »: « Modifier le texte » for its author, « Supprimer » for its author,
 * a responsible adult of its author and the admin principal, after a confirmation.
 */

/** The publications since [since], newest first, [limit] at most. */
fun postsForFeed(posts: List<GazettePost>, since: Instant, limit: Int): List<GazettePost> = posts
    .filter { it.createdAt?.isBefore(since) == false }
    .sortedByDescending { it.createdAt }
    .take(limit)

class PostFeedSource(private val posts: GazettePostRepository) : FeedSource {
    override fun cards(window: FeedWindow): Flow<List<FeedCard>> =
        posts.observeSince(window.since, window.limit).map { list ->
            postsForFeed(list, window.since, window.limit).map { PostFeedCard(it, posts) }
        }
}

class PostFeedCard(val post: GazettePost, private val posts: GazettePostRepository?) : FeedCard {
    override val key: String = "post:${post.id}"
    override val at: Instant = post.createdAt ?: Instant.EPOCH
    override val authorId: String = post.authorId

    /** Photos lie on black in their detail (brief §3), a word on the background of the theme. */
    override val social: FeedSocial = FeedSocial(
        gazetteThreadId(key),
        title = if (post.photoIds.isEmpty()) R.string.feed_detail_post else R.string.feed_detail_photos,
        dark = post.photoIds.isNotEmpty(),
    )

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val canEdit = !context.readOnly && post.authorId == context.me.id
        val canRemove = !context.readOnly && posts != null && canRemoveGazettePost(post, context.me, context.family)
        var menu by remember(post.id) { mutableStateOf(false) }
        FeedCardShell(
            tone = FeedTone.Green,
            onOpen = LocalFeedCardDetail.current,
            header = { PostHeader(post, context, onMenu = if (canEdit || canRemove) ({ menu = true }) else null) },
        ) {
            if (post.photoIds.isNotEmpty()) PostPhotos(post)
            if (post.text.isNotBlank()) {
                MentionText(post.text, post.mentionIds, style = MaterialTheme.typography.bodyLarge, maxLines = if (LocalFeedCardDetail.current == null) Int.MAX_VALUE else 6)
            }
        }
        var removing by remember(post.id) { mutableStateOf(false) }
        if (menu) {
            PostMenu(
                mine = post.authorId == context.me.id,
                onEdit = if (canEdit) ({ menu = false; navigation.onEditPost(post.id) }) else null,
                onRemove = if (canRemove) ({ menu = false; removing = true }) else null,
                onDismiss = { menu = false },
            )
        }
        // The scope of the card, not of the dialog: closing the dialog never stops the removal.
        val scope = rememberCoroutineScope()
        val toast = LocalContext.current
        val failed = stringResource(R.string.gazette_post_remove_failed)
        if (removing && posts != null) {
            RemoveDialog(
                onConfirm = {
                    removing = false
                    scope.launch {
                        try {
                            // Offline, the removal is kept on the phone and goes later.
                            withTimeoutOrNull(4_000) { posts.remove(post.id) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            Toast.makeText(toast, failed, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onClose = { removing = false },
            )
        }
    }
}

/** « Supprimer cette publication ? »: the photos, the J'aime and the comments go with it (récit 121). */
@Composable
private fun RemoveDialog(onConfirm: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.gazette_post_remove_title)) },
        text = { Text(stringResource(R.string.gazette_post_remove_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.gazette_post_remove_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** « (A) Alix a publié 3 photos   ⋯ » then « 8 h 12 · modifié ». */
@Composable
private fun PostHeader(post: GazettePost, context: FeedContext, onMenu: (() -> Unit)?) {
    val author = context.member(post.authorId)
    val name = author?.firstName ?: post.authorName
    val what = when {
        post.photoIds.isEmpty() -> stringResource(R.string.gazette_post_published_word)
        else -> pluralStringResource(R.plurals.gazette_post_published_photos, post.photoIds.size, post.photoIds.size)
    }
    val time = formatTime((post.createdAt ?: Instant.now()).atZone(FAMILY_ZONE).toLocalTime())
    val edited = stringResource(R.string.gazette_post_edited)
    val pending = stringResource(R.string.gazette_post_pending)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (author != null) MemberAvatar(author, size = if (LocalFeedGlass.current) 40.dp else 30.dp)
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name) }
                    append(" ")
                    append(what)
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(time, edited.takeIf { post.isEdited }, pending.takeIf { post.pending }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onMenu != null) {
            IconButton(onClick = onMenu, modifier = Modifier.size(56.dp)) {
                Text("⋯", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** The photos one by one, « 1 / 3 » in the corner (fiche C5). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostPhotos(post: GazettePost) {
    val pager = rememberPagerState { post.photoIds.size }
    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp))) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            StorageImage(
                post.photoPath(post.photoIds[page]),
                stringResource(R.string.gazette_post_photo, page + 1, post.photoIds.size),
                Modifier.fillMaxSize(),
            )
        }
        if (post.photoIds.size > 1) {
            Text(
                "${pager.currentPage + 1} / ${post.photoIds.size}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** « Ma publication » (fiche C6): « ✏️ Modifier le texte », « 🗑 Supprimer ». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostMenu(mine: Boolean, onEdit: (() -> Unit)?, onRemove: (() -> Unit)?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (mine) R.string.gazette_post_menu_mine else R.string.gazette_post_menu_other),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            onEdit?.let {
                OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.gazette_post_menu_edit))
                }
            }
            onRemove?.let {
                OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.gazette_post_menu_remove), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
