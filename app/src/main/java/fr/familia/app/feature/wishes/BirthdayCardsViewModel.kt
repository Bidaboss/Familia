package fr.familia.app.feature.wishes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.BirthdayCardRepository
import fr.familia.app.core.data.GoodNewsRepository
import fr.familia.app.core.model.BirthdayCard
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.nextBirthday
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The cards of vœux on a member's page (récit 36, avis EXPERT n° 11): those of their last birthday,
 * and on the day, the card I write. No count, nobody named for not writing, no reminder.
 */
data class BirthdayCardsState(
    val cards: List<BirthdayCard> = emptyList(),
    /** The day, on someone else's page, outside « Voir comme »: I may write or change my card. */
    val canWrite: Boolean = false,
    val mine: BirthdayCard? = null,
    val saving: Boolean = false,
    val failed: Boolean = false,
) {
    val isEmpty: Boolean get() = cards.isEmpty() && !canWrite
}

object BirthdayWishes {
    /** The year of the last birthday of someone born on [birthDate], today included; null without a date. */
    fun lastBirthdayYear(birthDate: LocalDate?, today: LocalDate): Int? {
        if (birthDate == null || !birthDate.isBefore(today)) return null
        val next = nextBirthday(birthDate, today)
        return if (next == today) today.year else next.year - 1
    }

    /** True on the day: the Bonne nouvelle of Familia was published less than 24 h ago (the rules say the same). */
    fun isOpen(newsCreatedAt: Instant?, now: Instant): Boolean =
        newsCreatedAt != null && newsCreatedAt.isAfter(now.minus(Duration.ofHours(24)))
}

class BirthdayCardsViewModel(
    private val cards: BirthdayCardRepository?,
    goodNews: GoodNewsRepository?,
    private val me: Member,
    private val person: Member,
    today: LocalDate,
    /** False in « Voir comme ». */
    mayWrite: Boolean = true,
    /** The year of the birthday shown; the last one when null (on the member's page). */
    birthdayYear: Int? = null,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {
    private val year = birthdayYear ?: BirthdayWishes.lastBirthdayYear(person.birthDate, today)
    private val saving = MutableStateFlow(false)
    private val failed = MutableStateFlow(false)

    private fun <T> Flow<T>.orElse(value: T): Flow<T> = catch { e -> if (e is CancellationException) throw e else emit(value) }

    private val list: Flow<List<BirthdayCard>> =
        if (cards == null || year == null) flowOf(emptyList()) else cards.observeFor(person.id, year).orElse(emptyList())

    private val open: Flow<Boolean> =
        if (goodNews == null || year != today.year || !mayWrite || me.id == person.id) {
            flowOf(false)
        } else {
            goodNews.observePost(BirthdayCard.newsId(year, person.id)).map { BirthdayWishes.isOpen(it?.createdAt, clock()) }.orElse(false)
        }

    val uiState: StateFlow<BirthdayCardsState> = combine(list, open, saving, failed) { l, o, s, f ->
        BirthdayCardsState(cards = l, canWrite = o, mine = l.firstOrNull { it.authorId == me.id }, saving = s, failed = f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BirthdayCardsState())

    /** Sends my card, or changes it. */
    fun send(text: String) {
        val repo = cards ?: return
        val y = year ?: return
        val state = uiState.value
        val word = text.trim()
        if (!state.canWrite || word.isEmpty() || word.length > BirthdayCard.TEXT_MAX || state.saving) return
        write { repo.save(BirthdayCard(person.id, me.id, me.firstName, word, y), isNew = state.mine == null) }
    }

    fun remove() {
        val repo = cards ?: return
        val mine = uiState.value.mine ?: return
        write { repo.remove(mine) }
    }

    private fun write(block: suspend () -> Unit) {
        saving.value = true
        failed.value = false
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed.value = true
            } finally {
                saving.value = false
            }
        }
    }
}
