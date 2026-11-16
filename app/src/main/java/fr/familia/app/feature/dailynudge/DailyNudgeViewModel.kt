package fr.familia.app.feature.dailynudge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.data.MemberRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant

/** Reads only: it never writes, so « Voir comme » leaves no trace (PRD, récit 43). */
class DailyNudgeViewModel(
    goodNews: GoodNewsRepository?,
    members: MemberRepository?,
    currentMemberId: String,
    clock: () -> Instant = Instant::now,
) : ViewModel() {

    val nudge: StateFlow<DailyNudge?> = if (goodNews == null || members == null) {
        emptyFlow<DailyNudge?>()
    } else {
        combine(goodNews.observeLatest(RECENT), members.observeFamily()) { news, family ->
            family.members.firstOrNull { it.id == currentMemberId }?.let { pickDailyNudge(news, it, family, clock()) }
        }.catch { emit(null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private companion object {
        /** The family publishes a few a day: the last ones are enough to find three days' worth. */
        const val RECENT = 20
    }
}
