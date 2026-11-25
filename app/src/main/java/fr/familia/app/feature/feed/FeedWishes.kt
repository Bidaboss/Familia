package fr.familia.app.feature.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import fr.familia.app.R
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.data.gazetteThreadId
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.ui.formatTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/*
 * « Envies » de la gazette (récit 116, brief §4): one card per person and per day, « Léon a ajouté 2
 * envies ». Only the wishes the person shows (never an idea the others keep for them), never who
 * reserves, never to the person themselves (their thread, wishes:{id}, is closed to them).
 */

/** The wishes one person added on one day. */
data class WishesDay(
    val listId: String,
    val beneficiaryId: String,
    val day: LocalDate,
    val titles: List<String>,
    val at: Instant,
)

/**
 * The days of wishes of the window: each wish list of the others with its items, only the wishes
 * shown to their beneficiary (an idea is never one), added since [since], grouped by person and by day
 * of Paris, the newest [limit].
 */
fun wishesForFeed(lists: List<Pair<GiftList, List<GiftItem>>>, meId: String, since: Instant, limit: Int): List<WishesDay> =
    lists
        .filter { (list, _) -> list.kind == GiftListKind.Wishes && list.beneficiaryId != meId }
        .flatMap { (list, items) ->
            items
                .filter { it.visibleToBeneficiary && it.addedAt?.isBefore(since) == false }
                .groupBy { it.addedAt!!.atZone(FAMILY_ZONE).toLocalDate() }
                .map { (day, onDay) ->
                    val sorted = onDay.sortedBy { it.addedAt }
                    WishesDay(list.id, list.beneficiaryId, day, sorted.map { it.title }, sorted.last().addedAt!!)
                }
        }
        .sortedByDescending { it.at }
        .take(limit)

/** The wish lists I may read, and their items, under the switch of the Cadeaux. */
class WishesFeedSource(private val gifts: GiftRepository, private val meId: String) : FeedSource {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun cards(window: FeedWindow): Flow<List<FeedCard>> =
        gifts.observeVisibleLists(meId)
            .map { lists -> lists.filter { it.kind == GiftListKind.Wishes && it.beneficiaryId != meId } }
            .distinctUntilChanged()
            .flatMapLatest { lists ->
                if (lists.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(lists.map { list -> gifts.observeItems(list.id, hiddenFromMe = false).catch { emit(emptyList()) }.map { list to it } }) { pairs ->
                        wishesForFeed(pairs.toList(), meId, window.since, window.limit).map(::WishesFeedCard)
                    }
                }
            }
}

class WishesFeedCard(val wishes: WishesDay) : FeedCard {
    override val key: String = "wishes:${wishes.beneficiaryId}:${wishes.day}"
    override val at: Instant = wishes.at
    override val authorId: String = wishes.beneficiaryId

    /** J'aime and comments on its thread, never read by the person themselves (règles gazetteThreads). */
    override val social: FeedSocial = FeedSocial(gazetteThreadId(key), title = R.string.feed_detail_wishes)

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val tone = FeedTone.Peach
        val person = context.member(wishes.beneficiaryId)
        val name = person?.firstName ?: return
        FeedCardShell(
            tone = tone,
            onOpen = { navigation.onOpenGiftList(wishes.listId) },
            header = {
                FeedHeader(
                    member = person,
                    name = name,
                    time = formatTime(at.atZone(FAMILY_ZONE).toLocalTime()),
                    label = stringResource(R.string.feed_label_wishes),
                    tone = tone,
                )
            },
        ) {
            Column {
                Text(
                    pluralStringResource(R.plurals.feed_wishes_added, wishes.titles.size, name, wishes.titles.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    wishes.titles.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
