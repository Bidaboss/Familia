package fr.familia.app.feature.yearinreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.data.HelpRequestRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.data.WeeklyQuestionRepository
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.WeeklyAnswer
import fr.familia.app.core.model.WeeklyQuestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class YearInReviewUiState(val loading: Boolean = true, val pages: List<ReviewPage> = emptyList())

/** How many questions of the week are read to find the one of the year (one a week). */
private const val REVIEW_QUESTIONS = 60

/**
 * The story of [year] for [currentMemberId], read live from what they can already see. Nothing is
 * written and no task runs on the server. A part that cannot be read is left out, never the story.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class YearInReviewViewModel(
    members: MemberRepository?,
    goodNews: GoodNewsRepository?,
    events: EventRepository?,
    helps: HelpRequestRepository?,
    trips: TripRepository?,
    questions: WeeklyQuestionRepository?,
    currentMemberId: String,
    year: Int,
    today: LocalDate,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    val uiState: StateFlow<YearInReviewUiState> =
        if (members == null) {
            MutableStateFlow(YearInReviewUiState(loading = false, pages = listOf(ReviewPage.Intro(year), ReviewPage.Thanks)))
        } else {
            val start = LocalDate.of(year, 1, 1)
            val from = start.atStartOfDay(zone).toInstant()
            val to = start.plusYears(1).atStartOfDay(zone).toInstant()
            val zoneYear = { i: Instant -> i.atZone(zone).year }
            val ofYear = combine(
                goodNews?.observeBetween(from, to).orEmpty(),
                events?.observeBetween(start, start.withDayOfYear(start.lengthOfYear())).orEmpty(),
                helps?.observeMine(currentMemberId).orEmpty(),
                trips?.observeFollowedTrips(currentMemberId).orEmpty(),
            ) { n, e, h, t -> Parts(n, e, h, t) }
            val noQuestion = flowOf(emptyList<WeeklyQuestion>() to emptyMap<String, List<WeeklyAnswer>>())
            val withQuestion = if (questions == null) noQuestion else questions.observeLatest(REVIEW_QUESTIONS).safe().flatMapLatest { list ->
                val candidates = list.filter { zoneYear(it.publishedAt) == year && it.revealAt <= now && it.answerCount > 0 }
                val best = candidates.maxWithOrNull(compareBy<WeeklyQuestion> { it.answerCount }.thenByDescending { it.publishedAt })
                if (best == null) {
                    flowOf(list to emptyMap<String, List<WeeklyAnswer>>())
                } else {
                    questions.observeAnswers(best.id).safe().map { answers -> list to mapOf(best.id to answers) }
                }
            }
            combine(members.observeFamily(), ofYear, withQuestion) { family, parts, (qs, answers) ->
                val me = family.members.firstOrNull { it.id == currentMemberId }
                    ?: return@combine YearInReviewUiState(loading = false, pages = listOf(ReviewPage.Intro(year), ReviewPage.Thanks))
                val review = buildYearInReview(year, me, today, now, parts.news, parts.events, parts.helps, parts.trips, family, qs, answers, zone)
                YearInReviewUiState(loading = false, pages = reviewPages(review))
            }
                .catch { e -> if (e is CancellationException) throw e else emit(YearInReviewUiState(loading = false, pages = listOf(ReviewPage.Intro(year), ReviewPage.Thanks))) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YearInReviewUiState())
        }

    private data class Parts(
        val news: List<GoodNews>,
        val events: List<Event>,
        val helps: List<HelpRequest>,
        val trips: List<Trip>,
    )
}

/** A part that cannot be read (rules, offline) counts as empty. */
private fun <T> Flow<List<T>>.safe(): Flow<List<T>> = catch { e -> if (e is CancellationException) throw e else emit(emptyList()) }

private fun <T> Flow<List<T>>?.orEmpty(): Flow<List<T>> = this?.safe() ?: flowOf(emptyList())
