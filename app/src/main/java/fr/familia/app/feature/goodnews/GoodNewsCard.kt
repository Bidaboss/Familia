package fr.familia.app.feature.goodnews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.mention.MentionText
import fr.familia.app.core.mention.mentionMembers
import fr.familia.app.core.mention.mentionedIds
import fr.familia.app.core.mention.openMemberFromMention
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Reaction
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.PendingSign
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
                if (item.news.pending) PendingSign(color = FamiliaClay.colors.onTintVariant(tint))
                if (item.news.littleOnes && LocalFeatureFlags.current.isOn(Feature.LittleOnes)) {
                    Text(
                        stringResource(R.string.goodnews_little_ones_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        // « @Prénom » in bold, a touch opens the member (l'admin, 19/11).
        val named = mentionMembers(item.news.mentionIds)
        MentionText(
            item.news.text,
            named,
            openMemberFromMention(),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (full) Int.MAX_VALUE else 6,
        )
        Photos(item, full)
        // Older news named members with boxes, not in the text: « Avec … » for those.
        val inText = mentionedIds(item.news.text, named)
        val withNames = if (named.isEmpty()) item.mentionNames else named.filter { it.id !in inText }.map { it.firstName }
        if (withNames.isNotEmpty()) {
            Text(
                stringResource(R.string.goodnews_with, withNames.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
                color = FamiliaClay.colors.onTintVariant(tint),
            )
        }
        // One heart, a long press for the six (PRODUIT-139).
        HeartReaction(item.news.reactions, item.myReaction, onTouch = onReact, nameOf = { item.reactorNames[it] })
        // In the list, only the heart and the comments; the actions are on the page of the post, discreet.
        if (full && (item.canEdit || item.canRemove || item.canHide)) PostActions(item, onEdit, onRemove, onHide, tint)
    }
}

/** « Modifier · Retirer » for the author (Retirer for the admin too), « Masquer pour moi » for the one named: small, at the bottom. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostActions(item: GoodNewsItem, onEdit: () -> Unit, onRemove: () -> Unit, onHide: (Boolean) -> Unit, tint: ClayTint) {
    val color = FamiliaClay.colors.onTintVariant(tint)
    val actions = buildList<Pair<String, () -> Unit>> {
        if (item.canEdit) add(stringResource(R.string.goodnews_edit) to onEdit)
        if (item.canRemove) add(stringResource(R.string.goodnews_remove) to onRemove)
        if (item.canHide) {
            val label = if (item.isHiddenForMe) R.string.goodnews_unhide else R.string.goodnews_hide
            add(stringResource(label) to { onHide(!item.isHiddenForMe) })
        }
    }
    FlowRow {
        actions.forEachIndexed { index, (label, action) ->
            if (index > 0) Text("·", style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.align(Alignment.CenterVertically).clearAndSetSemantics { })
            TextButton(onClick = action, modifier = Modifier.heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
            }
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
