package fr.familia.app.feature.yearago

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.viewas.LocalViewAs
import java.time.LocalDate

/** The « Il y a un an » card of the Accueil (récit 36): shown only on a day that has a souvenir. */
@Composable
fun YearAgoHomeCard(
    container: AppContainer?,
    currentMember: Member,
    onOpenGoodNews: (String) -> Unit,
    onOpenTrip: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { YearAgoPreferences(context.applicationContext) }
    val viewingAs = LocalViewAs.current.viewed != null
    val today = LocalDate.now(YearAgo.ZONE)
    // Each source follows its module: a good news or a trip of a module that is off stays out.
    val flags = LocalFeatureFlags.current
    val newsOn = flags.isOn(Feature.GoodNews)
    val tripsOn = flags.isOn(Feature.Trips)
    val viewModel: YearAgoViewModel = viewModel(
        key = "yearAgo-$today-${currentMember.id}-$newsOn-$tripsOn",
        factory = viewModelFactory {
            initializer {
                YearAgoViewModel(
                    if (newsOn) container?.goodNewsRepository else null,
                    if (tripsOn) container?.tripRepository else null,
                    container?.albumPhotoRepository,
                    currentMember.id,
                    today,
                    store,
                    canDismiss = !viewingAs,
                )
            }
        },
    )
    val memory by viewModel.memory.collectAsStateWithLifecycle()
    memory?.let { m ->
        YearAgoContent(
            memory = m,
            onOpen = {
                when (m) {
                    is YearAgoMemory.News -> onOpenGoodNews(m.news.id)
                    is YearAgoMemory.TripPhoto -> onOpenTrip(m.trip.id)
                }
            },
            onDismiss = if (viewModel.canDismiss) ({ viewModel.dismiss(m) }) else null,
            modifier = modifier,
        )
    }
}

@Composable
fun YearAgoContent(memory: YearAgoMemory, onOpen: () -> Unit, onDismiss: (() -> Unit)?, modifier: Modifier = Modifier) {
    val tint = ClayTint.Plain
    ClayCard(modifier = modifier.fillMaxWidth(), tint = tint) {
        Text(stringResource(R.string.year_ago_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        val thumbnail: String?
        val title: String
        val detail: String
        val openLabel: String
        when (memory) {
            is YearAgoMemory.News -> {
                thumbnail = memory.news.photoIds.firstOrNull()?.let { memory.news.thumbnailPath(it) }
                title = memory.news.text
                detail = stringResource(R.string.year_ago_news, memory.news.authorName)
                openLabel = stringResource(R.string.goodnews_open)
            }
            is YearAgoMemory.TripPhoto -> {
                thumbnail = memory.photo.thumbnailPath
                title = memory.trip.title
                detail = stringResource(R.string.year_ago_trip, memory.photo.authorName)
                openLabel = stringResource(R.string.recap_trip_open)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = openLabel, onClick = onOpen),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            thumbnail?.let { StorageImage(it, null, Modifier.size(72.dp).clip(RoundedCornerShape(16.dp))) }
            Column(Modifier.weight(1f)) {
                if (title.isNotBlank()) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = FamiliaClay.colors.onTintVariant(tint))
            }
        }
        if (onDismiss != null) {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp).align(Alignment.End)) {
                Text(stringResource(R.string.year_ago_dismiss))
            }
        }
    }
}
