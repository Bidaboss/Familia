package fr.familia.app.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.CapsuleRepository
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.Capsule
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.feature.recap.WeekRecap
import fr.familia.app.feature.yearago.MemoryAlbum
import fr.familia.app.feature.yearago.YearAgoMemory
import fr.familia.app.feature.yearago.yearAgoTitle
import fr.familia.app.feature.yearinreview.YEAR_IN_REVIEW_START
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/*
 * Se souvenir en un seul lieu (récit 71, DESIGN-28, maquette SF1): the capsule, « Il y a un an », « La
 * semaine de la famille » and « Notre année » come by themselves into the fil, as cards. Each source
 * follows the switch of its module; none writes anything.
 */

/** The capsules opened for me in the window, newest first: « La capsule de Jeanne s'est ouverte ». */
fun openedCapsules(capsules: List<Capsule>, since: Instant, limit: Int): List<Capsule> =
    capsules.filter { it.openedAt?.isBefore(since) == false }
        .sortedWith(compareByDescending<Capsule> { it.openedAt }.thenBy { it.id })
        .take(limit)

/** « Capsule » (pêche): the day a capsule opens, for its recipients only (observeOpenedFor). */
class CapsuleFeedSource(private val capsules: CapsuleRepository, private val meId: String) : FeedSource {
    override fun cards(window: FeedWindow): Flow<List<FeedCard>> =
        capsules.observeOpenedFor(meId)
            .map { list -> openedCapsules(list, window.since, window.limit).map(::CapsuleFeedCard) }
            .catch { emit(emptyList()) }
}

class CapsuleFeedCard(val capsule: Capsule) : FeedCard {
    override val key: String = "capsule:${capsule.id}"
    override val at: Instant = capsule.openedAt ?: Instant.EPOCH
    override val authorId: String = capsule.authorId

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val tone = FeedTone.Peach
        val open = { navigation.onOpenCapsule(capsule.id) }
        FeedCardShell(tone = tone, onOpen = open) {
            MemoryLine(
                label = stringResource(R.string.feed_label_capsule),
                tone = tone,
                text = stringResource(R.string.feed_capsule_opened, capsule.authorName),
                action = stringResource(R.string.feed_capsule_open),
                onAction = open,
            )
        }
    }
}

/**
 * « Il y a un an » (vert): the souvenir of the day the Accueil already picks (YearAgoViewModel), at
 * the start of [today]. [memory] is null on a day without one.
 */
class YearAgoFeedSource(private val memory: Flow<YearAgoMemory?>, private val today: LocalDate) : FeedSource {
    override fun cards(window: FeedWindow): Flow<List<FeedCard>> =
        memory.map { m -> listOfNotNull(m?.let { YearAgoFeedCard(it, today) }) }.catch { emit(emptyList()) }
}

class YearAgoFeedCard(val memory: YearAgoMemory, day: LocalDate) : FeedCard {
    override val key: String = "yearAgo:${memory.key}"
    override val at: Instant = day.atStartOfDay(FAMILY_ZONE).toInstant()
    override val authorId: String? = null

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val tone = FeedTone.Green
        val thumbnail: String?
        val title: String
        val detail: String
        val open: () -> Unit
        when (val m = memory) {
            is YearAgoMemory.News -> {
                thumbnail = m.news.photoIds.firstOrNull()?.let { m.news.thumbnailPath(it) }
                title = m.news.text
                detail = stringResource(R.string.year_ago_news, m.news.authorName)
                open = { navigation.onOpenGoodNews(m.news.id) }
            }
            is YearAgoMemory.Photo -> {
                thumbnail = m.photo.thumbnailPath
                title = m.album.title
                detail = stringResource(
                    if (m.album.kind == MemoryAlbum.Kind.Trip) R.string.year_ago_trip else R.string.year_ago_event,
                    m.photo.authorName,
                )
                open = { navigation.onOpenAlbumPhoto(m.album, m.photo.id) }
            }
        }
        FeedCardShell(tone = tone, onOpen = open) {
            FeedLabel(yearAgoTitle(memory.years), tone)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                thumbnail?.let { StorageImage(it, null, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))) }
                Column(Modifier.weight(1f)) {
                    if (title.isNotBlank()) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = open, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.feed_year_ago_again)) }
        }
    }
}

