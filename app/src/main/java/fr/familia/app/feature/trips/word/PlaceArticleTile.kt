package fr.familia.app.feature.trips.word

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.place.PlaceArticle
import fr.familia.app.core.place.PlaceArticles
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.feature.trips.TripTile

/** What the tile shows while it asks the web. */
private sealed interface ArticleState {
    data object Loading : ArticleState
    data object None : ArticleState
    data class Found(val article: PlaceArticle) : ArticleState
}

/**
 * « À savoir sur La Palmyre » (récit 96): a few lines of Wikivoyage about the place of the step, the
 * source and licence under them, « Lire la suite » in the browser. Hidden when there is no article
 * (or no network); grey blocks while loading, never a spinner. Nothing is stored.
 */
@Composable
fun PlaceArticleTile(place: String, articles: PlaceArticles?) {
    var state by remember(place) { mutableStateOf<ArticleState>(if (articles == null) ArticleState.None else ArticleState.Loading) }
    LaunchedEffect(place, articles) {
        if (articles == null) return@LaunchedEffect
        state = articles.about(place)?.let { ArticleState.Found(it) } ?: ArticleState.None
    }
    val uri = LocalUriHandler.current
    when (val current = state) {
        ArticleState.None -> Unit
        ArticleState.Loading -> TripTile(Icons.Outlined.Place, stringResource(R.string.place_article_title, place)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) }
            }
            FormHint(stringResource(R.string.place_article_loading))
        }
        is ArticleState.Found -> TripTile(Icons.Outlined.Place, stringResource(R.string.place_article_title, place)) {
            Text(current.article.extract, style = MaterialTheme.typography.bodyLarge)
            TextButton(
                onClick = { runCatching { uri.openUri(current.article.url) } },
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.place_article_more)) }
            FormHint(stringResource(R.string.place_article_source))
        }
    }
}
