package fr.familia.app.feature.capsules

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.CapsuleRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Capsule
import fr.familia.app.core.model.Member
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** What the member writes. */
data class CapsuleForm(
    val text: String = "",
    val recipientIds: Set<String> = emptySet(),
    val openAt: LocalDate? = null,
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

sealed interface CapsuleEditUiState {
    data object Loading : CapsuleEditUiState
    data object Unavailable : CapsuleEditUiState

    data class Editing(
        val form: CapsuleForm,
        val today: LocalDate,
        val members: List<RecipientChoice>,
        val errors: Set<CapsuleFormError> = emptySet(),
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : CapsuleEditUiState

    data class Done(val capsuleId: String) : CapsuleEditUiState
}

/**
 * « Nouvelle capsule » (PRD, récit 39): a text, the recipients and the opening day. A capsule
 * never changes once sealed. The draft survives Android stopping the app.
 */
class CapsuleEditViewModel(
    private val capsules: CapsuleRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val today: () -> LocalDate = LocalDate::now,
    private val writeTimeoutMillis: Long = 4_000,
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
        )
        val choices = family.members.sortedBy { it.firstName.lowercase() }.map { RecipientChoice(it.id, it.firstName) }
        return CapsuleEditUiState.Editing(form, today(), choices)
    }

    private fun edit(change: (CapsuleForm) -> CapsuleForm) {
        state.update { current ->
            if (current !is CapsuleEditUiState.Editing || current.isSaving) return@update current
            val form = change(current.form)
            savedState[TEXT] = form.text
            savedState[RECIPIENTS] = ArrayList(form.recipientIds)
            savedState[OPEN_AT] = form.openAt?.toString()
            current.copy(form = form, errors = if (showErrors) validate(form, current.today) else emptySet(), saveFailed = false)
        }
    }

    fun setText(text: String) = edit { it.copy(text = text.take(Capsule.MAX_TEXT)) }
    fun toggleRecipient(id: String) = edit { it.copy(recipientIds = if (id in it.recipientIds) it.recipientIds - id else it.recipientIds + id) }
    fun setOpenAt(day: LocalDate) = edit { it.copy(openAt = day) }

    /** Seals the capsule; offline, Firestore keeps it and sends it later. */
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
        val repo = capsules ?: return s.copy(saveFailed = true)
        val author = me ?: return s.copy(saveFailed = true)
        val capsule = Capsule(
            id = repo.newCapsuleId(),
            text = s.form.text.trim(),
            photoCount = 0,
            recipientIds = s.form.recipientIds.sorted(),
            openAt = s.form.openAt ?: return s.copy(saveFailed = true),
            openedFor = emptyList(),
            openedAt = null,
            authorId = author.id,
            authorName = author.firstName,
            createdAt = null,
        )
        state.value = s.copy(isSaving = true, saveFailed = false)
        return try {
            withTimeoutOrNull(writeTimeoutMillis) { repo.createCapsule(capsule) }
            listOf(TEXT, RECIPIENTS, OPEN_AT).forEach { savedState.remove<Any>(it) }
            CapsuleEditUiState.Done(capsule.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            s.copy(isSaving = false, saveFailed = true)
        }
    }

    private companion object {
        const val TEXT = "capsule_text"
        const val RECIPIENTS = "capsule_recipients"
        const val OPEN_AT = "capsule_open_at"
    }
}
