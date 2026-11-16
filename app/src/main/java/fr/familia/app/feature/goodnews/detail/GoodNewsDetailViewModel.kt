package fr.familia.app.feature.goodnews.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Reaction
import fr.familia.app.core.share.goodNewsPhotoAllowed
import fr.familia.app.feature.goodnews.goodNewsItem
import fr.familia.app.feature.goodnews.nextReaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/** One good news on its own page, opened from the feed, the home card or a notification. */
class GoodNewsDetailViewModel(
    private val goodNews: GoodNewsRepository?,
    members: MemberRepository?,
    private val currentMemberId: String,
    private val postId: String,
    clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val actionFailed = MutableStateFlow(false)

    private val source: Flow<GoodNewsDetailUiState> =
        if (goodNews == null || members == null) {
            flowOf(GoodNewsDetailUiState.Missing)
        } else {
            combine(goodNews.observePost(postId), members.observeFamily(), actionFailed) { news, family, failed ->
                val me = family.members.firstOrNull { it.id == currentMemberId }
                if (news == null || me == null) {
                    GoodNewsDetailUiState.Missing
                } else {
                    GoodNewsDetailUiState.Success(
                        goodNewsItem(news, me, family),
                        clock(),
                        failed,
                        birthdayPerson = news.birthdayOf?.let { id -> family.members.firstOrNull { it.id == id } },
                        photoShareable = goodNewsPhotoAllowed(news, currentMemberId, family),
                    )
                }
            }.catch { emit(GoodNewsDetailUiState.Missing) }
        }

    val uiState: StateFlow<GoodNewsDetailUiState> =
        source.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoodNewsDetailUiState.Loading)

    fun react(reaction: Reaction) {
        val current = (uiState.value as? GoodNewsDetailUiState.Success)?.item?.myReaction
        act { goodNews?.react(postId, currentMemberId, nextReaction(current, reaction)) }
    }

    fun remove() = act { goodNews?.remove(postId) }

    /** Hide it for me, or show it again: the page stays open either way. */
    fun setHidden(hidden: Boolean) = act { goodNews?.setHidden(postId, currentMemberId, hidden) }

    fun dismissError() {
        actionFailed.value = false
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() }.onFailure { actionFailed.value = true } }
    }
}
