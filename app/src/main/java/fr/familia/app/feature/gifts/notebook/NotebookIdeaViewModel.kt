package fr.familia.app.feature.gifts.notebook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.LinkPreviewRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.NotebookRepository
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftStatus
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.NotebookEntry
import fr.familia.app.core.model.NotebookOccasion
import fr.familia.app.core.model.NotebookSource
import fr.familia.app.core.photo.PhotoEdit
import fr.familia.app.core.photo.SinglePhoto
import fr.familia.app.core.photo.SinglePhotoSaver
import fr.familia.app.feature.gifts.item.GiftItemForm
import fr.familia.app.feature.gifts.item.GiftItemFormError
import fr.familia.app.feature.gifts.item.normalizeLink
import fr.familia.app.feature.gifts.item.onlineWhere
import fr.familia.app.feature.gifts.item.priceText
import fr.familia.app.feature.gifts.item.validateItem
import fr.familia.app.feature.gifts.parseEuros
import fr.familia.app.core.model.householdGroups
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A private idea as typed. [personId] null with [otherName] set: someone outside the family. */
data class NotebookIdeaForm(
    val personId: String? = null,
    val otherName: String = "",
    val occasion: GiftOccasion? = null,
    val year: String = "",
    val title: String = "",
    val url: String = "",
    val price: String = "",
    val note: String = "",
    val status: GiftStatus = GiftStatus.Idea,
    /** « Où trouver le cadeau ? » (correctif E11), 120 characters at most. */
    val whereToFind: String = "",
    /** Its photo (E11): sent by the ViewModel before the idea, not kept in the draft. */
    val photo: PhotoEdit = PhotoEdit.Unchanged,
) {
    /** The title, link, price and note checked like an item of a list. */
    val asItem: GiftItemForm get() = GiftItemForm(title, url, price, note, whereToFind = whereToFind)
}

enum class NotebookIdeaError { PersonMissing, NameTooLong, BadYear }

sealed interface NotebookIdeaUiState {
    data object Loading : NotebookIdeaUiState

    data object Unavailable : NotebookIdeaUiState

    data class Editing(
        val form: NotebookIdeaForm,
        val isNew: Boolean,
        /** The family, but the member: who the idea can be for. */
        val people: List<Member>,
        val errors: Set<NotebookIdeaError> = emptySet(),
        val itemErrors: Set<GiftItemFormError> = emptySet(),
        val hasChanges: Boolean = false,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
        /** A new idea from the page of this member (retour d'essai du 18/11): they stay chosen. */
        val fixedPerson: Member? = null,
        /** The picture of the link's page (correctif E12), kept with the idea. */
        val imageUrl: String? = null,
        val previewLoading: Boolean = false,
        /** Opened from a surprise (E13): the idea stays mine until I add it to the surprise. */
        val fromSurprise: Boolean = false,
        /** « Plus de photos » is on: the form offers a photo, [storedPhoto] being the saved one. */
        val showPhoto: Boolean = false,
        val storedPhoto: String? = null,
    ) : NotebookIdeaUiState

    data object Done : NotebookIdeaUiState
}

/**
 * Add a private idea to the notebook ([entryId] null), or edit or remove one (PRD, récit 9 bis). It
 * reserves nothing: nobody but the member knows about it.
 */
