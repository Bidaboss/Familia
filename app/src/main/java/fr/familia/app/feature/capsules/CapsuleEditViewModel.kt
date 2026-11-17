package fr.familia.app.feature.capsules

import fr.familia.app.core.ui.PickGroup
import fr.familia.app.core.ui.pickGroups
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.CapsuleRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Capsule
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.photo.PhotoUploader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.time.LocalDate

/** What the member writes. [photos] are content URIs, sent to Storage only on « Sceller ». */
data class CapsuleForm(
    val text: String = "",
    val recipientIds: Set<String> = emptySet(),
    val openAt: LocalDate? = null,
    val photos: List<String> = emptyList(),
)

enum class CapsuleFormError { TextMissing, NoRecipient, DateMissing, DateOutOfRange }

fun validate(form: CapsuleForm, today: LocalDate): Set<CapsuleFormError> = buildSet {
    if (form.text.isBlank()) add(CapsuleFormError.TextMissing)
    if (form.recipientIds.isEmpty()) add(CapsuleFormError.NoRecipient)
    val day = form.openAt
    if (day == null) {
        add(CapsuleFormError.DateMissing)
    } else if (day < Capsule.earliestOpening(today) || day > Capsule.latestOpening(today)) {
        add(CapsuleFormError.DateOutOfRange)
    }
}

/** A member who may receive the capsule: anyone of the family, oneself included. */
data class RecipientChoice(val id: String, val firstName: String)

enum class CapsuleSaveFailure { NeedsNetwork, PhotoUnreadable, Failed }

sealed interface CapsuleEditUiState {
    data object Loading : CapsuleEditUiState
    data object Unavailable : CapsuleEditUiState

    data class Editing(
        val form: CapsuleForm,
        val today: LocalDate,
        val members: List<RecipientChoice>,
        val errors: Set<CapsuleFormError> = emptySet(),
        /** The family by household, for the picker (règle de Kévin, 17/11); empty shows [members] as chips. */
        val groups: List<PickGroup> = emptyList(),
        /** null: not sending; 0: writing the capsule; n: sending photo n. */
        val sendingPhoto: Int? = null,
        val failure: CapsuleSaveFailure? = null,
    ) : CapsuleEditUiState {
        val isSaving: Boolean get() = sendingPhoto != null
        val canAddPhotos: Boolean get() = form.photos.size < Capsule.MAX_PHOTOS
    }

    data class Done(val capsuleId: String) : CapsuleEditUiState
}

/**
 * « Nouvelle capsule » (PRD, récit 39): a text, up to 3 photos, the recipients and the opening
 * day. The photos go to Storage first, then the capsule is written, so it never opens with a
 * photo missing. A capsule never changes once sealed. The draft, its photos included, survives Android
 * stopping the app.
 */
