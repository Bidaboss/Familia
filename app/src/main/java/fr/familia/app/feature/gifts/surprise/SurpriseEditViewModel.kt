package fr.familia.app.feature.gifts.surprise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.SurpriseRepository
import fr.familia.app.core.data.familyToday
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.OpenSurprise
import fr.familia.app.core.model.Surprise
import fr.familia.app.core.model.SurpriseOccasion
import fr.familia.app.core.model.canCreateSurprise
import fr.familia.app.core.model.canJoinSurprise
import fr.familia.app.core.model.nearestSurpriseOccasion
import fr.familia.app.core.model.surpriseCandidates
import fr.familia.app.core.model.surpriseDateFor
import fr.familia.app.core.model.surpriseMembers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** The fields of « Nouvelle surprise ». An empty title takes the suggested one on save. */
data class SurpriseForm(
    val title: String = "",
    val occasion: SurpriseOccasion = SurpriseOccasion.Christmas,
    val date: LocalDate? = null,
    val memberIds: Set<String> = emptySet(),
    /** Once the date is picked by hand, a change of occasion keeps it. */
    val dateTouched: Boolean = false,
)

enum class SurpriseFormError { TitleTooLong, DateMissing, DatePast }

sealed interface SurpriseEditUiState {
    data object Loading : SurpriseEditUiState

    data object Unavailable : SurpriseEditUiState

    data class Editing(
        val form: SurpriseForm,
        val isNew: Boolean,
        val beneficiary: Member,
        val meId: String,
        /** Everyone the surprise can be shown to (never the beneficiary), and their households. */
        val candidates: List<Member>,
        val households: List<Household>,
        /** Other surprises in preparation for the same person: mine to open, the others to join. */
        val mine: List<Surprise> = emptyList(),
        val joinable: List<OpenSurprise> = emptyList(),
        val errors: Set<SurpriseFormError> = emptySet(),
        val hasChanges: Boolean = false,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
        val joinFailed: Boolean = false,
    ) : SurpriseEditUiState

    /** Saved, joined or opened: [surpriseId] is the surprise to show next; null once deleted. */
    data class Done(val surpriseId: String?) : SurpriseEditUiState
}

/**
 * « Nouvelle surprise » and « Modifier la surprise » (brief Cadeaux et Surprise, §3, fiche C3): the
 * title, the occasion, the date and who sees it, by household, the member always in. Only an adult
 * starts one, for someone else; only its creator changes it.
 */
