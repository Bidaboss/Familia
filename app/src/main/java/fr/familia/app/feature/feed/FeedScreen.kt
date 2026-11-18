package fr.familia.app.feature.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.currentDayFlow
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.core.viewas.LocalViewAs
import fr.familia.app.feature.presentation.LocalPresenting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.util.Locale

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
    // Read only, in the Mode présentation and in « Voir comme » (récit 67).
    val readOnly = LocalPresenting.current || LocalViewAs.current.viewed != null
    return FeedState(
        cards = cards,
        context = family?.let { FeedContext(me, it, today, readOnly) },
        unseen = cards?.let { unseenCount(it, seenAt, me.id) } ?: 0,
        canLoadMore = pages < MAX_PAGES,
        onMore = { pages += 1 },
        onSeen = {
            val at = Instant.now()
            seenAt = at
            // Read only (« Voir comme », présentation): looking does not mark the fil as seen.
            if (!readOnly) prefs.setSeenAt(me.id, at)
        },
    )
}

/**
 * Under « Bonjour » (PRODUIT-128, variante C): a line that says where I am, and one button that
 * says where it leads. On Aujourd'hui, the date and a pale green « Le fil 4 › » with the number of
 * new cards; on the fil, « Le fil de la famille » and a white « ‹ Aujourd'hui ». Targets of 56 dp;
 * at large text the button goes under the words.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeFeedSwitch(onFeed: Boolean, unseen: Int, onChoose: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (onFeed) {
                stringResource(R.string.feed_switch_feed_title)
            } else {
                frenchDate(familyToday(), "EEEE d MMMM").replaceFirstChar { it.titlecase(Locale.FRANCE) }
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically).padding(end = 12.dp),
        )
        if (onFeed) {
            val description = stringResource(R.string.feed_switch_back)
            Surface(
                onClick = { onChoose(false) },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.align(Alignment.CenterVertically).heightIn(min = 56.dp).semantics { contentDescription = description },
            ) {
                Row(Modifier.padding(start = 12.dp, end = 18.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.feed_tab_today), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                }
            }
        } else {
            val news = if (unseen > 0) pluralStringResource(R.plurals.feed_tab_new, unseen, unseen) else null
            val description = stringResource(R.string.feed_switch_open) + (news?.let { ", $it" } ?: "")
            Surface(
                onClick = { onChoose(true) },
                shape = RoundedCornerShape(24.dp),
                color = FEED_BUTTON_GREEN,
                contentColor = FEED_BUTTON_GREEN_TEXT,
                modifier = Modifier.align(Alignment.CenterVertically).heightIn(min = 56.dp).semantics { contentDescription = description },
            ) {
                Row(Modifier.padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.feed_tab_feed), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                    if (unseen > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (unseen > 99) "99+" else unseen.toString(),
                            color = Color(0xFF1E1B18),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF2C14E))
                                .padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
}

/** The pale green of the button « Le fil », and its text: kept in both themes, like the labels of the cards. */
private val FEED_BUTTON_GREEN = Color(0xFFEDF7F0)
private val FEED_BUTTON_GREEN_TEXT = Color(0xFF1F5C3C)

/**
 * « Le fil » (maquette F2 bis): the cards newest first, cut by day, then « Voir plus ancien ». It
 * says when nothing happened in the window.
 */
@Composable
fun FeedList(state: FeedState, modifier: Modifier = Modifier) {
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
        if (cards.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.feed_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
