package fr.familia.app.feature.members.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/**
 * Admin form to add a member ([memberId] null) or edit one (PRD, "Utilisateurs"). Offline, the
 * write lands in the cache at once and Firestore sends it when the network is back.
 */
class MemberEditViewModel(
    private val members: MemberRepository?,
    private val currentMember: Member,
    private val memberId: String?,
    private val today: () -> LocalDate = LocalDate::now,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MemberEditUiState>(MemberEditUiState.Loading)
    val uiState: StateFlow<MemberEditUiState> = _uiState.asStateFlow()

    private var family = Family(emptyList(), emptyList())
    private var original: Member? = null
    private var editedId = ""
    private var showErrors = false
    private var initialForm = MemberForm()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val repo = members ?: return
        family = try {
            repo.observeFamily().first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.value = MemberEditUiState.NotFound
            return
        }
        val existing = memberId?.let { id -> family.members.firstOrNull { it.id == id } }
        // Read live: an admin whose role was just removed cannot open the form any more.
        val stillAdmin = family.members.any { it.id == currentMember.id && it.isAdmin }
        if (!stillAdmin || (memberId != null && existing == null)) {
            _uiState.value = MemberEditUiState.NotFound
            return
        }
        original = existing
        editedId = existing?.id ?: repo.newMemberId()
        initialForm = existing?.let { MemberForm.from(it) }
            ?: MemberForm(householdId = family.households.singleOrNull()?.id)
        _uiState.value = MemberEditUiState.Editing(
            form = initialForm,
            isNew = existing == null,
            households = family.households.sortedBy { it.name.lowercase() },
            adults = family.members
                .filter { it.profile == MemberProfile.Adult && it.id != editedId }
                .sortedBy { it.firstName.lowercase() },
            canChangeAdmin = editedId != currentMember.id,
            canRemove = existing != null && existing.id != currentMember.id,
            soleParentOf = family.members
                .filter { child -> editedId in child.parentIds && child.otherParents().isEmpty() }
                .map { it.firstName }
                .sorted(),
            canInvite = existing?.email != null && existing.uid == null,
            errors = emptySet(),
        )
    }

    fun update(change: (MemberForm) -> MemberForm) {
        val state = _uiState.value as? MemberEditUiState.Editing ?: return
        val form = change(state.form)
        _uiState.value = state.copy(
            form = form,
            errors = errorsFor(form),
            hasChanges = form != initialForm,
            saveFailed = false,
        )
    }

    /** Creates a household from the form and selects it. */
    fun addHousehold(name: String) {
        val state = _uiState.value as? MemberEditUiState.Editing ?: return
        val repo = members ?: return
        val trimmed = name.trim().take(MAX_FIRST_NAME)
        if (trimmed.isEmpty()) return
        val household = Household(repo.newHouseholdId(), trimmed)
        family = family.copy(households = family.households + household)
        val form = state.form.copy(householdId = household.id)
        _uiState.value = state.copy(
            form = form,
            households = family.households.sortedBy { it.name.lowercase() },
            errors = errorsFor(form),
            hasChanges = true,
        )
        viewModelScope.launch {
            if (!write { repo.saveHousehold(household) }) markFailed()
        }
    }

    fun save() {
        val state = _uiState.value as? MemberEditUiState.Editing ?: return
        if (state.isSaving) return
        val repo = members ?: return
        showErrors = true
        val errors = errorsFor(state.form)
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(errors = errors)
            return
        }
        // Nobody changes their own admin flag, even if the form was tampered with.
        val chosen = if (state.canChangeAdmin) state.form else state.form.copy(isAdmin = original?.isAdmin == true)
        // Only current adults are kept: the rules refuse any other responsible adult (lot 7 audit).
        val adults = family.members.filter { it.profile == MemberProfile.Adult && it.id != editedId }.map { it.id }.toSet()
        val form = chosen.copy(parentIds = chosen.parentIds.filter { it in adults }.toSet())
        val member = form.toMember(editedId, original?.uid)
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            if (write { repo.saveMember(member, original?.email) }) {
                _uiState.value = if (original == null) {
                    MemberEditUiState.Added(member.firstName, member.email)
                } else {
                    MemberEditUiState.Saved
                }
            } else {
                markFailed()
            }
        }
    }

    /**
     * Removes the member (a separation, for example). Children who listed them keep their other
     * responsible adults; removal is not offered while they are a child's only one.
     */
    fun remove() {
        val state = _uiState.value as? MemberEditUiState.Editing ?: return
        val member = original ?: return
        val repo = members ?: return
        if (!state.canRemove || state.soleParentOf.isNotEmpty() || state.isSaving) return
        val children = family.members
            .filter { member.id in it.parentIds }
            .map { it.copy(parentIds = it.parentIds - member.id) }
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            if (write { repo.removeMember(member, children) }) _uiState.value = MemberEditUiState.Saved else markFailed()
        }
    }

    /** The child's responsible adults other than the edited member who still exist. */
    private fun Member.otherParents(): List<String> =
        parentIds.filter { id -> id != editedId && family.members.any { it.id == id } }

    private fun errorsFor(form: MemberForm): Set<FormError> =
        if (showErrors) validate(form, family, editedId, today()) else emptySet()

    private fun markFailed() {
        val state = _uiState.value as? MemberEditUiState.Editing ?: return
        _uiState.value = state.copy(isSaving = false, saveFailed = true)
    }

    /**
     * True once the write is accepted, or still pending after the timeout: offline, Firestore only
     * confirms a write when the server has it, and it stays queued until then.
     */
    private suspend fun write(block: suspend () -> Unit): Boolean = try {
        withTimeoutOrNull(writeTimeoutMillis) { block() }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}