class SurpriseEditViewModel(
    private val surprises: SurpriseRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val beneficiaryId: String?,
    private val surpriseId: String?,
    private val today: () -> LocalDate = { familyToday() },
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SurpriseEditUiState>(SurpriseEditUiState.Loading)
    val uiState: StateFlow<SurpriseEditUiState> = _uiState.asStateFlow()

    private var family: Family? = null
    private var original: Surprise? = null
    private var initialForm = SurpriseForm()
    private var showErrors = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val repo = surprises ?: return unavailable()
        val memberRepo = members ?: return unavailable()
        val loaded = try {
            val fam = memberRepo.observeFamily().first()
            val existing = surpriseId?.let { repo.observeSurprise(it).first() ?: return unavailable() }
            val others = if (existing == null && beneficiaryId != null) otherSurprises(repo, beneficiaryId) else Others()
            Loaded(fam, existing, others)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return unavailable()
        val fam = loaded.family
        val existing = loaded.existing
        val me = fam.members.firstOrNull { it.id == currentMemberId } ?: return unavailable()
        val beneficiary = fam.members.firstOrNull { it.id == (existing?.beneficiaryId ?: beneficiaryId) } ?: return unavailable()
        val allowed = if (existing != null) existing.creatorId == me.id else canCreateSurprise(me) && beneficiary.id != me.id
        if (!allowed) return unavailable()
        family = fam
        original = existing
        initialForm = existing?.let { SurpriseForm(it.title, it.occasion, it.date, it.memberIds.toSet(), dateTouched = true) }
            ?: defaultForm(fam, me, beneficiary)
        _uiState.value = SurpriseEditUiState.Editing(
            form = initialForm,
            isNew = existing == null,
            beneficiary = beneficiary,
            meId = me.id,
            candidates = surpriseCandidates(fam, beneficiary.id).sortedBy { it.firstName.lowercase() },
            households = fam.households.sortedBy { it.name.lowercase() },
            mine = loaded.others.mine,
            joinable = if (canJoinSurprise(me)) loaded.others.joinable else emptyList(),
        )
    }

    private data class Others(val mine: List<Surprise> = emptyList(), val joinable: List<OpenSurprise> = emptyList())

    private data class Loaded(val family: Family, val existing: Surprise?, val others: Others)

    /** The surprises already in preparation for [forId]: the ones I am in, and the others. */
    private suspend fun otherSurprises(repo: SurpriseRepository, forId: String): Others {
        val mine = repo.observeMine(currentMemberId).first().filter { it.beneficiaryId == forId }
        val mineIds = mine.map { it.id }.toSet()
        return Others(mine, repo.observeOpenFor(forId).first().filter { it.id !in mineIds })
    }

    /** A new surprise: its nearest occasion and that day, seen by the whole family but the beneficiary. */
    private fun defaultForm(fam: Family, me: Member, beneficiary: Member): SurpriseForm {
        val occasion = nearestSurpriseOccasion(beneficiary, today())
        return SurpriseForm(
            occasion = occasion,
            date = surpriseDateFor(occasion, beneficiary, today()),
            memberIds = surpriseCandidates(fam, beneficiary.id).map { it.id }.toSet() + me.id,
        )
    }

    private fun unavailable() {
        _uiState.value = SurpriseEditUiState.Unavailable
    }

    fun update(change: (SurpriseForm) -> SurpriseForm) {
        val state = _uiState.value as? SurpriseEditUiState.Editing ?: return
        var form = change(state.form)
        if (form.occasion != state.form.occasion && !form.dateTouched) {
            form = form.copy(date = surpriseDateFor(form.occasion, state.beneficiary, today()))
        }
        // I am always in my own surprise.
        form = form.copy(memberIds = form.memberIds + state.meId)
        _uiState.value = state.copy(
            form = form,
            errors = errorsFor(form),
            hasChanges = form.copy(dateTouched = false) != initialForm.copy(dateTouched = false),
            saveFailed = false,
        )
    }

    /** Saves; an empty title becomes [suggestedTitle], the one shown in the field. */
    fun save(suggestedTitle: String) {
        val state = _uiState.value as? SurpriseEditUiState.Editing ?: return
        val repo = surprises ?: return
        val fam = family ?: return
        if (state.isSaving) return
        showErrors = true
        val errors = errorsFor(state.form)
        val date = state.form.date
        if (errors.isNotEmpty() || date == null) {
            _uiState.value = state.copy(errors = errors)
            return
        }
        val me = fam.members.firstOrNull { it.id == state.meId } ?: return
        val surprise = Surprise(
            id = original?.id ?: repo.newSurpriseId(),
            beneficiaryId = state.beneficiary.id,
            title = state.form.title.trim().ifEmpty { suggestedTitle }.take(Surprise.MAX_TITLE),
            occasion = state.form.occasion,
            date = date,
            memberIds = surpriseMembers(fam, state.beneficiary.id, original?.creatorId ?: me.id, state.form.memberIds),
            creatorId = original?.creatorId ?: me.id,
            creatorName = original?.creatorName ?: me.firstName,
        )
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            if (write { repo.saveSurprise(surprise, isNew = original == null) }) {
                _uiState.value = SurpriseEditUiState.Done(surprise.id)
            } else {
                (_uiState.value as? SurpriseEditUiState.Editing)?.let { _uiState.value = it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    fun delete() {
        val state = _uiState.value as? SurpriseEditUiState.Editing ?: return
        val repo = surprises ?: return
        val existing = original ?: return
        if (state.isSaving) return
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            if (write { repo.deleteSurprise(existing.id) }) {
                _uiState.value = SurpriseEditUiState.Done(null)
            } else {
                (_uiState.value as? SurpriseEditUiState.Editing)?.let { _uiState.value = it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    /** « Rejoindre » the surprise already in preparation rather than start a second one. */
    fun join(id: String) {
        val state = _uiState.value as? SurpriseEditUiState.Editing ?: return
        val repo = surprises ?: return
        if (state.isSaving || state.joinable.none { it.id == id }) return
        _uiState.value = state.copy(isSaving = true, joinFailed = false)
        viewModelScope.launch {
            if (write { repo.joinSurprise(id, state.meId) }) {
                _uiState.value = SurpriseEditUiState.Done(id)
            } else {
                (_uiState.value as? SurpriseEditUiState.Editing)?.let { _uiState.value = it.copy(isSaving = false, joinFailed = true) }
            }
        }
    }

    /** « Ouvrir » a surprise I already prepare for the same person. */
    fun open(id: String) {
        val state = _uiState.value as? SurpriseEditUiState.Editing ?: return
        if (state.mine.any { it.id == id }) _uiState.value = SurpriseEditUiState.Done(id)
    }

    private fun errorsFor(form: SurpriseForm): Set<SurpriseFormError> = if (!showErrors) {
        emptySet()
    } else {
        buildSet {
            if (form.title.trim().length > Surprise.MAX_TITLE) add(SurpriseFormError.TitleTooLong)
            when {
                form.date == null -> add(SurpriseFormError.DateMissing)
                form.date < today() -> add(SurpriseFormError.DatePast)
            }
        }
    }

    private suspend fun write(block: suspend () -> Unit): Boolean = try {
        // A write still waiting for the network after the delay is kept by Firestore and goes out later.
        withTimeoutOrNull(writeTimeoutMillis) { block() }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}
