package fr.familia.app.feature.events.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.SinglePhoto
import fr.familia.app.core.photo.SinglePhotoSaver
import fr.familia.app.feature.events.allowedTargets
import fr.familia.app.feature.events.canManage
import fr.familia.app.feature.events.targetName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import fr.familia.app.core.ui.Confirmation
import fr.familia.app.core.ui.WriteSent
import fr.familia.app.core.ui.sendWrite
import java.time.LocalDate
import java.time.LocalTime

/**
 * Create an event ([eventId] null), or edit and delete one (PRD, récits 2 and 5). Offline, the write
 * lands in the cache at once and Firestore sends it when the network is back.
 */
class EventEditViewModel(
    private val events: EventRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val eventId: String?,
    private val writeTimeoutMillis: Long = 4_000,
    /** Keeps the draft if Android closes the app in the background (audit DESIGN du 15/11). */
    private val savedState: SavedStateHandle? = null,
    /** Sends and removes the cover (PRD, récit 44); null hides it, as when « Plus de photos » is off. */
    private val photos: SinglePhotoSaver? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<EventEditUiState>(EventEditUiState.Loading)
    val uiState: StateFlow<EventEditUiState> = _uiState.asStateFlow()

    private var original: Event? = null
    private var me: Member? = null
    private var allowed: List<EventTarget> = emptyList()
    private var editedId = ""
    private var initialForm = EventForm()
    private var showErrors = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val eventRepo = events ?: return
        val memberRepo = members ?: return
        val loaded = try {
            val family = memberRepo.observeFamily().first()
            family to eventId?.let { eventRepo.getEvent(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val family: Family = loaded?.first ?: return unavailable()
        val existing = loaded.second
        // Read live: rights follow the family as it is now, not as it was at sign-in.
        val current = family.members.firstOrNull { it.id == currentMemberId } ?: return unavailable()
        if (eventId != null && (existing == null || !canManage(existing, current, family))) return unavailable()

        me = current
        original = existing
        editedId = existing?.id ?: savedState?.get<String>(KEY_ID) ?: eventRepo.newEventId()
        savedState?.set(KEY_ID, editedId)
        allowed = allowedTargets(current, family)
        // Editing keeps the current target even if this member could not have chosen it.
        val choices = (listOfNotNull(existing?.target).filter { it !in allowed } + allowed)
            .map { target ->
                val isMe = target is EventTarget.ForSelf && target.memberId == current.id
                TargetChoice(target, if (isMe) null else targetName(target, family))
            }
        initialForm = existing?.let { EventForm.from(it) } ?: EventForm(target = allowed.first())
        val draft = savedState?.let { restoreDraft(it) }?.takeIf { d -> d.target == null || choices.any { it.target == d.target } }
        val form = draft ?: initialForm
        _uiState.value = EventEditUiState.Editing(
            form = form,
            isNew = existing == null,
            targets = choices,
            errors = emptySet(),
            hasChanges = form != initialForm,
            showPhoto = photos != null,
            storedCover = existing?.coverPhoto,
        )
    }

    private fun unavailable() {
        _uiState.value = EventEditUiState.Unavailable
    }

    fun update(change: (EventForm) -> EventForm) {
        val state = _uiState.value as? EventEditUiState.Editing ?: return
        val form = change(state.form)
        savedState?.let { saveDraft(it, form) }
        _uiState.value = state.copy(
            form = form,
            errors = errorsFor(form, state),
            hasChanges = form != initialForm,
            saveFailed = false,
        )
    }

    fun save() {
        val state = _uiState.value as? EventEditUiState.Editing ?: return
        val repo = events ?: return
        val author = me ?: return
        if (state.isSaving) return
        showErrors = true
        val errors = errorsFor(state.form, state)
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(errors = errors)
            return
        }
        val before = original?.coverPhoto
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            // The new cover goes first: the rules of the event only name a photo already sent.
            val cover = try {
                photos?.prepare(before, state.form.photo) { SinglePhoto.eventCover(editedId, author.id) } ?: before
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch markFailed()
            }
            val event = state.form.toEvent(
                id = editedId,
                authorId = original?.authorId ?: author.id,
                authorName = original?.authorName ?: author.firstName,
                coverPhoto = cover,
            )
            val sent = write { repo.saveEvent(event, isNew = original == null) }
            if (sent != null) {
                photos?.cleanUp(previous = before, saved = cover)
                _uiState.value = EventEditUiState.Done(sent.or(Confirmation.Saved))
            } else {
                photos?.cleanUp(previous = cover, saved = before)
                markFailed()
            }
        }
    }

    /** Deletes the event, after the screen asked for confirmation. */
    fun delete() {
        val state = _uiState.value as? EventEditUiState.Editing ?: return
        val repo = events ?: return
        val event = original ?: return
        if (state.isSaving) return
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            val sent = write { repo.deleteEvent(event.id) }
            if (sent != null) _uiState.value = EventEditUiState.Done(sent.or(Confirmation.Deleted)) else markFailed()
        }
    }

    private fun errorsFor(form: EventForm, state: EventEditUiState.Editing): Set<EventFormError> =
        if (showErrors) validate(form, state.targets.map { it.target }) else emptySet()

    private fun markFailed() {
        val state = _uiState.value as? EventEditUiState.Editing ?: return
        _uiState.value = state.copy(isSaving = false, saveFailed = true)
    }

    /** How the write ended, null when the server refused it (see [sendWrite]). */
    private suspend fun write(block: suspend () -> Unit): WriteSent? = sendWrite(writeTimeoutMillis, block)

    private companion object {
        const val KEY_ID = "eventDraft.id"
        const val KEY_TITLE = "eventDraft.title"
        const val KEY_DATE = "eventDraft.date"
        const val KEY_TIME = "eventDraft.time"
        const val KEY_PLACE = "eventDraft.place"
        const val KEY_NOTE = "eventDraft.note"
        const val KEY_TARGET = "eventDraft.target"

        fun saveDraft(handle: SavedStateHandle, form: EventForm) {
            handle[KEY_TITLE] = form.title
            handle[KEY_DATE] = form.date?.toString()
            handle[KEY_TIME] = form.time?.toString()
            handle[KEY_PLACE] = form.place
            handle[KEY_NOTE] = form.note
            handle[KEY_TARGET] = form.target?.let(::encodeTarget)
        }

        fun restoreDraft(handle: SavedStateHandle): EventForm? {
            val title = handle.get<String>(KEY_TITLE) ?: return null
            return EventForm(
                title = title,
                date = handle.get<String>(KEY_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                time = handle.get<String>(KEY_TIME)?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                place = handle.get<String>(KEY_PLACE).orEmpty(),
                note = handle.get<String>(KEY_NOTE).orEmpty(),
                target = handle.get<String>(KEY_TARGET)?.let(::decodeTarget),
            )
        }
    }
}

internal fun encodeTarget(target: EventTarget): String = when (target) {
    EventTarget.ForFamily -> "family"
    is EventTarget.ForHousehold -> "household:${target.householdId}"
    is EventTarget.ForSelf -> "self:${target.memberId}"
    is EventTarget.ForChild -> "child:${target.memberId}"
}

internal fun decodeTarget(value: String): EventTarget? {
    val (kind, id) = value.split(":", limit = 2).let { it[0] to it.getOrNull(1) }
    return when (kind) {
        "family" -> EventTarget.ForFamily
        "household" -> id?.let { EventTarget.ForHousehold(it) }
        "self" -> id?.let { EventTarget.ForSelf(it) }
        "child" -> id?.let { EventTarget.ForChild(it) }
        else -> null
    }
}
