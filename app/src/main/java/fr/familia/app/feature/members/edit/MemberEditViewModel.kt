package fr.familia.app.feature.members.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.theme.MemberColors
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.canAddMembers
import fr.familia.app.core.model.canEditMember
import fr.familia.app.core.model.isOwner
import fr.familia.app.core.ui.Confirmation
import fr.familia.app.core.ui.WriteSent
import fr.familia.app.core.ui.sendWrite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Form of the admin principal or of a chef de famille (for their own household only) to add a member ([memberId] null) or edit one (PRD, "Utilisateurs"). Offline, the
 * write lands in the cache at once and Firestore sends it when the network is back.
 */
class MemberEditViewModel(
    private val members: MemberRepository?,
    private val currentMember: Member,
    private val memberId: String?,
    private val today: () -> LocalDate = LocalDate::now,
    private val writeTimeoutMillis: Long = 4_000,
    /** Keeps the draft if Android closes the app in the background (audit DESIGN du 15/11). */
    private val savedState: SavedStateHandle? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MemberEditUiState>(MemberEditUiState.Loading)
    val uiState: StateFlow<MemberEditUiState> = _uiState.asStateFlow()

    private var family = Family(emptyList(), emptyList())
    private var original: Member? = null
    private var editedId = ""
    private var showErrors = false
    private var initialForm = MemberForm()
    private var isOwner = false
    private var myHouseholdId = ""

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
        // Read live: a chef whose role was just removed cannot open the form any more.
        val me = family.members.firstOrNull { it.id == currentMember.id }
        val allowed = me != null && when {
            memberId == null -> family.canAddMembers(me)
            existing == null -> false
            else -> family.canEditMember(me, existing)
        }
        if (me == null || !allowed) {
            _uiState.value = MemberEditUiState.NotFound
            return
        }
        isOwner = family.isOwner(me)
        myHouseholdId = me.householdId
        original = existing
        editedId = existing?.id ?: savedState?.get<String>(KEY_ID) ?: repo.newMemberId()
        savedState?.set(KEY_ID, editedId)
        val households = if (isOwner) family.households else family.households.filter { it.id == me.householdId }
        initialForm = existing?.let { MemberForm.from(it) }
            ?: MemberForm(householdId = households.singleOrNull()?.id, colorIndex = freeColor(family))
        // A chef chooses responsible adults in their own household. If the admin principal chose
        // one from another household, only the admin principal changes them (the rules check it).
        val outsideParents = existing?.parentIds.orEmpty().filter { id ->
            family.members.any { it.id == id && it.householdId != me.householdId }
        }
        val children = family.members.filter { editedId in it.parentIds }
        val draft = savedState?.let { restoreDraft(it) }?.let { d ->
            d.copy(householdId = d.householdId?.takeIf { id -> households.any { it.id == id } } ?: initialForm.householdId)
        }
        val form = draft ?: initialForm
        _uiState.value = MemberEditUiState.Editing(
            form = form,
            hasChanges = form != initialForm,
            isNew = existing == null,
            households = households.sortedBy { it.name.lowercase() },
            adults = family.members
                .filter { it.profile == MemberProfile.Adult && it.id != editedId }
                .filter { isOwner || it.householdId == me.householdId }
                .sortedBy { it.firstName.lowercase() },
            canAddHousehold = isOwner,
            canEditEmail = isOwner || existing == null || !existing.isAdmin || existing.id == me.id,
            canEditParents = isOwner || outsideParents.isEmpty(),
            canRemove = existing != null && existing.id != currentMember.id &&
                (isOwner || (!existing.isAdmin && children.all { it.householdId == me.householdId })),
            removeOwnerOnly = existing != null && existing.id != currentMember.id && !isOwner &&
                (existing.isAdmin || children.any { it.householdId != me.householdId }),
            soleParentOf = children
                .filter { child -> child.otherParents().isEmpty() }
                .map { it.firstName }
                .sorted(),
            canInvite = existing?.email != null && existing.uid == null,
            errors = emptySet(),
        )
    }

    fun update(change: (MemberForm) -> MemberForm) {
        val state = _uiState.value as? MemberEditUiState.Editing ?: return
        val form = change(state.form)
        savedState?.let { saveDraft(it, form) }
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
        if (!state.canAddHousehold) return
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
        // Chefs are named in the Administration menu only: the form never changes the flag.
        val chosen = state.form.copy(isAdmin = original?.isAdmin == true)
        // Only current adults are kept: the rules refuse any other responsible adult (lot 7 audit).
        val adults = family.members.filter { it.profile == MemberProfile.Adult && it.id != editedId }.map { it.id }.toSet()
        val form = if (state.canEditParents) {
            chosen.copy(parentIds = chosen.parentIds.filter { it in adults }.toSet())
        } else {
            chosen.copy(parentIds = original?.parentIds.orEmpty().toSet())
        }
        // The rules refuse a chef changing another chef's address.
        val withEmail = if (state.canEditEmail) form else form.copy(email = original?.email.orEmpty())
        // A chef keeps the member in their own household, even if the form was tampered with.
        if (!isOwner && withEmail.householdId != myHouseholdId) {
            markFailed()
            return
        }
        val member = withEmail.toMember(editedId, original?.uid)
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            val sent = write { repo.saveMember(member, original?.email) }
            if (sent == null) return@launch markFailed()
            _uiState.value = if (original == null) {
                MemberEditUiState.Added(member.firstName, member.email)
            } else {
                MemberEditUiState.Saved(sent.or(Confirmation.Saved))
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
            val sent = write { repo.removeMember(member, children) }
            if (sent != null) _uiState.value = MemberEditUiState.Saved(sent.or(Confirmation.MemberRemoved)) else markFailed()
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

    /** How the write ended, null when the server refused it (see [sendWrite]). */
    private suspend fun write(block: suspend () -> Unit): WriteSent? = sendWrite(writeTimeoutMillis, block)
}

private const val KEY_ID = "memberDraft.id"
private const val KEY_DRAFT = "memberDraft."

/** The form as plain values, the only kind a SavedStateHandle keeps across the end of the process. */
private fun saveDraft(handle: SavedStateHandle, form: MemberForm) {
    handle[KEY_DRAFT + "firstName"] = form.firstName
    handle[KEY_DRAFT + "profile"] = form.profile.firestoreValue
    handle[KEY_DRAFT + "email"] = form.email
    handle[KEY_DRAFT + "householdId"] = form.householdId
    handle[KEY_DRAFT + "birthDate"] = form.birthDate?.toString()
    handle[KEY_DRAFT + "parentIds"] = ArrayList(form.parentIds)
    handle[KEY_DRAFT + "colorIndex"] = form.colorIndex
    handle[KEY_DRAFT + "isAdmin"] = form.isAdmin
}

private fun restoreDraft(handle: SavedStateHandle): MemberForm? {
    val firstName = handle.get<String>(KEY_DRAFT + "firstName") ?: return null
    return MemberForm(
        firstName = firstName,
        profile = MemberProfile.fromFirestore(handle.get<String>(KEY_DRAFT + "profile")),
        email = handle.get<String>(KEY_DRAFT + "email").orEmpty(),
        householdId = handle.get<String>(KEY_DRAFT + "householdId"),
        birthDate = handle.get<String>(KEY_DRAFT + "birthDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        parentIds = handle.get<ArrayList<String>>(KEY_DRAFT + "parentIds").orEmpty().toSet(),
        colorIndex = handle.get<Int>(KEY_DRAFT + "colorIndex"),
        isAdmin = handle.get<Boolean>(KEY_DRAFT + "isAdmin") ?: false,
    )
}

/** The first of the member colors nobody has yet (DESIGN-5), or null when all 13 are taken. */
internal fun freeColor(family: Family): Int? {
    val taken = family.members.mapNotNull { it.colorIndex }.toSet()
    return MemberColors.indices.firstOrNull { it !in taken }
}
