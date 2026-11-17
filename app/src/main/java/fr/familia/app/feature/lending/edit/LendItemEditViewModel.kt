package fr.familia.app.feature.lending.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.LendItemRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.LendCategory
import fr.familia.app.core.model.LendItem
import fr.familia.app.core.model.lendPhotoBase
import fr.familia.app.core.photo.PhotoEdit
import fr.familia.app.core.photo.SinglePhotoSaver
import fr.familia.app.feature.lending.canAddLendItem
import fr.familia.app.feature.lending.canManageLendItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the member types on the form of a thing to lend. */
data class LendItemForm(
    val name: String = "",
    val note: String = "",
    val category: LendCategory? = null,
    val photo: PhotoEdit = PhotoEdit.Unchanged,
)

enum class LendFormError { NameMissing, CategoryMissing }

sealed interface LendItemEditUiState {
    data object Loading : LendItemEditUiState

    /** Not found, not mine to change, or a child: the screen says so. */
    data object Unavailable : LendItemEditUiState

    data object Done : LendItemEditUiState

    data class Editing(
        val form: LendItemForm,
        val isNew: Boolean,
        /** The photo already saved, as a Storage base. */
        val storedPhoto: String? = null,
        val errors: Set<LendFormError> = emptySet(),
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : LendItemEditUiState
}

const val LEND_NAME_MAX = 60
const val LEND_NOTE_MAX = 300

/**
 * Add a thing to lend ([itemId] null) or change one (PRD, récit 51): a name, a category, a note
 * and an optional photo, sent first as on a bon plan. The loan is never touched here.
 */
class LendItemEditViewModel(
    private val items: LendItemRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val itemId: String? = null,
    private val photos: SinglePhotoSaver? = null,
    private val writeTimeoutMillis: Long = 4_000,
    private val photoTimeoutMillis: Long = 60_000,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LendItemEditUiState>(LendItemEditUiState.Loading)
    val uiState: StateFlow<LendItemEditUiState> = _uiState.asStateFlow()

    private var original: LendItem? = null
    private var ownerName = ""
    private var editedId = ""
    private var showErrors = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val itemRepo = items ?: return unavailable()
        val memberRepo = members ?: return unavailable()
        val (family, existing) = try {
            memberRepo.observeFamily().first() to itemId?.let { itemRepo.get(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return unavailable()
        }
        val me = family.members.firstOrNull { it.id == currentMemberId } ?: return unavailable()
        if (itemId == null && !canAddLendItem(me)) return unavailable()
        if (itemId != null && (existing == null || !canManageLendItem(existing, me, family))) return unavailable()
        original = existing
        ownerName = me.firstName
        editedId = existing?.id ?: itemRepo.newId()
        val form = existing?.let { LendItemForm(it.name, it.note, it.category) } ?: LendItemForm()
        _uiState.value = LendItemEditUiState.Editing(form, isNew = existing == null, storedPhoto = existing?.photo)
    }

    private fun unavailable() {
        _uiState.value = LendItemEditUiState.Unavailable
    }

    fun update(change: (LendItemForm) -> LendItemForm) {
        val state = _uiState.value as? LendItemEditUiState.Editing ?: return
        val next = state.copy(form = change(state.form), saveFailed = false)
        _uiState.value = next.copy(errors = errorsFor(next.form))
    }

    private fun errorsFor(form: LendItemForm): Set<LendFormError> {
        if (!showErrors) return emptySet()
        return buildSet {
            if (form.name.isBlank()) add(LendFormError.NameMissing)
            if (form.category == null) add(LendFormError.CategoryMissing)
        }
    }

    fun save() {
        val state = _uiState.value as? LendItemEditUiState.Editing ?: return
        val repo = items ?: return
        if (state.isSaving) return
        showErrors = true
        val errors = errorsFor(state.form)
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(errors = errors)
            return
        }
        val existing = original
        val form = state.form
        fun item(photo: String?) = (existing ?: LendItem(id = editedId, name = "", ownerId = currentMemberId, ownerName = ownerName)).copy(
            name = form.name.trim().take(LEND_NAME_MAX),
            note = form.note.trim().take(LEND_NOTE_MAX),
            category = requireNotNull(form.category),
            photo = photo,
        )
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            // The photo goes first: the rules only accept a photo already sent.
            val previous = existing?.photo
            val photo = if (photos == null) {
                previous.takeIf { form.photo == PhotoEdit.Unchanged }
            } else {
                try {
                    withTimeoutOrNull(photoTimeoutMillis) {
                        photos.prepare(previous, form.photo) { lendPhotoBase(editedId, currentMemberId) }
                    // A new photo is never null once sent: null here means it timed out.
                    } ?: if (form.photo is PhotoEdit.Picked) throw IllegalStateException("photo not sent") else null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@launch failed()
                }
            }
            val written = try {
                withTimeoutOrNull(writeTimeoutMillis) { repo.save(item(photo), isNew = existing == null) }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (written) {
                photos?.cleanUp(previous, photo)
                _uiState.value = LendItemEditUiState.Done
            } else {
                // Not saved: the photo just sent is named by nothing.
                if (photo != previous) photos?.cleanUp(photo, null)
                failed()
            }
        }
    }

    private fun failed() {
        val state = _uiState.value as? LendItemEditUiState.Editing ?: return
        _uiState.value = state.copy(isSaving = false, saveFailed = true)
    }
}
