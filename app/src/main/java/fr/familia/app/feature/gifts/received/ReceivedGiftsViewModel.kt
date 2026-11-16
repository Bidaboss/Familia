package fr.familia.app.feature.gifts.received

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.model.ReceivedGift
import fr.familia.app.core.model.wishesListId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface ReceivedGiftsUiState {
    data object Loading : ReceivedGiftsUiState

    data class Success(
        /** Newest occasion first, then by title. */
        val gifts: List<ReceivedGift>,
        val thankFailed: Boolean = false,
    ) : ReceivedGiftsUiState

    data object Error : ReceivedGiftsUiState
}

/**
 * « On m'a offert » (PRD, récit 10 bis): the wishes of the member that the family gave, with who
 * gave them, and a « Merci » for each. Only the member themselves sees it.
 */
class ReceivedGiftsViewModel(
    private val gifts: GiftRepository?,
    currentMemberId: String,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val listId = wishesListId(currentMemberId)
    private val failed = MutableStateFlow(false)

    /** Thanked on this phone, before the server answers: the button turns at once, even offline. */
    private val thanked = MutableStateFlow(emptySet<String>())

    val uiState: StateFlow<ReceivedGiftsUiState> = run {
        val repo = gifts ?: return@run MutableStateFlow<ReceivedGiftsUiState>(ReceivedGiftsUiState.Success(emptyList()))
        combine(repo.observeReceived(listId), thanked, failed) { all, mine, didFail ->
            val shown = all.map { if (it.id in mine) it.copy(thanked = true) else it }
                .sortedWith(compareByDescending<ReceivedGift> { it.year ?: 0 }.thenBy { it.title.lowercase() })
            ReceivedGiftsUiState.Success(shown, didFail) as ReceivedGiftsUiState
        }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(ReceivedGiftsUiState.Error)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceivedGiftsUiState.Loading)
    }

    fun thank(itemId: String) {
        val repo = gifts ?: return
        thanked.value += itemId
        failed.value = false
        viewModelScope.launch {
            val ok = try {
                // Still pending after the delay: queued offline, Firestore sends it later.
                withTimeoutOrNull(writeTimeoutMillis) { repo.thank(listId, itemId) }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (!ok) {
                thanked.value -= itemId
                failed.value = true
            }
        }
    }
}
