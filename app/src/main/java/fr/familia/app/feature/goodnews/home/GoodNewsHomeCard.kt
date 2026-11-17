package fr.familia.app.feature.goodnews.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.SectionLabel
import fr.familia.app.feature.goodnews.GoodNewsItem
import fr.familia.app.feature.goodnews.postedLabel
import fr.familia.app.feature.goodnews.previewGoodNewsItems
import fr.familia.app.feature.goodnews.whoLabel
import java.time.Instant

/**
 * The « Bonnes nouvelles » card of the home screen (récit 32): the last three of the last 14 days,
 * or a call to publish when there is none. Placed by the home screen.
 */
@Composable
fun GoodNewsHomeCard(
    container: AppContainer?,
    currentMember: Member,
    onOpenFeed: () -> Unit,
    onOpen: (postId: String) -> Unit,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
    /** Beside the challenge on the Accueil (DESIGN-20): one news, the whole card opens the feed. */
    compact: Boolean = false,
) {
    val viewModel: GoodNewsHomeViewModel = viewModel(
        key = "goodNewsHome",
        factory = viewModelFactory {
            initializer { GoodNewsHomeViewModel(container?.goodNewsRepository, container?.memberRepository, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (compact) GoodNewsCompactContent(state, onOpenFeed, onPublish, modifier) else GoodNewsHomeContent(state, onOpenFeed, onOpen, onPublish, modifier)
}

@Composable
fun GoodNewsHomeContent(
    state: GoodNewsHomeUiState,
    onOpenFeed: () -> Unit,
    onOpen: (String) -> Unit,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = ClayTint.Plain
    ClayCard(modifier = modifier.fillMaxWidth(), tint = tint) {
        ClayCardTitle(stringResource(R.string.goodnews_title))
        val ready = state as? GoodNewsHomeUiState.Ready ?: return@ClayCard
        if (ready.items.isEmpty()) {
            Text(
                stringResource(R.string.goodnews_home_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = FamiliaClay.colors.onTintVariant(tint),
            )
            // Tonal: one filled button per screen, and on the Accueil it is « Répondre » of the
            // question of the week, which has a deadline (relecture, note C).
            FilledTonalButton(onClick = onPublish, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.goodnews_publish))
            }
        } else {
            ready.items.forEach { item -> NewsRow(item, ready.now, tint) { onOpen(item.news.id) } }
        }
        ClayLink(stringResource(R.string.goodnews_home_all), onClick = onOpenFeed, modifier = Modifier.align(Alignment.End))
    }
}

/**
 * The half-width card beside the challenge (DESIGN-20): the small label, the photo of the last news
 * if it has one, who and when on one line, the text on two lines at most. The whole card opens the
 * feed. Empty: a short sentence and « Publier ».
 */
@Composable
fun GoodNewsCompactContent(
    state: GoodNewsHomeUiState,
    onOpenFeed: () -> Unit,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = ClayTint.Plain
    val ready = state as? GoodNewsHomeUiState.Ready
    val last = ready?.items?.firstOrNull()
    val openLabel = stringResource(R.string.goodnews_home_all)
    val clickable = if (last != null) Modifier.clickable(onClickLabel = openLabel, onClick = onOpenFeed) else Modifier
    ClayCard(modifier = modifier.fillMaxWidth().then(clickable), tint = tint) {
        SectionLabel(stringResource(R.string.goodnews_home_compact_label))
        if (ready == null) return@ClayCard
        if (last == null) {
            Text(
                stringResource(R.string.goodnews_home_compact_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = FamiliaClay.colors.onTintVariant(tint),
            )
            FilledTonalButton(onClick = onPublish, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.goodnews_publish))
            }
        } else {
            val news = last.news
            news.photoIds.firstOrNull()?.let {
                StorageImage(news.thumbnailPath(it), null, Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp)))
            }
            Text(
                stringResource(R.string.goodnews_home_row_title, whoLabel(last), postedLabel(news.createdAt, ready.now)),
                style = MaterialTheme.typography.labelLarge,
                color = FamiliaClay.colors.onTintVariant(tint),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(news.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NewsRow(item: GoodNewsItem, now: Instant, tint: ClayTint, onClick: () -> Unit) {
    val openLabel = stringResource(R.string.goodnews_open)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = openLabel, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val news = item.news
        news.photoIds.firstOrNull()?.let { StorageImage(news.thumbnailPath(it), null, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))) }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.goodnews_home_row_title, whoLabel(item), postedLabel(news.createdAt, now)),
                style = MaterialTheme.typography.labelLarge,
                color = FamiliaClay.colors.onTintVariant(tint),
            )
            Text(news.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@FamiliaPreviews
@Composable
private fun GoodNewsHomeCardPreview() {
    FamiliaTheme {
        Surface {
            GoodNewsHomeContent(GoodNewsHomeUiState.Ready(previewGoodNewsItems(), Instant.parse("2026-11-21T16:00:00Z")), {}, {}, {})
        }
    }
}

@FamiliaPreviews
@Composable
private fun GoodNewsCompactPreview() {
    FamiliaTheme {
        Surface {
            GoodNewsCompactContent(GoodNewsHomeUiState.Ready(previewGoodNewsItems(), Instant.parse("2026-11-21T16:00:00Z")), {}, {})
        }
    }
}
