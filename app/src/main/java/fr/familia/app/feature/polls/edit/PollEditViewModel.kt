package fr.familia.app.feature.polls.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.PollRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Poll
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
        initialForm = PollForm(askedIds = everyone)
        // My household first, then the others; in each, the adults then the children.
        val households = loaded.households.sortedWith(compareBy({ it.id != current.householdId }, { it.name.lowercase() }))
        val groups = households.mapNotNull { household ->
            loaded.members
                .filter { it.householdId == household.id }
                .sortedWith(compareBy({ it.profile != MemberProfile.Adult }, { it.firstName.lowercase() }))
                .takeIf { it.isNotEmpty() }
                ?.let { AskGroup(household.name, it) }
        }
        _uiState.value = PollEditUiState.Editing(
            form = initialForm,
            groups = groups,
            shortcuts = listOf(AskShortcut(null, everyone)) + groups.map { g -> AskShortcut(g.name, g.members.map { it.id }.toSet()) },
        )
    }

    private fun unavailable() {
        _uiState.value = PollEditUiState.Unavailable
    }

    fun update(change: (PollForm) -> PollForm) {
        val state = _uiState.value as? PollEditUiState.Editing ?: return
        val form = change(state.form)
        _uiState.value = state.copy(
            form = form,
            errors = if (showErrors) validate(form, today()) else emptySet(),
            hasChanges = form != initialForm,
            saveFailed = false,
        )
    }

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
                val failed = _uiState.value as? PollEditUiState.Editing ?: return@launch
                _uiState.value = failed.copy(isSaving = false, saveFailed = true)
            }
        }
    }
}
