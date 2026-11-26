package fr.familia.app.feature.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import fr.familia.app.R
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.data.GoodDealRepository
import fr.familia.app.core.data.gazetteThreadId
import fr.familia.app.core.model.GoodDeal
import fr.familia.app.core.model.GoodDealKind
import fr.familia.app.core.ui.formatTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/*
 * « Bon plan » de la gazette (récit 116, brief §4): a bon plan or a bonne adresse shared in Entraide
 * comes by itself, « titre, lieu, avis », and opens its page.
 */

/** The bons plans and bonnes adresses shared since [since], newest first, [limit] at most. */
fun dealsForFeed(deals: List<GoodDeal>, since: Instant, limit: Int): List<GoodDeal> = deals
    .filter { it.createdAt?.isBefore(since) == false }
    .sortedByDescending { it.createdAt }
    .take(limit)

class DealFeedSource(private val deals: GoodDealRepository) : FeedSource {
    override fun cards(window: FeedWindow): Flow<List<FeedCard>> =
        deals.observeAll().map { list -> dealsForFeed(list, window.since, window.limit).map(::DealFeedCard) }
}

class DealFeedCard(val deal: GoodDeal) : FeedCard {
    override val key: String = "deal:${deal.id}"
    override val at: Instant = deal.createdAt ?: Instant.EPOCH
    override val authorId: String = deal.authorId
    override val social: FeedSocial = FeedSocial(gazetteThreadId(key), title = R.string.feed_detail_deal)

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val tone = FeedTone.Lilac
        val address = deal.kind == GoodDealKind.Address
        FeedCardShell(
            tone = tone,
            onOpen = { navigation.onOpenGoodDeal(deal.id) },
            header = {
                FeedHeader(
                    member = context.member(deal.authorId),
                    name = deal.authorName,
                    action = stringResource(if (address) R.string.feed_action_address else R.string.feed_action_deal),
                    emoji = if (address) "📍" else "🏷️",
                    time = formatTime(at.atZone(FAMILY_ZONE).toLocalTime()),
                )
            },
        ) {
            Column {
                Text(deal.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                deal.place?.takeIf { it.isNotBlank() }?.let {
                    Text("📍 $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                deal.text.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
