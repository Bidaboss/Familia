package fr.familia.app.feature.goodnews.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import fr.familia.app.core.ui.FamiliaPreviews
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
) {
    val viewModel: GoodNewsHomeViewModel = viewModel(
        key = "goodNewsHome",
        factory = viewModelFactory {
            initializer { GoodNewsHomeViewModel(container?.goodNewsRepository, container?.memberRepository, currentMember.id) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GoodNewsHomeContent(state, onOpenFeed, onOpen, onPublish, modifier)
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
        Text(stringResource(R.string.goodnews_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        val ready = state as? GoodNewsHomeUiState.Ready ?: return@ClayCard
        if (ready.items.isEmpty()) {
            Text(
                stringResource(R.string.goodnews_home_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = FamiliaClay.colors.onTintVariant(tint),
            )
            Button(onClick = onPublish, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.goodnews_publish)) }
        } else {
            ready.items.forEach { item -> NewsRow(item, ready.now, tint) { onOpen(item.news.id) } }
        }
        TextButton(onClick = onOpenFeed, modifier = Modifier.heightIn(min = 48.dp).align(Alignment.End)) {
            Text(stringResource(R.string.goodnews_home_all))
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
        StorageImage(news.thumbnailPath(news.photoIds.first()), null, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
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
