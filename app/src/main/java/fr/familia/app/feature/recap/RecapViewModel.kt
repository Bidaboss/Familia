package fr.familia.app.feature.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.TreeRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MonthChallenge
import fr.familia.app.core.model.Trip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

sealed interface RecapUiState {
    data object Loading : RecapUiState
    data class Ready(val recap: WeekRecap, val today: LocalDate) : RecapUiState
}

/** Which parts of the week show: each follows its own module. */
data class RecapParts(val goodNews: Boolean, val trips: Boolean, val tree: Boolean)

/**
 * « La semaine de la famille » (récit 36). Each part is read on its own: one that fails or whose
 * module is off is left out, and the rest still shows.
 */
class RecapViewModel(
    goodNews: GoodNewsRepository?,
    members: MemberRepository?,
    trips: TripRepository?,
    tree: TreeRepository?,
    memberId: String,
    parts: RecapParts,
    today: LocalDate,
    clock: () -> Instant = Instant::now,
) : ViewModel() {

    private fun <T> Flow<T>.orElse(value: T): Flow<T> = catch { e -> if (e is CancellationException) throw e else emit(value) }

    private val news: Flow<List<GoodNews>> =
        if (parts.goodNews && goodNews != null) goodNews.observeLatest(READ).orElse(emptyList()) else flowOf(emptyList())
    private val family: Flow<List<Member>> =
        members?.observeFamily()?.map { it.members }?.orElse(emptyList()) ?: flowOf(emptyList())
    private val followed: Flow<List<Trip>> =
        if (parts.trips && trips != null) trips.observeFollowedTrips(memberId).orElse(emptyList()) else flowOf(emptyList())
    private val challenge: Flow<MonthChallenge?> =
        if (parts.tree && tree != null) tree.observeChallenge(YearMonth.from(today)).orElse(null) else flowOf(null)

    val uiState: StateFlow<RecapUiState> = combine(news, family, followed, challenge) { n, f, t, c ->
        RecapUiState.Ready(
            WeekRecap(
                photos = WeekRecap.photos(n, memberId, clock()),
                birthdays = WeekRecap.birthdays(f, today),
                challenge = c,
                trips = WeekRecap.tripsUnderWay(t, today),
            ),
            today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapUiState.Loading)

    private companion object {
        /** More than a week of good news, in a family of 13. */
        const val READ = 40
    }
}
