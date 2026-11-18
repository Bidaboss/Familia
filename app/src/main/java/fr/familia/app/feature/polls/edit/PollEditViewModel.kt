package fr.familia.app.feature.polls.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.PollRepository
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Poll
import fr.familia.app.core.model.concerns
import fr.familia.app.core.model.pollViewers
import fr.familia.app.feature.polls.canAskPoll
import fr.familia.app.feature.polls.closesAtFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime

/**
 * « Nouveau sondage » (PRD, récits 2 bis et 2 ter): a date poll or a free poll, asked to the whole
 * family by default. Offline, the write lands in the cache at once and Firestore sends it when the
 * network is back.
 */
class PollEditViewModel(
    private val polls: PollRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val today: () -> LocalDate = LocalDate::now,
    private val writeTimeoutMillis: Long = 4_000,
    /** Asked from the page of this event (récit 2 quater): its guests are ticked, and the poll is linked to it. */
    private val eventId: String? = null,
    private val events: EventRepository? = null,
    /** Keeps what is typed when Android stops the app in the background (appareil photo, WhatsApp…). */
    private val savedState: SavedStateHandle? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PollEditUiState>(PollEditUiState.Loading)
    val uiState: StateFlow<PollEditUiState> = _uiState.asStateFlow()

    private var family: Family? = null
    private var me: Member? = null
    private var initialForm = PollForm()
    private var showErrors = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        // Stopped by Android while the poll was leaving: it is in Firestore's queue, never asked twice.
        if (savedState?.get<Boolean>(KEY_SENT) == true) {
            _uiState.value = PollEditUiState.Done
            return
        }
        val memberRepo = members ?: return unavailable()
        val loaded = try {
            memberRepo.observeFamily().first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return unavailable()
        val current = loaded.members.firstOrNull { it.id == currentMemberId } ?: return unavailable()
        if (!canAskPoll(current)) return unavailable()
        family = loaded
        me = current
        val everyone = loaded.members.map { it.id }.toSet()
        val event = loadEvent()
        initialForm = PollForm(askedIds = eventGuests(loaded, event) ?: everyone)
        // My household first, then the others; in each, the adults then the children.
        val households = loaded.households.sortedWith(compareBy({ it.id != current.householdId }, { it.name.lowercase() }))
        val groups = households.mapNotNull { household ->
            loaded.members
                .filter { it.householdId == household.id }
                .sortedWith(compareBy({ it.profile != MemberProfile.Adult }, { it.firstName.lowercase() }))
                .takeIf { it.isNotEmpty() }
                ?.let { AskGroup(household.name, it) }
        }
        val form = savedState?.let { restoreDraft(it) }?.let { draft -> draft.copy(askedIds = draft.askedIds.filter { it in everyone }.toSet()) } ?: initialForm
        _uiState.value = PollEditUiState.Editing(
            form = form,
            hasChanges = form != initialForm,
            groups = groups,
            shortcuts = listOf(AskShortcut(null, everyone)) + groups.map { g -> AskShortcut(g.name, g.members.map { it.id }.toSet()) },
            forEvent = event?.let { PollEvent(it.title, it.date) },
        )
    }

    /** The event the poll is asked from, or null outside an event or when it cannot be read. */
    private suspend fun loadEvent(): Event? {
        val id = eventId ?: return null
        return try {
            events?.getEvent(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Those the event concerns, or null outside an event; an event gone meanwhile ticks nobody. */
    private fun eventGuests(family: Family, event: Event?): Set<String>? {
        if (eventId == null) return null
        event ?: return emptySet()
        return family.members.filter { concerns(event.target, it, family) }.map { it.id }.toSet()
    }

    /** From an event: [motive] chosen, with its [question] written in (still to change). */
    fun chooseMotive(motive: PollMotive, question: String) = update { it.withMotive(motive, question) }

    private fun unavailable() {
        _uiState.value = PollEditUiState.Unavailable
    }

    fun update(change: (PollForm) -> PollForm) {
        val state = _uiState.value as? PollEditUiState.Editing ?: return
        val form = change(state.form)
        savedState?.let { saveDraft(it, form) }
        _uiState.value = state.copy(
            form = form,
            errors = if (showErrors) validate(form, today()) else emptySet(),
            hasChanges = form != initialForm,
            saveFailed = false,
        )
    }

    /** The members asked, as the picker by household gives them. */
    fun setAsked(ids: Set<String>) = update { form -> form.copy(askedIds = ids) }

    fun toggle(memberId: String) = update { form ->
        form.copy(askedIds = if (memberId in form.askedIds) form.askedIds - memberId else form.askedIds + memberId)
    }

    /** A shortcut ticks only its group; tapped again when it is the only one ticked, it unticks it. */
    fun applyShortcut(shortcut: AskShortcut) = update { form ->
        form.copy(askedIds = if (form.askedIds == shortcut.memberIds) emptySet() else shortcut.memberIds)
    }

    fun addOption() = update { form ->
        if (!form.canAddOption) {
            form
        } else {
            when (form.kind) {
                Poll.Kind.Date -> form.copy(dates = form.dates + DateChoice())
                Poll.Kind.Choice -> form.copy(choices = form.choices + "")
            }
        }
    }

    fun removeOption(index: Int) = update { form ->
        if (!form.canRemoveOption) {
            form
        } else {
            when (form.kind) {
                Poll.Kind.Date -> form.copy(dates = form.dates.filterIndexed { i, _ -> i != index })
                Poll.Kind.Choice -> form.copy(choices = form.choices.filterIndexed { i, _ -> i != index })
            }
        }
    }

    fun save() {
        val state = _uiState.value as? PollEditUiState.Editing ?: return
        val repo = polls ?: return
        val author = me ?: return
        val members = family?.members ?: return
        if (state.isSaving) return
        showErrors = true
        val form = state.form
        val errors = validate(form, today())
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(errors = errors)
            return
        }
        // Only members still in the family are asked, in the family's order.
        val asked = members.map { it.id }.filter { it in form.askedIds }
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        savedState?.set(KEY_SENT, true)
        viewModelScope.launch {
            val sent = try {
                withTimeoutOrNull(writeTimeoutMillis) {
                    repo.create(
                        kind = form.kind,
                        question = form.question.trim(),
                        options = form.options(),
                        multiple = form.kind == Poll.Kind.Choice && form.multiple,
                        askedIds = asked,
                        viewerIds = pollViewers(author.id, asked, members),
                        closesAt = form.closesOn?.let(::closesAtFor),
                        authorId = author.id,
                        authorName = author.firstName,
                        eventId = eventId,
                    )
                }
                // Still pending after the timeout: queued offline, sent when the network is back.
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (sent) {
                _uiState.value = PollEditUiState.Done
            } else {
                savedState?.set(KEY_SENT, false)
                val failed = _uiState.value as? PollEditUiState.Editing ?: return@launch
                _uiState.value = failed.copy(isSaving = false, saveFailed = true)
            }
        }
    }
}

private const val KEY_SENT = "pollDraft.sent"
private const val KEY_DRAFT = "pollDraft."

/** The form as plain values, so the SavedStateHandle can keep it. */
internal fun saveDraft(handle: SavedStateHandle, form: PollForm) {
    handle[KEY_DRAFT + "kind"] = form.kind.key
    handle[KEY_DRAFT + "question"] = form.question
    handle[KEY_DRAFT + "dates"] = ArrayList(form.dates.map { "${it.day ?: ""}|${it.time ?: ""}" })
    handle[KEY_DRAFT + "choices"] = ArrayList(form.choices)
    handle[KEY_DRAFT + "multiple"] = form.multiple
    handle[KEY_DRAFT + "asked"] = ArrayList(form.askedIds)
    handle[KEY_DRAFT + "closesOn"] = form.closesOn?.toString()
    handle[KEY_DRAFT + "motive"] = form.motive?.name
}

/** The form kept before Android stopped the app, or null when nothing was typed yet. */
internal fun restoreDraft(handle: SavedStateHandle): PollForm? {
    val question = handle.get<String>(KEY_DRAFT + "question") ?: return null
    val day = { text: String -> text.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
    val time = { text: String -> text.takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() } }
    val defaults = PollForm()
    return PollForm(
        kind = Poll.Kind.entries.firstOrNull { it.key == handle.get<String>(KEY_DRAFT + "kind") } ?: defaults.kind,
        question = question,
        dates = handle.get<ArrayList<String>>(KEY_DRAFT + "dates")
            ?.map { DateChoice(day(it.substringBefore('|')), time(it.substringAfter('|', ""))) }
            ?.takeIf { it.isNotEmpty() } ?: defaults.dates,
        choices = handle.get<ArrayList<String>>(KEY_DRAFT + "choices")?.takeIf { it.isNotEmpty() } ?: defaults.choices,
        multiple = handle.get<Boolean>(KEY_DRAFT + "multiple") ?: false,
        askedIds = handle.get<ArrayList<String>>(KEY_DRAFT + "asked").orEmpty().toSet(),
        closesOn = handle.get<String>(KEY_DRAFT + "closesOn")?.let(day),
        motive = PollMotive.entries.firstOrNull { it.name == handle.get<String>(KEY_DRAFT + "motive") },
    )
}
