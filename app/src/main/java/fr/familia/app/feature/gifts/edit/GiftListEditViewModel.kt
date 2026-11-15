package fr.familia.app.feature.gifts.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.feature.gifts.canManageList
import fr.familia.app.feature.gifts.keepsListsOf
import fr.familia.app.feature.gifts.keptMembers
import fr.familia.app.feature.gifts.lockedViewers
import fr.familia.app.feature.gifts.normalizedViewers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** The fields of the list form. */
data class GiftListForm(
    val title: String = "",
    val occasion: GiftOccasion = GiftOccasion.Christmas,
    val date: LocalDate? = null,
    val beneficiaryId: String = "",
    val viewerIds: Set<String> = emptySet(),
)

enum class GiftListFormError { TitleMissing, TitleTooLong, DateMissing }

/** A person the list can be for; [surprise] when it is someone whose lists the member does not keep. */
data class BeneficiaryChoice(val member: Member, val isMe: Boolean, val surprise: Boolean)

sealed interface GiftListEditUiState {
    data object Loading : GiftListEditUiState

    data object Unavailable : GiftListEditUiState

    data class Editing(
        val form: GiftListForm,
        val isNew: Boolean,
        val beneficiaries: List<BeneficiaryChoice>,
        val members: List<Member>,
        val households: List<Household>,
        /** Checked and greyed: the member, the beneficiary of an own list and the adults of a child. */
        val locked: Set<String>,
        val errors: Set<GiftListFormError> = emptySet(),
        val hasChanges: Boolean = false,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : GiftListEditUiState {
        val beneficiary: BeneficiaryChoice? get() = beneficiaries.firstOrNull { it.member.id == form.beneficiaryId }
    }

    data object Done : GiftListEditUiState
}

/**
 * Create a list for an occasion ([listId] null) or edit one (PRD, récit 7). The list is the
 * member's own, a child's they keep, or a surprise for anyone else. Its beneficiary never changes.
 */
class GiftListEditViewModel(
    private val gifts: GiftRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val listId: String?,
    /** Preselected beneficiary, for « Créer une liste surprise » from a reminder. */
    private val forMemberId: String? = null,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GiftListEditUiState>(GiftListEditUiState.Loading)
    val uiState: StateFlow<GiftListEditUiState> = _uiState.asStateFlow()

    private var original: GiftList? = null
    private var me: Member? = null
    private var family: Family? = null
    private var initialForm = GiftListForm()
    private var showErrors = false
    private var newId = ""

    /** True once the list was deleted rather than saved: the list screen behind closes too. */
    var deleted = false
        private set

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val giftRepo = gifts ?: return unavailable()
        val memberRepo = members ?: return unavailable()
        val loaded = try {
            val fam = memberRepo.observeFamily().first()
            fam to listId?.let { giftRepo.observeList(it).first() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return unavailable()
        val (fam, existing) = loaded
        val current = fam.members.firstOrNull { it.id == currentMemberId } ?: return unavailable()
        if (listId != null && (existing == null || existing.kind == GiftListKind.Wishes || !canManageList(existing, current, fam))) {
            return unavailable()
        }
        me = current
        family = fam
        original = existing
        newId = existing?.id ?: giftRepo.newListId()

        val kept = keptMembers(current, fam)
        val keptIds = kept.map { it.id }.toSet()
        val others = fam.members.filter { it.id !in keptIds }.sortedBy { it.firstName.lowercase() }
        val choices = kept.map { BeneficiaryChoice(it, it.id == current.id, surprise = false) } +
            others.map { BeneficiaryChoice(it, isMe = false, surprise = true) }

        val beneficiaryId = existing?.beneficiaryId ?: forMemberId?.takeIf { id -> choices.any { it.member.id == id } } ?: current.id
        initialForm = existing?.let {
            GiftListForm(it.title, it.occasion ?: GiftOccasion.Other, it.date, it.beneficiaryId, it.viewerIds.toSet())
        } ?: defaultForm(fam, current, beneficiaryId)
        _uiState.value = GiftListEditUiState.Editing(
            form = initialForm,
            isNew = existing == null,
            beneficiaries = if (existing == null) choices else choices.filter { it.member.id == existing.beneficiaryId },
            members = fam.members.sortedBy { it.firstName.lowercase() },
            households = fam.households.sortedBy { it.name.lowercase() },
            locked = locked(fam, current, initialForm),
        )
    }

    /** A new list is seen by the whole family by default, except the beneficiary of a surprise. */
    private fun defaultForm(fam: Family, current: Member, beneficiaryId: String): GiftListForm {
        val beneficiary = fam.members.firstOrNull { it.id == beneficiaryId } ?: current
        val surprise = !keepsListsOf(current, beneficiary)
        val all = fam.members.map { it.id }.toSet()
        return GiftListForm(
            occasion = if (surprise) GiftOccasion.Birthday else GiftOccasion.Christmas,
            beneficiaryId = beneficiary.id,
            viewerIds = if (surprise) all - beneficiary.id else all,
        )
    }

    private fun kindFor(fam: Family, current: Member, beneficiaryId: String): GiftListKind {
        val beneficiary = fam.members.firstOrNull { it.id == beneficiaryId } ?: return GiftListKind.Own
        return if (keepsListsOf(current, beneficiary)) GiftListKind.Own else GiftListKind.Surprise
    }

    private fun locked(fam: Family, current: Member, form: GiftListForm): Set<String> {
        val beneficiary = fam.members.firstOrNull { it.id == form.beneficiaryId } ?: return setOf(current.id)
        val kind = original?.kind ?: kindFor(fam, current, form.beneficiaryId)
        return lockedViewers(kind, beneficiary, current)
    }

    private fun unavailable() {
        _uiState.value = GiftListEditUiState.Unavailable
    }

    fun update(change: (GiftListForm) -> GiftListForm) {
        val state = _uiState.value as? GiftListEditUiState.Editing ?: return
        val fam = family ?: return
        val current = me ?: return
        var form = change(state.form)
        if (form.beneficiaryId != state.form.beneficiaryId) {
            // A surprise never lists its beneficiary; switching back to an own list adds them again.
            form = if (kindFor(fam, current, form.beneficiaryId) == GiftListKind.Surprise) {
                form.copy(viewerIds = (form.viewerIds + state.form.beneficiaryId) - form.beneficiaryId)
            } else {
                form.copy(viewerIds = form.viewerIds + form.beneficiaryId)
            }
        }
        val locked = locked(fam, current, form)
        _uiState.value = state.copy(
            form = form.copy(viewerIds = form.viewerIds + locked),
            locked = locked,
            errors = errorsFor(form),
            hasChanges = form != initialForm,
            saveFailed = false,
        )
    }

    fun save() {
        val state = _uiState.value as? GiftListEditUiState.Editing ?: return
        val repo = gifts ?: return
        val fam = family ?: return
        val current = me ?: return
        if (state.isSaving) return
        showErrors = true
        val errors = errorsFor(state.form)
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(errors = errors)
            return
        }
        val form = state.form
        val beneficiary = fam.members.firstOrNull { it.id == form.beneficiaryId } ?: return
        val kind = original?.kind ?: kindFor(fam, current, form.beneficiaryId)
        val creator = original?.let { o -> fam.members.firstOrNull { it.id == o.creatorId } } ?: current
        val list = GiftList(
            id = newId,
            kind = kind,
            title = form.title.trim(),
            occasion = form.occasion,
            date = form.date,
            beneficiaryId = beneficiary.id,
            viewerIds = normalizedViewers(kind, beneficiary, creator, form.viewerIds + current.id)
                // Members removed meanwhile are dropped.
                .filter { id -> fam.members.any { it.id == id } },
            creatorId = original?.creatorId ?: current.id,
            creatorName = original?.creatorName ?: current.firstName,
        )
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            if (write { repo.saveList(list, isNew = original == null) }) {
                _uiState.value = GiftListEditUiState.Done
            } else {
                markFailed()
            }
        }
    }

    fun delete() {
        val state = _uiState.value as? GiftListEditUiState.Editing ?: return
        val repo = gifts ?: return
        val list = original ?: return
        if (state.isSaving) return
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            if (write { repo.deleteList(list.id) }) {
                deleted = true
                _uiState.value = GiftListEditUiState.Done
            } else {
                markFailed()
            }
        }
    }

    private fun errorsFor(form: GiftListForm): Set<GiftListFormError> = if (!showErrors) {
        emptySet()
    } else {
        buildSet {
            val title = form.title.trim()
            if (title.isEmpty()) add(GiftListFormError.TitleMissing)
            if (title.length > 60) add(GiftListFormError.TitleTooLong)
            if (form.date == null) add(GiftListFormError.DateMissing)
        }
    }

    private fun markFailed() {
        val state = _uiState.value as? GiftListEditUiState.Editing ?: return
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
}
