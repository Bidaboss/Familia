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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import kotlinx.coroutines.delay
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
    /** J'aime and comments under the cards (récit 115); null without Firebase. */
    val social: FeedSocialEnv? = null,
)

/** The brown of « ＋ Publier » (brief §3: « brun très foncé, texte clair »). */
private val PublishBrown = Color(0xFF3A2A1A)

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
    // Back from a publication the gazette comes back at once, where it was (l'admin, 26/11): the last
    // cards of this member show while the fresh ones load, so the list is never empty (and scrolled to
    // the top) for a moment.
    val cards by flow.collectAsStateWithLifecycle(initialValue = FeedCardsMemory.of(me.id))
    LaunchedEffect(cards) { cards?.let { FeedCardsMemory.keep(me.id, it) } }
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
        social = remember(container, me, readOnly) {
            container?.gazetteThreadRepository?.let { FeedSocialEnv(it, container.gazetteCommentRepository, me, readOnly, container) }
        },
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
fun FeedList(
    state: FeedState,
    modifier: Modifier = Modifier,
    top: (@Composable () -> Unit)? = null,
    /** « ＋ Publier » floating at the bottom (brief §3); null in « Voir comme ». */
    onPublish: (() -> Unit)? = null,
) {
    val cards = state.cards
    val context = state.context
    LaunchedEffect(cards) { state.onSeen() }
    // Before the early return below, so the scroll is kept (saved) whatever the loading does.
    val listState = rememberLazyListState()
    if (cards == null || context == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val todayLabel = stringResource(R.string.feed_day_today)
    val yesterdayLabel = stringResource(R.string.feed_day_yesterday)
    // The card opened by its counter of comments (brief §4), over the gazette; kept by its key.
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    val detail = detailKey?.let { key -> cards.firstOrNull { it.key == key } }
    // A touched notification (a comment, a mention) opens the detail of its card once it shows;
    // a card no longer in the gazette leaves the gazette as it is.
    val askedThread by GazetteThreadRequest.asked.collectAsStateWithLifecycle()
    LaunchedEffect(askedThread, cards) {
        val threadId = askedThread ?: return@LaunchedEffect
        cardOfThread(cards, threadId)?.let {
            detailKey = it.key
            GazetteThreadRequest.consume()
        }
    }
    LaunchedEffect(askedThread) {
        if (askedThread != null) {
            delay(10_000)
            GazetteThreadRequest.consume()
        }
    }
    val nameOf: (String) -> String? = remember(context) { { id -> context.member(id)?.firstName } }
    Box(modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Room under the last card for « ＋ Publier ».
        contentPadding = PaddingValues(start = ClaySpacing.screen, end = ClaySpacing.screen, top = 4.dp, bottom = if (onPublish != null) 96.dp else 24.dp),
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
                FeedDayHeader(feedDayLabel(day, context.today, todayLabel, yesterdayLabel), feedDayEmoji(day, context.today))
            }
            items(onDay, key = { it.key }) { card ->
                // Only a card whose comments live on its thread has a detail; the others open their page.
                val openDetail: (() -> Unit)? = if (state.social != null && card.social?.own == null) ({ detailKey = card.key }) else null
                CompositionLocalProvider(
                    LocalFeedMemberName provides nameOf,
                    LocalFeedSocialEnv provides state.social,
                    LocalFeedCardSocial provides card.social,
                    LocalFeedCardDetail provides openDetail,
                ) {
                    card.Content(context)
                }
            }
        }
        if (state.canLoadMore) {
            item(key = "more") {
                // A button of cream glass, dark brown text (prototype), readable on the photo.
                Button(
                    onClick = state.onMore,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xF2FFFAF2), contentColor = PublishBrown),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.feed_more), fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
    if (onPublish != null) {
        // « ＋ Publier », folded into « + » once the gazette scrolls, unfolded back at the top.
        val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 40 } }
        val label = stringResource(R.string.gazette_publish_fab)
        ExtendedFloatingActionButton(
            // Prototype: 16 sp, 800, radius 24, a marked shadow.
            text = { Text(label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) },
            icon = { Text("＋", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) },
            expanded = atTop,
            onClick = onPublish,
            shape = RoundedCornerShape(24.dp),
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 10.dp, pressedElevation = 6.dp),
            containerColor = PublishBrown,
            contentColor = Color(0xFFFFF6EA),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = ClaySpacing.screen, bottom = 16.dp)
                .heightIn(min = 56.dp)
                .semantics { contentDescription = label },
        )
    }
    val social = detail?.social
    if (detail != null && social != null && state.social != null) {
        FeedCardDetail(detail, social, context, state.social, onClose = { detailKey = null })
    }
    }
}

/** The last cards shown for a member, kept for the session so the gazette reopens as it was. */
internal object FeedCardsMemory {
    private var memberId: String? = null
    private var cards: List<FeedCard>? = null

    fun of(member: String): List<FeedCard>? = if (memberId == member) cards else null

    fun keep(member: String, list: List<FeedCard>) {
        memberId = member
        cards = list
    }
}
