package fr.familia.app.feature.thinkofme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MomentRepository
import fr.familia.app.core.data.WRITE_WAIT_MILLIS
import fr.familia.app.core.data.writeOrQueue
import fr.familia.app.core.model.Moment
import fr.familia.app.core.model.MomentCheer
import fr.familia.app.core.model.Member
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
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/**
 * « Pensez à moi » on a member's page (PRD, récit 43). The member, or a responsible adult, notes
 * the moments to come; on the day, the family writes an encouragement. No count, nobody named for
 * not writing, no reminder.
 */
data class ThinkOfMeState(
    /** The moment of the day, once Familia has started it. */
    val today: Moment? = null,
    val cheers: List<MomentCheer> = emptyList(),
    /** On someone else's page, outside « Voir comme », on the day. */
    val canCheer: Boolean = false,
    val mine: MomentCheer? = null,
    /** The member themselves or one of their responsible adults: they see and note the moments to come. */
    val canManage: Boolean = false,
    val upcoming: List<Moment> = emptyList(),
    /** I am the member, and accept or refuse what a parent noted. */
    val canAccept: Boolean = false,
    val saving: Boolean = false,
    val failed: Boolean = false,
) {
    val isEmpty: Boolean get() = today == null && !canManage
}

@OptIn(ExperimentalCoroutinesApi::class)
class ThinkOfMeViewModel(
    private val moments: MomentRepository?,
    private val me: Member,
    private val person: Member,
    private val today: LocalDate,
    /** False in « Voir comme ». */
    private val mayWrite: Boolean = true,
    /** Who reads with their own account: [me], or the admin principal in « Voir comme ». */
    readerId: String = me.id,
    private val clock: () -> Instant = Instant::now,
    private val writeTimeoutMillis: Long = WRITE_WAIT_MILLIS,
) : ViewModel() {
    private val saving = MutableStateFlow(false)
    private val failed = MutableStateFlow(false)
    private val isSelf = me.id == person.id
    private val isResponsible = isSelf || me.id in person.parentIds
    private val canManage = mayWrite && isResponsible

    private fun <T> Flow<T>.orElse(value: T): Flow<T> = catch { e -> if (e is CancellationException) throw e else emit(value) }

    // The rules let only the person and their parents read a pending moment: ask for them only
    // when the account that reads may, or the whole list would be refused.
    private val readerMaySeePending = readerId == person.id || readerId in person.parentIds

    private val list: Flow<List<Moment>> = moments?.observeFor(person.id, withPending = readerMaySeePending)?.orElse(emptyList()) ?: flowOf(emptyList())

    private val openMoment: Flow<Moment?> = list.map { all -> all.firstOrNull { it.isOpen(clock()) } }

    private val cheers: Flow<List<MomentCheer>> = openMoment.flatMapLatest { m ->
        if (m == null || moments == null) flowOf(emptyList()) else moments.observeCheers(m.id).orElse(emptyList())
    }

    val uiState: StateFlow<ThinkOfMeState> = combine(list, openMoment, cheers, saving, failed) { all, open, c, s, f ->
        ThinkOfMeState(
            today = open,
            cheers = c,
            canCheer = open != null && mayWrite && !isSelf,
            mine = c.firstOrNull { it.authorId == me.id },
            canManage = canManage,
            upcoming = if (canManage) all.filter { !it.day.isBefore(today) && it.id != open?.id } else emptyList(),
            canAccept = mayWrite && isSelf,
            saving = s,
            failed = f,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThinkOfMeState())

    /** A parent noting it for a teenager of 15 or more: it waits for them to accept. */
    val notedForTeen: Boolean get() = !isSelf && Moment.isTeenOrOlder(person, today)

    fun note(title: String, day: LocalDate) {
        val repo = moments ?: return
        val t = title.trim()
        if (!canManage || t.isEmpty() || t.length > Moment.TITLE_MAX || day.isBefore(today)) return
        write {
            repo.add(Moment("", person.id, me.id, me.firstName, t, day, pending = notedForTeen))
        }
    }

    fun accept(moment: Moment) {
        val repo = moments ?: return
        if (!isSelf || !moment.pending) return
        write { repo.accept(moment.id) }
    }

    fun remove(moment: Moment) {
        val repo = moments ?: return
        if (!canManage) return
        write { repo.remove(moment.id) }
    }

    fun cheer(text: String) {
        val repo = moments ?: return
        val state = uiState.value
        val open = state.today ?: return
        val word = text.trim()
        if (!state.canCheer || state.mine != null || word.isEmpty() || word.length > MomentCheer.TEXT_MAX) return
        write { repo.cheer(MomentCheer(open.id, person.id, me.id, me.firstName, word)) }
    }

    fun removeCheer() {
        val repo = moments ?: return
        val mine = uiState.value.mine ?: return
        write { repo.removeCheer(mine) }
    }

    private fun write(block: suspend () -> Unit) {
        if (saving.value) return
        saving.value = true
        failed.value = false
        viewModelScope.launch {
            // Offline the write waits for the network and shows at once: the buttons come back after
            // a few seconds rather than staying « en cours » until the network returns.
            val ok = writeOrQueue(viewModelScope, writeTimeoutMillis, onLateFailure = { failed.value = true }) { block() }
            if (!ok) failed.value = true
            saving.value = false
        }
    }
}