/**
 * « La semaine de la famille » (lilas), in place of the récap (récit 36): on Sundays only, the week
 * that ends, from the same parts as the récap screen. A week with nothing to tell makes no card.
 */
class WeekFeedSource(
    private val goodNews: GoodNewsRepository?,
    private val trips: TripRepository?,
    private val members: List<Member>,
    private val meId: String,
    private val today: LocalDate,
    private val now: () -> Instant = Instant::now,
) : FeedSource {
    override fun cards(window: FeedWindow): Flow<List<FeedCard>> {
        if (today.dayOfWeek != DayOfWeek.SUNDAY) return flowOf(emptyList())
        val news = goodNews?.observeLatest(WEEK_NEWS)?.catch { emit(emptyList()) } ?: flowOf(emptyList())
        val followed = trips?.observeFollowedTrips(meId)?.catch { emit(emptyList()) } ?: flowOf(emptyList())
        return combine(news, followed) { n, t ->
            val week = WeekRecap(
                photos = WeekRecap.photos(n, meId, now()),
                birthdays = WeekRecap.birthdays(members, today),
                challenge = null,
                trips = WeekRecap.tripsUnderWay(t, today),
            )
            if (week.isEmpty) emptyList() else listOf(WeekFeedCard(week, today))
        }
    }

    private companion object {
        /** As many good news as the récap reads for one week. */
        const val WEEK_NEWS = 60
    }
}

class WeekFeedCard(val week: WeekRecap, sunday: LocalDate) : FeedCard {
    override val key: String = "week:$sunday"
    override val at: Instant = sunday.atStartOfDay(FAMILY_ZONE).toInstant()
    override val authorId: String? = null

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val tone = FeedTone.Lilac
        FeedCardShell(tone = tone, onOpen = navigation.onOpenRecap) {
            FeedLabel(stringResource(R.string.feed_label_week), tone)
            val figures = listOfNotNull(
                week.photos.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.feed_week_photos, it, it) },
                week.birthdays.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.feed_week_birthdays, it, it) },
                week.trips.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.feed_week_trips, it, it) },
            )
            Text(figures.joinToString(" · "), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
            if (week.photos.isNotEmpty()) {
                Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    week.photos.forEach { photo ->
                        StorageImage(photo.thumbnailPath, null, Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
                    }
                    repeat(WeekRecap.PHOTOS - week.photos.size) { Box(Modifier.weight(1f)) }
                }
            }
            OutlinedButton(onClick = navigation.onOpenRecap, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.feed_week_again)) }
        }
    }
}

/** « Notre année » (vert), from 26/12 to 10/01: [year] when its card shows, else nothing. */
class YearInReviewFeedSource(private val year: Int?) : FeedSource {
    override fun cards(window: FeedWindow): Flow<List<FeedCard>> = flowOf(listOfNotNull(year?.let(::YearInReviewFeedCard)))
}

class YearInReviewFeedCard(val year: Int) : FeedCard {
    override val key: String = "yearInReview:$year"

    /** The day the year can be watched: it stays there, under its day, until the 10/01. */
    override val at: Instant = YEAR_IN_REVIEW_START.atYear(year).atStartOfDay(FAMILY_ZONE).toInstant()
    override val authorId: String? = null

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val tone = FeedTone.Green
        val open = { navigation.onOpenYear(year) }
        FeedCardShell(tone = tone, onOpen = open) {
            MemoryLine(
                label = stringResource(R.string.feed_label_year),
                tone = tone,
                text = stringResource(R.string.year_title, year),
                action = stringResource(R.string.feed_year_watch),
                onAction = open,
            )
        }
    }
}

/** The label of the card, its sentence, and its one button, as on the maquette SF1. */
@Composable
private fun MemoryLine(label: String, tone: FeedTone, text: String, action: String, onAction: () -> Unit) {
    FeedLabel(label, tone)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onAction, modifier = Modifier.heightIn(min = 56.dp)) { Text(action) }
    }
}