class NotebookIdeaViewModel(
    private val notebook: NotebookRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val entryId: String?,
    /** The occasion chosen on the notebook, for a new idea. */
    private val startOccasion: NotebookOccasion = NotebookOccasion(null, null),
    /** « Un achat fait ailleurs » (récit 57): a new idea that starts as bought. */
    private val startBought: Boolean = false,
    /** A new idea from the page of a member: that member is already chosen. */
    private val startPersonId: String? = null,
    /** A new idea from « Mes notes » (récit 114): the note is already written. */
    private val startNote: String? = null,
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val writeTimeoutMillis: Long = 4_000,
    /** Reads the image, title and price of a link (« Coller », or a link typed); null reads nothing. */
    private val previews: LinkPreviewRepository? = null,
    /** Opened from a surprise (E13): only the words of the 🔒 change. */
    private val fromSurprise: Boolean = false,
    /** Sends and removes the photo of the idea (E11); null hides it, as when « Plus de photos » is off. */
    private val photos: SinglePhotoSaver? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NotebookIdeaUiState>(NotebookIdeaUiState.Loading)
    val uiState: StateFlow<NotebookIdeaUiState> = _uiState.asStateFlow()

    private var initialForm = NotebookIdeaForm()
    private var original: NotebookEntry? = null
    private var people: List<Member> = emptyList()
    private var fixedPerson: Member? = null
    private var showErrors = false
    /** The picture read for [previewedUrl]: dropped if the member changes the link. */
    private var previewImage: String? = null
    private var previewedUrl: String? = null
    private var typedPreview: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val repo = notebook ?: return unavailable()
        val memberRepo = members ?: return unavailable()
        val loaded = try {
            val family = memberRepo.observeFamily().first()
            family to entryId?.let { id -> repo.observeEntries(currentMemberId).first().firstOrNull { it.id == id } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return unavailable()
        val (family, existing) = loaded
        if (family.members.none { it.id == currentMemberId }) return unavailable()
        // Only a private idea is edited here; a reservation is followed from the notebook.
        if (entryId != null && existing?.source != NotebookSource.Idea) return unavailable()
        // By household, in the order of the Famille tab (retour d'essai, 17/11).
        people = householdGroups(family).flatMap { group -> group.members.filter { it.id != currentMemberId } }
        original = existing
        initialForm = existing?.let { e ->
            NotebookIdeaForm(
                personId = e.personId,
                otherName = if (e.personId == null) e.personName else "",
                occasion = e.occasion.occasion,
                year = e.occasion.year?.toString().orEmpty(),
                title = e.title,
                url = e.url.orEmpty(),
                price = e.price?.let { p -> if (p % 1.0 == 0.0) p.toLong().toString() else p.toString().replace('.', ',') }.orEmpty(),
                note = e.note.orEmpty(),
                status = e.status,
                whereToFind = e.whereToFind.orEmpty(),
            )
        } ?: NotebookIdeaForm(
            personId = startPersonId?.takeIf { id -> people.any { it.id == id } },
            occasion = startOccasion.occasion,
            year = startOccasion.year?.toString().orEmpty(),
            note = startNote.orEmpty().trim().take(500),
            status = if (startBought) GiftStatus.Bought else GiftStatus.Idea,
        )
        fixedPerson = if (existing == null) people.firstOrNull { it.id == startPersonId } else null
        val form = (restoredDraft() ?: initialForm).fixed()
        _uiState.value = NotebookIdeaUiState.Editing(
            form, isNew = existing == null, people = people, hasChanges = form != initialForm, fixedPerson = fixedPerson,
            imageUrl = imageFor(form), fromSurprise = fromSurprise, showPhoto = photos != null, storedPhoto = existing?.photo,
        )
        // An idea saved with a link but no picture: its page gives one now.
        if (existing != null && existing.imageUrl == null && existing.url != null) readPreview(existing.url, fillForm = false)
    }

    /** Fills what the member has not typed yet with what the link's page says (E12). */
    private suspend fun readPreview(url: String, fillForm: Boolean = true) {
        val repo = previews ?: return
        val preview = try {
            repo.preview(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val state = _uiState.value as? NotebookIdeaUiState.Editing ?: return
        if (preview == null) {
            _uiState.value = state.copy(previewLoading = false)
            return
        }
        previewImage = preview.imageUrl
        previewedUrl = url
        val form = if (!fillForm) state.form else state.form.copy(
            title = state.form.title.ifBlank { preview.title.orEmpty().take(80) },
            price = state.form.price.ifBlank { preview.price?.let(::priceText).orEmpty() },
            whereToFind = state.form.whereToFind.ifBlank { onlineWhere(url).orEmpty() },
        )
        if (fillForm) keepDraft(form)
        _uiState.value = state.copy(form = form, hasChanges = form != initialForm, imageUrl = imageFor(form), previewLoading = false)
    }

    /** The picture goes with its link: none once the member types another one. */
    private fun imageFor(form: NotebookIdeaForm): String? {
        val link = normalizeLink(form.url)
        return if (link != null && previewedUrl != null && link == normalizeLink(previewedUrl.orEmpty())) {
            previewImage
        } else {
            original?.imageUrl?.takeIf { link == original?.url }
        }
    }

    /** « Coller » (E12): the link of the clipboard, then what its page says fills what is still empty. */
    fun pasteLink(url: String) {
        update { it.copy(url = url) }
        typedPreview?.cancel()
        val state = _uiState.value as? NotebookIdeaUiState.Editing ?: return
        val link = normalizeLink(url) ?: return
        if (previews == null) return
        _uiState.value = state.copy(previewLoading = true)
        viewModelScope.launch { readPreview(link) }
    }

    /** A link typed or pasted from the keyboard is read too, once typing stops. */
    private fun readTypedLink(url: String) {
        typedPreview?.cancel()
        val link = normalizeLink(url) ?: return
        if (previews == null || link == previewedUrl?.let(::normalizeLink)) return
        typedPreview = viewModelScope.launch {
            delay(TYPED_LINK_PAUSE_MILLIS)
            readPreview(link)
        }
    }

    /** From a member's page, the idea stays for that member whatever the form asks. */
    private fun NotebookIdeaForm.fixed(): NotebookIdeaForm = fixedPerson?.let { copy(personId = it.id, otherName = "") } ?: this

    private fun unavailable() {
        _uiState.value = NotebookIdeaUiState.Unavailable
    }

    fun update(change: (NotebookIdeaForm) -> NotebookIdeaForm) {
        val state = _uiState.value as? NotebookIdeaUiState.Editing ?: return
        val form = change(state.form).fixed()
        keepDraft(form)
        _uiState.value = state.copy(
            form = form,
            errors = if (showErrors) validateIdea(form, people) else emptySet(),
            itemErrors = if (showErrors) validateItem(form.asItem) else emptySet(),
            hasChanges = form != initialForm,
            saveFailed = false,
            imageUrl = imageFor(form),
        )
        if (form.url != state.form.url) readTypedLink(form.url)
    }

    fun save() {
        val state = _uiState.value as? NotebookIdeaUiState.Editing ?: return
        val repo = notebook ?: return
        if (state.isSaving) return
        showErrors = true
        val form = state.form
        val errors = validateIdea(form, people)
        val itemErrors = validateItem(form.asItem)
        if (errors.isNotEmpty() || itemErrors.isNotEmpty()) {
            _uiState.value = state.copy(errors = errors, itemErrors = itemErrors)
            return
        }
        val person = people.firstOrNull { it.id == form.personId }
        val entry = NotebookEntry(
            id = original?.id ?: repo.newEntryId(currentMemberId),
            source = NotebookSource.Idea,
            personId = person?.id,
            personName = person?.firstName ?: form.otherName.trim(),
            occasion = NotebookOccasion(form.occasion, form.year.trim().toIntOrNull()),
            title = form.title.trim(),
            url = normalizeLink(form.url),
            price = parseEuros(form.price),
            note = form.note.trim().ifEmpty { null },
            status = form.status,
            imageUrl = state.imageUrl,
            whereToFind = form.whereToFind.trim().ifEmpty { null },
        )
        val before = original?.photo
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            val photo = try {
                // A removed photo gives null, which must stay null: no « ?: before » here.
                photos?.prepare(before, form.photo) { SinglePhoto.notebookIdea(currentMemberId) } ?: before.takeIf { photos == null }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch markFailed()
            }
            if (write { repo.saveIdea(currentMemberId, entry.copy(photo = photo), isNew = original == null) }) {
                photos?.cleanUp(previous = before, saved = photo)
                done()
            } else {
                photos?.cleanUp(previous = photo, saved = before)
                markFailed()
            }
        }
    }

    fun delete() {
        val state = _uiState.value as? NotebookIdeaUiState.Editing ?: return
        val repo = notebook ?: return
        val entry = original ?: return
        if (state.isSaving) return
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            if (write { repo.deleteIdea(currentMemberId, entry.id) }) {
                // The server removes it too; this only spares a photo left behind when it is late.
                photos?.cleanUp(previous = entry.photo, saved = null)
                done()
            } else {
                markFailed()
            }
        }
    }

    private fun done() {
        DRAFT_KEYS.forEach { saved.remove<Any>(it) }
        _uiState.value = NotebookIdeaUiState.Done
    }

    private fun markFailed() {
        val state = _uiState.value as? NotebookIdeaUiState.Editing ?: return
        _uiState.value = state.copy(isSaving = false, saveFailed = true)
    }

    private suspend fun write(block: suspend () -> Unit): Boolean = try {
        withTimeoutOrNull(writeTimeoutMillis) { block() }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private fun keepDraft(form: NotebookIdeaForm) {
        saved[DRAFT_PERSON] = form.personId
        saved[DRAFT_OTHER] = form.otherName
        saved[DRAFT_OCCASION] = form.occasion?.name
        saved[DRAFT_YEAR] = form.year
        saved[DRAFT_TITLE] = form.title
        saved[DRAFT_URL] = form.url
        saved[DRAFT_PRICE] = form.price
        saved[DRAFT_NOTE] = form.note
        saved[DRAFT_STATUS] = form.status.name
        saved[DRAFT_WHERE] = form.whereToFind
    }

    /** The draft kept by Android; a member who left the family is dropped. */
    private fun restoredDraft(): NotebookIdeaForm? {
        val title = saved.get<String>(DRAFT_TITLE) ?: return null
        return NotebookIdeaForm(
            personId = saved.get<String>(DRAFT_PERSON)?.takeIf { id -> people.any { it.id == id } },
            otherName = saved[DRAFT_OTHER] ?: "",
            occasion = saved.get<String>(DRAFT_OCCASION)?.let { name -> GiftOccasion.entries.firstOrNull { it.name == name } },
            year = saved[DRAFT_YEAR] ?: "",
            title = title,
            url = saved[DRAFT_URL] ?: "",
            price = saved[DRAFT_PRICE] ?: "",
            note = saved[DRAFT_NOTE] ?: "",
            status = saved.get<String>(DRAFT_STATUS)?.let { name -> GiftStatus.entries.firstOrNull { it.name == name } } ?: GiftStatus.Idea,
            whereToFind = saved[DRAFT_WHERE] ?: "",
        )
    }
}

/** The same limits as the security rules, for what the item check does not cover. */
fun validateIdea(form: NotebookIdeaForm, people: List<Member>): Set<NotebookIdeaError> = buildSet {
    val known = form.personId != null && people.any { it.id == form.personId }
    if (!known && form.otherName.isBlank()) add(NotebookIdeaError.PersonMissing)
    if (!known && form.otherName.trim().length > 40) add(NotebookIdeaError.NameTooLong)
    val year = form.year.trim()
    if (year.isNotEmpty() && year.toIntOrNull()?.let { it in 2020..2100 } != true) add(NotebookIdeaError.BadYear)
}

private const val DRAFT_PERSON = "draft.person"
private const val DRAFT_OTHER = "draft.other"
private const val DRAFT_OCCASION = "draft.occasion"
private const val DRAFT_YEAR = "draft.year"
private const val DRAFT_TITLE = "draft.title"
private const val DRAFT_URL = "draft.url"
private const val DRAFT_PRICE = "draft.price"
private const val DRAFT_NOTE = "draft.note"
private const val DRAFT_STATUS = "draft.status"
private const val DRAFT_WHERE = "draft.where"
private val DRAFT_KEYS = listOf(
    DRAFT_PERSON, DRAFT_OTHER, DRAFT_OCCASION, DRAFT_YEAR, DRAFT_TITLE, DRAFT_URL, DRAFT_PRICE, DRAFT_NOTE, DRAFT_STATUS, DRAFT_WHERE,
)

/** The pause after the last letter of a typed link before its page is read. */
private const val TYPED_LINK_PAUSE_MILLIS = 800L
