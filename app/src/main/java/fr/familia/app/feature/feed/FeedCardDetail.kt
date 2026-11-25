package fr.familia.app.feature.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.core.model.EventComment
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.caresFor
import fr.familia.app.core.model.isOwner
import fr.familia.app.core.theme.themeBackground
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.feature.events.comments.CommentsSection

/*
 * Le détail d'une carte de la gazette (récit 115, brief §4 et §11 PR 3, fiches D1 à D9): opened by the
 * counter of comments of a card whose comments live on its thread (an anniversaire, a coup de main,
 * the question of the week, an album, a film). The card as in the gazette (a touch still opens its
 * page), its J'aime, then all its comments with the field to answer. Photos and films lie on black
 * (« un seul fond noir »), the rest on the background of the theme. An event and a recipe have their
 * own page with their own comments: they open it instead.
 */

/** The black of the detail of photos and films (fiche D1). */
private val DetailBlack = Color(0xFF15110E)

/**
 * Who removes a comment of the gazette, as the rules say (gazetteThreads/comments): its author, an
 * adult who looks after its child author ([caresFor]: a responsible adult or a chef de famille of
 * its household, règle du 25/11 comme pour les photos d'album) and the admin principal.
 */
fun canRemoveGazetteComment(family: Family, comment: EventComment, me: Member): Boolean {
    if (comment.authorId == me.id || family.isOwner(me)) return true
    val author = family.members.firstOrNull { it.id == comment.authorId } ?: return false
    return caresFor(me, author)
}

/** Over the gazette, full size: every touch stays on it; back closes it. */
@Composable
internal fun FeedCardDetail(
    card: FeedCard,
    social: FeedSocial,
    context: FeedContext,
    env: FeedSocialEnv,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val ink = if (social.dark) Color.White else MaterialTheme.colorScheme.onBackground
    Box(
        Modifier
            .fillMaxSize()
            .then(if (social.dark) Modifier.background(DetailBlack) else Modifier.themeBackground())
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color.White.copy(alpha = 0.92f)) {
                    BackButton(onClick = onClose)
                }
                Text(
                    stringResource(social.title),
                    style = MaterialTheme.typography.titleLarge,
                    color = ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                // Keeps the title in the middle, across from the round of « Retour ».
                Spacer(Modifier.size(56.dp))
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ClaySpacing.screen)
                    .padding(bottom = 32.dp),
            ) {
                // The card as in the gazette, without its bar: the J'aime come right under it.
                CompositionLocalProvider(
                    LocalFeedGlass provides false,
                    LocalFeedCardSocial provides null,
                    LocalFeedCardDetail provides null,
                ) {
                    card.Content(context)
                }
                val scheme = if (social.dark) {
                    MaterialTheme.colorScheme.copy(onSurface = Color.White, onSurfaceVariant = Color(0xFFB8AEA4), onBackground = Color.White)
                } else {
                    MaterialTheme.colorScheme
                }
                MaterialTheme(colorScheme = scheme) {
                    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides ink) {
                        FeedSocialBar(social, env, onOpen = null, inDetail = true)
                        if (social.commentable && env.comments != null) {
                            CommentsSection(
                                repository = env.comments,
                                container = env.container,
                                currentMember = env.me,
                                key = "gazette-comments-${social.threadId}",
                                parentId = social.threadId,
                                readOnly = env.readOnly,
                                canRemove = ::canRemoveGazetteComment,
                                // No notice of the comments of the gazette yet (PR 7): nothing to mute.
                                canMute = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