class CapsuleEditViewModel(
    private val capsules: CapsuleRepository?,
    private val members: MemberRepository?,
    private val uploader: PhotoUploader?,
    private val currentMemberId: String,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val isOnline: Flow<Boolean> = flowOf(true),
    private val today: () -> LocalDate = LocalDate::now,
    private val writeTimeoutMillis: Long = 10_000,
    /** Removes the photos already sent when the capsule could not be sealed, so none stays orphan. */
    private val storage: PhotoStorage? = null,
) : ViewModel() {

    private val state = MutableStateFlow<CapsuleEditUiState>(CapsuleEditUiState.Loading)
    val uiState: StateFlow<CapsuleEditUiState> = state.asStateFlow()
    private var me: Member? = null
    private var showErrors = false

    init {
        viewModelScope.launch { state.value = runCatching { load() }.getOrDefault(CapsuleEditUiState.Unavailable) }
    }

    private suspend fun load(): CapsuleEditUiState {
        if (capsules == null) return CapsuleEditUiState.Unavailable
        val family = members?.observeFamily()?.first() ?: return CapsuleEditUiState.Unavailable
        me = family.members.firstOrNull { it.id == currentMemberId } ?: return CapsuleEditUiState.Unavailable
        val known = family.members.map { it.id }.toSet()
        val form = CapsuleForm(
            text = savedState[TEXT] ?: "",
            recipientIds = savedState.get<ArrayList<String>>(RECIPIENTS).orEmpty().filter { it in known }.toSet(),
            openAt = savedState.get<String>(OPEN_AT)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            photos = savedState.get<ArrayList<String>>(PHOTOS).orEmpty().take(Capsule.MAX_PHOTOS),
        )
        val choices = family.members.sortedBy { it.firstName.lowercase() }.map { RecipientChoice(it.id, it.firstName) }
        return CapsuleEditUiState.Editing(form, today(), choices, groups = pickGroups(family.members, family, me?.householdId))
    }

    private fun edit(change: (CapsuleForm) -> CapsuleForm) {
        state.update { current ->
            if (current !is CapsuleEditUiState.Editing || current.isSaving) return@update current
            val form = change(current.form)
            savedState[TEXT] = form.text
            savedState[RECIPIENTS] = ArrayList(form.recipientIds)
            savedState[OPEN_AT] = form.openAt?.toString()
            savedState[PHOTOS] = ArrayList(form.photos)
            current.copy(form = form, errors = if (showErrors) validate(form, current.today) else emptySet(), failure = null)
        }
    }

    fun setText(text: String) = edit { it.copy(text = text.take(Capsule.MAX_TEXT)) }
    /** Who the capsule is for, as the picker by household gives them. */
    fun setRecipients(ids: Set<String>) = edit { it.copy(recipientIds = ids) }

    fun toggleRecipient(id: String) = edit { it.copy(recipientIds = if (id in it.recipientIds) it.recipientIds - id else it.recipientIds + id) }
    fun setOpenAt(day: LocalDate) = edit { it.copy(openAt = day) }
    fun addPhotos(uris: List<String>) = edit { it.copy(photos = (it.photos + uris).distinct().take(Capsule.MAX_PHOTOS)) }
    fun removePhoto(uri: String) = edit { it.copy(photos = it.photos - uri) }

    /** Seals the capsule: photos first, then the document. */
    fun seal() {
        val current = state.value as? CapsuleEditUiState.Editing ?: return
        if (current.isSaving) return
        val errors = validate(current.form, current.today)
        if (errors.isNotEmpty()) {
            showErrors = true
            state.value = current.copy(errors = errors)
            return
        }
        viewModelScope.launch { state.value = write(current) }
    }

    private suspend fun write(s: CapsuleEditUiState.Editing): CapsuleEditUiState {
        val repo = capsules ?: return s.copy(failure = CapsuleSaveFailure.Failed)
        val author = me ?: return s.copy(failure = CapsuleSaveFailure.Failed)
        val photos = s.form.photos
        val upload = uploader
        if (photos.isNotEmpty() && (upload == null || !isOnline.first())) return s.copy(failure = CapsuleSaveFailure.NeedsNetwork)
        val capsule = Capsule(
            id = repo.newCapsuleId(),
            text = s.form.text.trim(),
            photoCount = photos.size,
            recipientIds = s.form.recipientIds.sorted(),
            openAt = s.form.openAt ?: return s.copy(failure = CapsuleSaveFailure.Failed),
            openedFor = emptyList(),
            openedAt = null,
            authorId = author.id,
            authorName = author.firstName,
            createdAt = null,
        )
        var sent = 0
        return try {
            photos.forEachIndexed { index, uri ->
                state.value = s.copy(sendingPhoto = index + 1, failure = null)
                // Counted before the upload: a half-sent photo is removed too.
                sent = index + 1
                upload?.upload(uri, capsule.photoPath(index + 1), capsule.photoPath(index + 1, thumbnail = true))
            }
            state.value = s.copy(sendingPhoto = 0, failure = null)
            // Without photos, the write reaches the local cache at once and goes later when offline.
            withTimeoutOrNull(writeTimeoutMillis) { repo.createCapsule(capsule) }
            listOf(TEXT, RECIPIENTS, OPEN_AT, PHOTOS).forEach { savedState.remove<Any>(it) }
            CapsuleEditUiState.Done(capsule.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            removeSent(capsule, sent)
            val failure = when {
                e is IOException -> CapsuleSaveFailure.PhotoUnreadable
                isOnline.first() -> CapsuleSaveFailure.Failed
                else -> CapsuleSaveFailure.NeedsNetwork
            }
            s.copy(sendingPhoto = null, failure = failure)
        }
    }

    /** Best effort: a file that stays is only ever readable by its author. */
    private suspend fun removeSent(capsule: Capsule, count: Int) {
        val photos = storage ?: return
        for (n in 1..count) {
            for (path in listOf(capsule.photoPath(n), capsule.photoPath(n, thumbnail = true))) {
                try {
                    photos.delete(path)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Already gone, or offline: nothing more to do.
                }
            }
        }
    }

    private companion object {
        const val TEXT = "capsule_text"
        const val RECIPIENTS = "capsule_recipients"
        const val OPEN_AT = "capsule_open_at"
        const val PHOTOS = "capsule_photos"
    }
}
