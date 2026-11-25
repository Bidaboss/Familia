package fr.familia.app.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.viewas.LocalViewAs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/** The fil as the Accueil holds it: the cards (null while they load) and the badge of « Le fil ». */
class FeedState(
    val cards: List<FeedCard>?,
    val context: FeedContext?,
    val unseen: Int,
    val canLoadMore: Boolean,
    val onMore: () -> Unit,
    val onSeen: () -> Unit,
)

/** « Voir plus ancien » goes back 14 days each time, 12 weeks at most. */
private const val MAX_PAGES = 6

@Composable
fun rememberFeedState(container: AppContainer?, me: Member): FeedState {
    val families = remember(container) { container?.memberRepository?.observeFamily()?.catch { } ?: flowOf(null) }
    val family by families.collectAsStateWithLifecycle(initialValue = null)
    val days = remember { currentDayFlow() }
    val today by days.collectAsStateWithLifecycle(initialValue = familyToday())
    var pages by rememberSaveable { mutableIntStateOf(1) }
    val now = remember(pages, today) { Instant.now() }
    val sources = rememberFeedSources(container, me, family, today)
    val flow: Flow<List<FeedCard>> = remember(sources, pages, now) {
        val window = FeedWindow(pages, now)
        if (sources.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(sources.map { source -> source.cards(window).catch { emit(emptyList()) } }) { lists -> mergeFeed(lists.toList()) }
        }
    }
    val cards by flow.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val prefs = remember(context) { FeedPreferences(context.applicationContext) }
    var seenAt by remember(me.id) { mutableStateOf(prefs.seenAt(me.id)) }
    // Read only in « Voir comme » (récit 67).
    val readOnly = LocalViewAs.current.viewed != null
    return FeedState(
        cards = cards,
        context = family?.let { FeedContext(me, it, today, readOnly) },
        unseen = cards?.let { unseenCount(it, seenAt, me.id) } ?: 0,
        canLoadMore = pages < MAX_PAGES,
        onMore = { pages += 1 },
        onSeen = {
            val at = Instant.now()
            seenAt = at
            // Read only (« Voir comme »): looking does not mark the fil as seen.
            if (!readOnly) prefs.setSeenAt(me.id, at)
        },
    )
}

/**
 * « Le fil » (maquette F2 bis): the cards newest first, cut by day, then « Voir plus ancien ». It
 * says when nothing happened in the window.
 */
@Composable
fun FeedList(state: FeedState, modifier: Modifier = Modifier, top: (@Composable () -> Unit)? = null) {
    val cards = state.cards
    val context = state.context
    LaunchedEffect(cards) { state.onSeen() }
    if (cards == null || context == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val todayLabel = stringResource(R.string.feed_day_today)
    val yesterdayLabel = stringResource(R.string.feed_day_yesterday)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 4.dp, bottom = 24.dp),
        // 8 dp between two actualités of a day (PRODUIT-127); the day titles keep their 12 dp.
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        top?.let { item(key = "top") { it() } }
        if (cards.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.feed_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (LocalFeedGlass.current) fr.familia.app.core.theme.PhotoSectionText else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        feedDays(cards).forEach { (day, onDay) ->
            item(key = "day-$day") {
                FeedDayHeader(feedDayLabel(day, context.today, todayLabel, yesterdayLabel), Modifier.padding(vertical = 4.dp))
            }
            items(onDay, key = { it.key }) { card -> card.Content(context) }
        }
        if (state.canLoadMore) {
            item(key = "more") {
                OutlinedButton(onClick = state.onMore, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.feed_more))
                }
            }
        }
    }
}
