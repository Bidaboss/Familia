package fr.familia.app.feature.events.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.EventRepository
import fr.familia.app.core.data.EventSeriesRepository
import fr.familia.app.core.model.EventSeries
import fr.familia.app.core.model.RepeatChoice
import fr.familia.app.core.model.RepeatEnd
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
    /** « Événements qui reviennent » (récit 54); null when the app has no series at all. */
    private val series: EventSeriesRepository? = null,
    /** recurringEvents is on: « Se répète » shows. An event of a series is handled as one either way. */
    private val recurring: Boolean = false,
    private val today: () -> LocalDate = LocalDate::now,
    /** A new event's title already written (« Créer l'événement » of « À qui le tour ? », récit 58). */
    private val prefillTitle: String? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<EventEditUiState>(EventEditUiState.Loading)
    val uiState: StateFlow<EventEditUiState> = _uiState.asStateFlow()

    private var original: Event? = null
    private var originalSeries: EventSeries? = null
    private var me: Member? = null
    private var allowed: List<EventTarget> = emptyList()
    private var editedId = ""
    private var initialForm = EventForm()
    private var showErrors = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        // Stopped by Android while the write was leaving: it is in Firestore's queue, never sent twice.
        when (savedState?.get<String>(KEY_SENT)) {
            SENT_SAVED -> return run { _uiState.value = EventEditUiState.Done(Confirmation.Saved) }
            SENT_DELETED -> return run { _uiState.value = EventEditUiState.Done(Confirmation.Deleted) }
        }
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
        // A series gone (all deleted) leaves its past events as plain ones.
        val ofSeries = existing?.seriesId?.let { id ->
            try {
                series?.getSeries(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return unavailable()
            }
        }
        // Read live: rights follow the family as it is now, not as it was at sign-in.
        val current = family.members.firstOrNull { it.id == currentMemberId } ?: return unavailable()
        if (eventId != null && (existing == null || !canManage(existing, current, family))) return unavailable()

        me = current
        original = existing
        originalSeries = ofSeries?.takeIf { existing?.occurrence != null }
        editedId = existing?.id ?: savedState?.get<String>(KEY_ID) ?: eventRepo.newEventId()
        savedState?.set(KEY_ID, editedId)
        allowed = allowedTargets(current, family)
        // Editing keeps the current target even if this member could not have chosen it.
        val choices = (listOfNotNull(existing?.target).filter { it !in allowed } + allowed)
            .map { target ->
                val isMe = target is EventTarget.ForSelf && target.memberId == current.id
                TargetChoice(target, if (isMe) null else targetName(target, family))
            }
        val occurrenceOf = originalSeries
        initialForm = when {
            existing != null && occurrenceOf != null -> EventForm.from(existing, occurrenceOf)
            existing != null -> EventForm.from(existing)
            else -> EventForm(title = prefillTitle.orEmpty().take(MAX_TITLE), target = allowed.first())
        }
        val draft = savedState?.let { restoreDraft(it) }
            ?.takeIf { d -> d.target == null || choices.any { it.target == d.target } }
            // The end of a series is not in the draft: it stays the one loaded while the rhythm does.
            ?.let { d -> d.copy(end = if (d.repeat == initialForm.repeat) initialForm.end else RepeatEnd.Never) }
        val form = draft ?: initialForm
        _uiState.value = EventEditUiState.Editing(
            form = form,
            isNew = existing == null,
            targets = choices,
            errors = emptySet(),
            hasChanges = form != initialForm,
            showPhoto = photos != null,
            storedCover = existing?.coverPhoto,
            showRepeat = recurring && series != null && (existing == null || occurrenceOf != null),
            isOccurrence = occurrenceOf != null,
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

    /** Saves; [scope] only counts for an event of a series. */
    fun save(scope: SeriesScope = SeriesScope.ThisOnly) {
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
        val ofSeries = originalSeries
        val newRule = state.form.rule()
        if (ofSeries != null && scope == SeriesScope.ThisAndFollowing) return saveFollowing(state, ofSeries)
        if (original == null && newRule != null && state.showRepeat) {
            val seriesRepo = series ?: return
            _uiState.value = state.copy(isSaving = true, saveFailed = false)
            viewModelScope.launch {
                val created = state.form.toSeries(seriesRepo.newSeriesId(), author.id, author.firstName, newRule)
                savedState?.set(KEY_SENT, SENT_SAVED)
                val sent = write { seriesRepo.saveSeries(created, isNew = true) }
                if (sent != null) _uiState.value = EventEditUiState.Done(sent.or(Confirmation.Saved)) else markFailed()
            }
            return
        }
        val before = original?.coverPhoto
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            // The new cover goes first: the rules of the event only name a photo already sent.
            val cover = try {
                // A removed cover gives null, which must stay null: no « ?: before » here.
                photos?.prepare(before, state.form.photo) { SinglePhoto.eventCover(editedId, author.id) }
                    ?: before.takeIf { photos == null }
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
            ).copy(seriesId = original?.seriesId, occurrence = original?.occurrence)
            savedState?.set(KEY_SENT, SENT_SAVED)
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

    /**
     * Deletes the event, after the screen asked for confirmation. One of a series is taken out of it
     * (an exdate), or ends the series the day before ([SeriesScope.ThisAndFollowing]): a deleted
     * occurrence would otherwise be written again by the server.
     */
    fun delete(scope: SeriesScope = SeriesScope.ThisOnly) {
        val state = _uiState.value as? EventEditUiState.Editing ?: return
        val repo = events ?: return
        val event = original ?: return
        if (state.isSaving) return
        val ofSeries = originalSeries
        val day = event.occurrence
        val seriesRepo = series
        if (ofSeries != null && day != null && seriesRepo != null) {
            if (scope == SeriesScope.ThisOnly && ofSeries.exdates.size >= EventSeries.MAX_EXDATES) return markFailed()
            _uiState.value = state.copy(isSaving = true, saveFailed = false)
            viewModelScope.launch {
                savedState?.set(KEY_SENT, SENT_DELETED)
                val sent = write {
                    when {
                        scope == SeriesScope.ThisOnly ->
                            seriesRepo.saveSeries(ofSeries.copy(exdates = (ofSeries.exdates + day).distinct()), isNew = false)
                        ofSeries.rule.days(day.minusDays(1)).isEmpty() -> seriesRepo.deleteSeries(ofSeries.id)
                        else -> seriesRepo.saveSeries(ofSeries.endingBefore(day), isNew = false)
                    }
                }
                if (sent != null) _uiState.value = EventEditUiState.Done(sent.or(Confirmation.Deleted)) else markFailed()
            }
            return
        }
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            savedState?.set(KEY_SENT, SENT_DELETED)
            val sent = write { repo.deleteEvent(event.id) }
            if (sent != null) _uiState.value = EventEditUiState.Done(sent.or(Confirmation.Deleted)) else markFailed()
        }
    }

    /**
     * « Celle-ci et les suivantes »: the texts apply from this day on. A new rhythm or a new day keeps
     * the same series when nothing of it is still to come before this day; otherwise the series ends
     * the day before, and a new one starts here.
     */
    private fun saveFollowing(state: EventEditUiState.Editing, ofSeries: EventSeries) {
        val repo = series ?: return
        val author = me ?: return
        val event = original ?: return
        val day = event.occurrence ?: return
        val form = state.form
        val rule = form.rule() ?: return
        val sameRhythm = form.date == day && rule.copy(start = ofSeries.rule.start) == ofSeries.rule
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            savedState?.set(KEY_SENT, SENT_SAVED)
            val sent = write {
                when {
                    sameRhythm -> repo.saveSeries(
                        form.toSeries(ofSeries.id, ofSeries.authorId, ofSeries.authorName, ofSeries.rule, ofSeries).copy(applyFrom = day),
                        isNew = false,
                    )
                    ofSeries.comingBefore(day, today()).isEmpty() -> repo.saveSeries(
                        form.toSeries(ofSeries.id, ofSeries.authorId, ofSeries.authorName, rule, ofSeries).copy(applyFrom = null),
                        isNew = false,
                    )
                    else -> {
                        repo.saveSeries(form.toSeries(repo.newSeriesId(), author.id, author.firstName, rule), isNew = true)
                        repo.saveSeries(ofSeries.endingBefore(day), isNew = false)
                    }
                }
            }
            if (sent != null) _uiState.value = EventEditUiState.Done(sent.or(Confirmation.Saved)) else markFailed()
        }
    }

    private fun errorsFor(form: EventForm, state: EventEditUiState.Editing): Set<EventFormError> =
        if (showErrors) validate(form, state.targets.map { it.target }) else emptySet()

    private fun markFailed() {
        savedState?.remove<String>(KEY_SENT)
        val state = _uiState.value as? EventEditUiState.Editing ?: return
        _uiState.value = state.copy(isSaving = false, saveFailed = true)
    }

    /** How the write ended, null when the server refused it (see [sendWrite]). */
    private suspend fun write(block: suspend () -> Unit): WriteSent? = sendWrite(writeTimeoutMillis, block)

    private companion object {
        const val KEY_ID = "eventDraft.id"
        /** The write left (it waits in Firestore's queue): after Android stops the app, never twice. */
        const val KEY_SENT = "eventDraft.sent"
        const val SENT_SAVED = "saved"
        const val SENT_DELETED = "deleted"
        const val KEY_TITLE = "eventDraft.title"
        const val KEY_DATE = "eventDraft.date"
        const val KEY_TIME = "eventDraft.time"
        const val KEY_PLACE = "eventDraft.place"
        const val KEY_NOTE = "eventDraft.note"
        const val KEY_TARGET = "eventDraft.target"
        const val KEY_REPEAT = "eventDraft.repeat"

        fun saveDraft(handle: SavedStateHandle, form: EventForm) {
            handle[KEY_TITLE] = form.title
            handle[KEY_DATE] = form.date?.toString()
            handle[KEY_TIME] = form.time?.toString()
            handle[KEY_PLACE] = form.place
            handle[KEY_NOTE] = form.note
            handle[KEY_TARGET] = form.target?.let(::encodeTarget)
            handle[KEY_REPEAT] = form.repeat.name
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
                repeat = handle.get<String>(KEY_REPEAT)?.let { name -> RepeatChoice.entries.firstOrNull { it.name == name } }
                    ?: RepeatChoice.None,
            )
        }
    }
}

/** The series stopped on the day before [day]: what came before stays as it was. */
private fun EventSeries.endingBefore(day: LocalDate): EventSeries =
    copy(rule = rule.copy(until = day.minusDays(1), count = null))

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
