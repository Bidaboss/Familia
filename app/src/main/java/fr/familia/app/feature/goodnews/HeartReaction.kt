package fr.familia.app.feature.goodnews

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import fr.familia.app.R
import fr.familia.app.core.model.Reaction
import fr.familia.app.core.viewas.LocalViewAs
import kotlinx.coroutines.delay

/**
 * The reaction to touch with one tap (PRODUIT-139): a heart, or the reaction already given, which
 * a tap takes back. Pass it to [nextReaction] like any touched reaction.
 */
fun tappedReaction(mine: Reaction?): Reaction = mine ?: Reaction.Heart

/** The 2 or 3 reactions given the most, most given first, then in their usual order. */
fun topReactions(reactions: Map<String, Reaction>, max: Int = 3): List<Reaction> =
    Reaction.entries
        .map { r -> r to reactions.values.count { it == r } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(max)
        .map { it.first }

/**
 * One heart button and, beside it, « ❤️👏 5 » (PRODUIT-139). A tap puts or takes back the
 * reaction, a long press opens the six reactions. [onTouch] gets the touched reaction, for
 * [nextReaction]. [nameOf] gives the first name of a member, for « who reacted ».
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeartReaction(
    reactions: Map<String, Reaction>,
    mine: Reaction?,
    onTouch: (Reaction) -> Unit,
    nameOf: (String) -> String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val viewing = LocalViewAs.current.viewed != null
    val canTouch = enabled && !viewing
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf(false) }
    var who by remember { mutableStateOf(false) }
    val chooseLabel = stringResource(R.string.goodnews_reaction_choose)
    val heartLabel = if (mine == null) {
        stringResource(R.string.goodnews_heart_add)
    } else {
        stringResource(R.string.goodnews_heart_remove, stringResource(reactionName(mine)))
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        enabled = canTouch,
                        role = Role.Button,
                        onClickLabel = heartLabel,
                        onLongClickLabel = chooseLabel,
                        onLongClick = { hint = false; picking = true },
                        onClick = {
                            onTouch(tappedReaction(mine))
                            if (!ReactionHint.seen(context)) {
                                ReactionHint.markSeen(context)
                                hint = true
                            }
                        },
                    )
                    .semantics { contentDescription = heartLabel },
            ) {
                if (mine == null) {
                    Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(mine.emoji, style = MaterialTheme.typography.titleLarge)
                }
            }
            if (picking) {
                AboveAnchorPopup(onDismiss = { picking = false }, focusable = true) {
                    ReactionPicker(mine) { touched ->
                        picking = false
                        onTouch(touched)
                    }
                }
            }
            if (hint) {
                LaunchedEffect(Unit) {
                    delay(HINT_MILLIS)
                    hint = false
                }
                // Not focusable: the next touch on the screen goes where it is aimed (relecture renfort, C1).
                AboveAnchorPopup(onDismiss = { hint = false }, focusable = false) { ReactionHintBubble(onTap = { hint = false }) }
            }
        }
        val total = reactions.size
        if (total > 0) {
            val summary = topReactions(reactions).joinToString("") { it.emoji } + " $total"
            val description = pluralStringResource(R.plurals.goodnews_reactions_total, total, total)
            Text(
                summary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = stringResource(R.string.goodnews_reactions_who), role = Role.Button) { who = true }
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .semantics { contentDescription = description },
            )
        }
    }
    if (who) ReactorsDialog(reactions, nameOf, onDismiss = { who = false })
}

/** The six reactions in a small bar (56 dp each, 344 dp in all): the one already given is marked. */
@Composable
private fun ReactionPicker(mine: Reaction?, onTouch: (Reaction) -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 6.dp, shadowElevation = 6.dp) {
        Row(Modifier.padding(4.dp)) {
            Reaction.entries.forEach { reaction ->
                val name = stringResource(reactionName(reaction))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .then(if (reaction == mine) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                        .clickable(role = Role.Button) { onTouch(reaction) }
                        .semantics { contentDescription = name },
                ) {
                    Text(reaction.emoji, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

/** Shown once per phone, after the first heart: the long press is there to be found. */
@Composable
private fun ReactionHintBubble(onTap: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
        modifier = Modifier.widthIn(max = 280.dp).clickable(onClick = onTap),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.goodnews_reaction_hint), style = MaterialTheme.typography.bodyMedium)
            Text(
                Reaction.entries.joinToString(" ") { it.emoji },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/** « Ont réagi »: each first name with its reaction. */
@Composable
private fun ReactorsDialog(reactions: Map<String, Reaction>, nameOf: (String) -> String?, onDismiss: () -> Unit) {
    val unknown = stringResource(R.string.goodnews_reactions_someone)
    val rows = reactions.entries
        .map { (id, reaction) -> (nameOf(id) ?: unknown) to reaction }
        .sortedWith(compareBy({ it.second.ordinal }, { it.first }))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goodnews_reactions_who)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { (name, reaction) -> Text("${reaction.emoji}  $name", style = MaterialTheme.typography.bodyLarge) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.goodnews_reactions_close)) }
        },
    )
}

/** A popup drawn just above its anchor, kept inside the screen. */
@Composable
private fun AboveAnchorPopup(onDismiss: () -> Unit, focusable: Boolean, content: @Composable () -> Unit) {
    Popup(popupPositionProvider = AboveAnchor, onDismissRequest = onDismiss, properties = PopupProperties(focusable = focusable)) {
        content()
    }
}

private object AboveAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - popupContentSize.height
        val y = if (above >= 0) above else anchorBounds.bottom
        return IntOffset(x, y)
    }
}

private const val HINT_MILLIS = 4_000L

/** « Reste appuyé… » is shown once per phone (PRODUIT-139). */
internal object ReactionHint {
    private const val KEY = "reaction_hint_seen"
    fun seen(context: Context): Boolean = prefs(context).getBoolean(KEY, false)
    fun markSeen(context: Context) = prefs(context).edit().putBoolean(KEY, true).apply()
    private fun prefs(context: Context) = context.getSharedPreferences("familia", Context.MODE_PRIVATE)
}
