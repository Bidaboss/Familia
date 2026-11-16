package fr.familia.app.feature.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.UsageRepository
import fr.familia.app.core.model.isOwner
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * « Utilisation » (PRD, récit 1 nonies): read once when the screen opens, and again on « Réessayer ».
 * The admin principal sees everyone; any other adult sees their children's totals (carte EXPERT-5).
 */
class UsageViewModel(
    private val members: MemberRepository?,
    private val usage: UsageRepository?,
    private val goodNews: GoodNewsRepository?,
    private val currentMemberId: String,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UsageUiState>(UsageUiState.Loading)
    val uiState: StateFlow<UsageUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        _uiState.value = UsageUiState.Loading
        load()
    }

    private fun load() {
        val memberRepo = members
        val usageRepo = usage
        if (memberRepo == null || usageRepo == null) {
            _uiState.value = UsageUiState.Error
            return
        }
        viewModelScope.launch {
            _uiState.value = try {
                val day = today()
                val family = memberRepo.observeFamily().first()
                val me = family.members.firstOrNull { it.id == currentMemberId } ?: error("Not in the family")
                val full = family.isOwner(me)
                val shown = if (full) UsageReport.counted(family.members) else UsageReport.childrenOf(family.members, currentMemberId)
                coroutineScope {
                    val days = shown.map { m -> async { usageRepo.fetchDays(m.id, UsageReport.since(day)) } }.awaitAll().flatten()
                    val recentIds = days.map { it.memberId }.toSet()
                    val older = if (full) {
                        shown.filter { it.id !in recentIds }.map { m -> async { m.id to usageRepo.fetchLastDay(m.id) } }.awaitAll().toMap()
                    } else {
                        emptyMap()
                    }
                    // The good news are only a part of the page: without them, the rest still shows.
                    val news = if (full) {
                        try {
                            withTimeoutOrNull(NEWS_TIMEOUT_MILLIS) { goodNews?.observeLatest(NEWS_READ)?.first() }.orEmpty()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                    UsageReport.build(shown, days, older, news, day, full)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UsageUiState.Error
            }
        }
    }

    private companion object {
        /** More than four weeks of good news for 13 people. */
        const val NEWS_READ = 200
        const val NEWS_TIMEOUT_MILLIS = 10_000L
    }
}
