package fr.familia.app.feature.goodnews.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.feature.goodnews.GoodNewsItem
import fr.familia.app.feature.goodnews.goodNewsItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant

sealed interface GoodNewsHomeUiState {
    data object Loading : GoodNewsHomeUiState

    /** The last 3 of the last 14 days (récit 32); empty means a call to publish. */
    data class Ready(val items: List<GoodNewsItem>, val now: Instant) : GoodNewsHomeUiState
}

/** The « Bonnes nouvelles » card of the home screen. */
class GoodNewsHomeViewModel(
    goodNews: GoodNewsRepository?,
    members: MemberRepository?,
    currentMemberId: String,
    clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val source: Flow<GoodNewsHomeUiState> =
        if (goodNews == null || members == null) {
            flowOf(GoodNewsHomeUiState.Ready(emptyList(), clock()))
        } else {
            // A few more than shown, so the posts one hid do not leave the card empty.
            combine(goodNews.observeLatest(READ), members.observeFamily()) { list, family ->
                val now = clock()
                val me = family.members.firstOrNull { it.id == currentMemberId }
                val recent = list
                    .filter { it.createdAt == null || it.createdAt.isAfter(now.minus(WINDOW)) }
                    .filterNot { it.isHiddenFor(currentMemberId) }
                    .take(SHOWN)
                GoodNewsHomeUiState.Ready(if (me == null) emptyList() else recent.map { goodNewsItem(it, me, family) }, now)
            }.catch { emit(GoodNewsHomeUiState.Ready(emptyList(), clock())) }
        }

    val uiState: StateFlow<GoodNewsHomeUiState> =
        source.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoodNewsHomeUiState.Loading)

    private companion object {
        const val SHOWN = 3
        const val READ = 10
        val WINDOW: Duration = Duration.ofDays(14)
    }
}
