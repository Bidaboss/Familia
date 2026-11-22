package fr.familia.app.feature.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.feature.yearago.MemoryAlbum
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/*
 * Le fil d'actualité de l'Accueil (PRD récit 67, maquette compacte F2 bis du 18/11).
 *
 * The phone puts the fil together from what the member can already read, module by module, with the
 * same queries and the same rights as each screen: no « fil » collection on the server. Each module
 * gives a [FeedSource]; its cards say when they happened ([FeedCard.at]) and draw themselves.
 *
 * To add a module (fils coups de main et voyage): write its source and its card in this package
 * (FeedXxx.kt), drawn with [FeedCardShell], and add one line to rememberFeedSources (FeedSources.kt),
 * under the switch of the module. Nothing of the Cadeaux, the surprises or the capsules, ever.
 */

/** One card of the fil. */
interface FeedCard {
    /** Unique across the modules, « module:id » (« event:e1 », « birthday:m3 »). */
    val key: String

    /** When it happened: the fil is newest first, cut by day. */
    val at: Instant

    /** Who it comes from, if anyone: my own cards never count in the « Le fil » badge. */
    val authorId: String?

    /**
     * Not null: pinned at the top of its day, the smallest rank first (the birthday of the day,
     * DESIGN-59, the youngest first). The next day it is simply a card of its day.
     */
    val pinRank: Int? get() = null

    @Composable
    fun Content(context: FeedContext)
}

/**
 * How far back the fil reads: each source takes its last [days] days, [limit] cards at most (PRD:
 * 14 days and 10 cards, then « Voir plus ancien » takes the next slice).
 */
data class FeedWindow(val pages: Int, val now: Instant) {
    val since: Instant get() = now.minus(Duration.ofDays(FEED_DAYS * pages))
    val limit: Int get() = FEED_LIMIT * pages

    companion object {
        const val FEED_DAYS = 14L
        const val FEED_LIMIT = 10
    }
}

/** The cards of one module, newest first, as the member may already read them elsewhere. */
fun interface FeedSource {
    fun cards(window: FeedWindow): Flow<List<FeedCard>>
}

/**
 * What every card knows. [readOnly] in « Voir comme »: a card reads,
 * it never writes (no « J'y vais », no comment).
 */
class FeedContext(
    val me: Member,
    val family: Family,
    val today: LocalDate,
    val readOnly: Boolean,
)

/** The member of [id] in the family, for an avatar; null once they left. */
fun FeedContext.member(id: String?): Member? = id?.let { wanted -> family.members.firstOrNull { it.id == wanted } }

/** Where the cards lead: set by the Accueil, from the navigation of the app. */
class FeedNavigation(
    val onOpenEvent: (String) -> Unit = {},
    val onOpenMember: (String) -> Unit = {},
    val onOpenGoodNews: (String) -> Unit = {},
    val onOpenHelpRequest: (String) -> Unit = {},
    val onOpenTrip: (String) -> Unit = {},
    /** The page of the word of a step: the trip, then the step (récit 88). */
    val onOpenWord: (String, String) -> Unit = { _, _ -> },
    val onOpenRecipe: (String) -> Unit = {},
    /** An album of an event or a trip, on one of its photos (fil voyage). */
    val onOpenAlbumPhoto: (MemoryAlbum, String) -> Unit = { _, _ -> },
    val onOpenWeeklyQuestion: () -> Unit = {},
    /** Se souvenir en un seul lieu (récit 71): a capsule opened, the récap of the week, « Notre année ». */
    val onOpenCapsule: (String) -> Unit = {},
    val onOpenRecap: () -> Unit = {},
    val onOpenYear: (Int) -> Unit = {},
)

val LocalFeedNavigation = staticCompositionLocalOf { FeedNavigation() }

/** « Le fil » of Tout Familia (récit 71): asks the Accueil to open on the fil, once. */
object FeedOpenRequest {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(false)
    val asked: kotlinx.coroutines.flow.StateFlow<Boolean> = state

    fun ask() {
        state.value = true
    }

    fun consume() {
        state.value = false
    }
}

/** Every source together: newest day first, its pinned cards on top, then newest first; a card given twice kept once. */
fun mergeFeed(lists: List<List<FeedCard>>): List<FeedCard> =
    lists.flatten().distinctBy { it.key }.sortedWith(
        compareByDescending<FeedCard> { feedDay(it) }
            .thenBy { it.pinRank ?: Int.MAX_VALUE }
            .thenByDescending { it.at }
            .thenBy { it.key },
    )

/** The day of Paris of a card: its separator. */
fun feedDay(card: FeedCard): LocalDate = card.at.atZone(FAMILY_ZONE).toLocalDate()

/** The fil cut by day, in its order. */
fun feedDays(cards: List<FeedCard>): List<Pair<LocalDate, List<FeedCard>>> =
    cards.groupBy(::feedDay).toList()

/** « Aujourd'hui », « Hier », « Lundi 16 novembre », with the year when it is not this one. */
fun feedDayLabel(date: LocalDate, today: LocalDate, todayLabel: String, yesterdayLabel: String): String = when (date) {
    today -> todayLabel
    today.minusDays(1) -> yesterdayLabel
    else -> frenchDate(date, if (date.year == today.year) "EEEE d MMMM" else "EEEE d MMMM yyyy")
        .replaceFirstChar { it.titlecase(Locale.FRANCE) }
}

/** The badge of « Le fil »: the cards of the others since I last looked. */
fun unseenCount(cards: List<FeedCard>, seenAt: Instant?, meId: String): Int =
    cards.count { it.authorId != meId && (seenAt == null || it.at.isAfter(seenAt)) }
