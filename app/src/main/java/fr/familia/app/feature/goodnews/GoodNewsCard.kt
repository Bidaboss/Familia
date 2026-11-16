package fr.familia.app.feature.goodnews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Reaction
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.feature.souvenirs.SouvenirFamily
import java.time.Instant

/**
 * A good news in the Argile style. In the feed ([full] false) the text is cut and the photos are
 * small; on its own page ([full] true) everything is shown, photos at full size. Reactions work
 * the same in both.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoodNewsCard(
    item: GoodNewsItem,
    now: Instant,
    full: Boolean,
    onReact: (Reaction) -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
    onEdit: () -> Unit = {},
    onRemove: () -> Unit = {},
    onHide: (Boolean) -> Unit = {},
) {
    val tint = ClayTint.Plain
    ClayCard(
        modifier = modifier.fillMaxWidth(),
        tint = tint,
        onClick = onOpen,
        onClickLabel = stringResource(R.string.goodnews_open),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                item.author != null -> MemberAvatar(item.author)
                item.news.challengeMonth != null -> ChallengeAvatar()
                item.news.birthdayOf != null -> BirthdayAvatar()
            }
            Column(Modifier.weight(1f)) {
                Text(whoLabel(item), style = MaterialTheme.typography.titleMedium)
                val posted = postedLabel(item.news.createdAt, now)
                Text(
                    if (item.news.isEdited) stringResource(R.string.goodnews_posted_edited, posted) else posted,
                    style = MaterialTheme.typography.bodySmall,
                    color = FamiliaClay.colors.onTintVariant(tint),
                )
            }
            // In the feed the actions sit in the ⋮ menu; on the page of the post they are visible buttons.
            if (!full && (item.canEdit || item.canRemove || item.canHide)) PostMenu(item, onEdit, onRemove, onHide)
        }
        Text(
            item.news.text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (full) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis,
        )
        Photos(item, full)
        if (item.mentionNames.isNotEmpty()) {
            Text(
                stringResource(R.string.goodnews_with, item.mentionNames.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
                color = FamiliaClay.colors.onTintVariant(tint),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item.reactionCounts.forEach { (reaction, count) -> ReactionChip(reaction, count, item.myReaction == reaction, onReact) }
        }
        if (full && (item.canEdit || item.canRemove || item.canHide)) PostActions(item, onEdit, onRemove, onHide)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostActions(item: GoodNewsItem, onEdit: () -> Unit, onRemove: () -> Unit, onHide: (Boolean) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (item.canEdit) {
            ClayLink(stringResource(R.string.goodnews_edit), onClick = onEdit)
        }
        if (item.canHide) {
            val label = if (item.isHiddenForMe) R.string.goodnews_unhide else R.string.goodnews_hide
            ClayLink(stringResource(label), onClick = { onHide(!item.isHiddenForMe) })
        }
        if (item.canRemove) {
            TextButton(
                onClick = onRemove,
                modifier = Modifier.heightIn(min = 56.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.goodnews_remove)) }
        }
    }
}

@Composable
private fun Photos(item: GoodNewsItem, full: Boolean) {
    val news = item.news
    val description = stringResource(R.string.goodnews_photo_of, whoLabel(item))
    val shape = RoundedCornerShape(16.dp)
    when {
        news.photoIds.isEmpty() -> Unit // « Défi réussi » (récit 35), « Joyeux anniversaire » (récit 36).
        full -> news.photoIds.forEach { id ->
            StorageImage(news.photoPath(id), description, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(shape))
        }
        // One photo: the photo itself, the thumbnail would look blurred at this width.
        news.photoIds.size == 1 ->
            StorageImage(news.photoPath(news.photoIds[0]), description, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(shape))
        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            news.photoIds.forEach { id ->
                StorageImage(news.thumbnailPath(id), description, Modifier.weight(1f).aspectRatio(1f).clip(shape))
            }
            // Two photos keep the size of three, so every card has the same rhythm.
            if (news.photoIds.size == 2) Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReactionChip(reaction: Reaction, count: Int, mine: Boolean, onReact: (Reaction) -> Unit) {
    val name = stringResource(reactionName(reaction))
    val description = if (mine) {
        stringResource(R.string.goodnews_reaction_mine, name, count)
    } else {
        stringResource(R.string.goodnews_reaction, name, count)
    }
    FilterChip(
        selected = mine,
        onClick = { onReact(reaction) },
        label = { Text(if (count > 0) "${reaction.emoji} $count" else reaction.emoji, style = MaterialTheme.typography.titleSmall) },
        modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = description },
    )
}

@Composable
private fun PostMenu(item: GoodNewsItem, onEdit: () -> Unit, onRemove: () -> Unit, onHide: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.goodnews_menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (item.canEdit) {
                DropdownMenuItem(text = { Text(stringResource(R.string.goodnews_edit)) }, onClick = { open = false; onEdit() })
            }
            if (item.canHide) {
                val label = if (item.isHiddenForMe) R.string.goodnews_unhide else R.string.goodnews_hide
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { open = false; onHide(!item.isHiddenForMe) })
            }
            if (item.canRemove) {
                DropdownMenuItem(text = { Text(stringResource(R.string.goodnews_remove)) }, onClick = { open = false; onRemove() })
            }
        }
    }
}

fun reactionName(reaction: Reaction): Int = when (reaction) {
    Reaction.Heart -> R.string.goodnews_reaction_heart
    Reaction.Clap -> R.string.goodnews_reaction_clap
    Reaction.Party -> R.string.goodnews_reaction_party
    Reaction.Laugh -> R.string.goodnews_reaction_laugh
    Reaction.Wow -> R.string.goodnews_reaction_wow
    Reaction.ThumbsUp -> R.string.goodnews_reaction_thumbs_up
}

/**
 * The avatar of the « Défi réussi » published by Familia (récit 35), in the size of a member's
 * avatar: the medal of the Fêtes souvenirs (DESIGN, 16/11), so the challenge has the same image everywhere.
 */
@Composable
private fun BirthdayAvatar() {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(SouvenirFamily.Party.light),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Cake, contentDescription = null, tint = SouvenirFamily.Party.dark)
    }
}

@Composable
private fun ChallengeAvatar() {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(SouvenirFamily.Party.light),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = SouvenirFamily.Party.dark)
    }
}
