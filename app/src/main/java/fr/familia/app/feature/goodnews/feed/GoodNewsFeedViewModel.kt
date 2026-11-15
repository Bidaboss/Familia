package fr.familia.app.feature.goodnews.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.GoodNews
import fr.familia.app.core.model.Reaction
import fr.familia.app.feature.goodnews.goodNewsItem
import fr.familia.app.feature.goodnews.nextReaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/** The feed of the « Bonnes nouvelles » (PRD, récit 32). */
@OptIn(ExperimentalCoroutinesApi::class)
class GoodNewsFeedViewModel(
    private val goodNews: GoodNewsRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val pages = MutableStateFlow(1)
    private val retries = MutableStateFlow(0)
    private val actionFailed = MutableStateFlow(false)

    val uiState: StateFlow<GoodNewsFeedUiState> = combine(retries, pages) { _, p -> p }
        .flatMapLatest { load(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoodNewsFeedUiState.Loading)

    private fun load(pageCount: Int): Flow<GoodNewsFeedUiState> {
        val newsRepo = goodNews ?: return flowOf(GoodNewsFeedUiState.Error)
        val memberRepo = members ?: return flowOf(GoodNewsFeedUiState.Error)
        val count = pageCount * GoodNews.PAGE_SIZE
        return combine(newsRepo.observeLatest(count), memberRepo.observeFamily(), actionFailed) { list, family, failed ->
            val me = family.members.firstOrNull { it.id == currentMemberId }
                ?: return@combine GoodNewsFeedUiState.Error
            GoodNewsFeedUiState.Success(
                items = list.filterNot { it.isHiddenFor(currentMemberId) }.map { goodNewsItem(it, me, family) },
                canLoadMore = list.size >= count,
                now = clock(),
                actionFailed = failed,
            )
        }.catch { emit(GoodNewsFeedUiState.Error) }
    }

    fun loadMore() {
        pages.value++
    }

    fun retry() {
        retries.value++
    }

    /** Touching one's own reaction again takes it back. */
    fun react(postId: String, reaction: Reaction) {
        val state = uiState.value as? GoodNewsFeedUiState.Success ?: return
        val current = state.items.firstOrNull { it.news.id == postId }?.myReaction
        act { goodNews?.react(postId, currentMemberId, nextReaction(current, reaction)) }
    }

    fun remove(postId: String) = act { goodNews?.remove(postId) }

    /** « Masquer pour moi »: it leaves this feed at once, the others still see it. */
    fun hide(postId: String) = act { goodNews?.setHidden(postId, currentMemberId, hidden = true) }

    fun dismissError() {
        actionFailed.value = false
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { actionFailed.value = true }
        }
    }
}
