package fr.familia.app.feature.gifts.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.wishesListId
import fr.familia.app.feature.gifts.addsVisibleItem
import fr.familia.app.feature.gifts.isKeptFrom
import fr.familia.app.feature.gifts.keepsListsOf
import fr.familia.app.feature.gifts.parseEuros
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class GiftItemForm(val title: String = "", val url: String = "", val price: String = "", val note: String = "")

enum class GiftItemFormError { TitleMissing, TitleTooLong, BadLink, BadPrice, NoteTooLong }

sealed interface GiftItemEditUiState {
    data object Loading : GiftItemEditUiState

    data object Unavailable : GiftItemEditUiState

    data class Editing(
        val form: GiftItemForm,
        val isNew: Boolean,
        /** True when the beneficiary will not see this item: a surprise idea. */
        val hiddenFromBeneficiary: Boolean,
        val beneficiaryName: String,
        val errors: Set<GiftItemFormError> = emptySet(),
        val hasChanges: Boolean = false,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : GiftItemEditUiState

    data object Done : GiftItemEditUiState
}

/**
 * Add an item to a list ([itemId] null), or edit or remove one (PRD, récit 8). The first wish of
 * a « Mes envies » creates the list.
 */
class GiftItemEditViewModel(
    private val gifts: GiftRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val listId: String,
    private val itemId: String?,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GiftItemEditUiState>(GiftItemEditUiState.Loading)
    val uiState: StateFlow<GiftItemEditUiState> = _uiState.asStateFlow()

    private var list: GiftList? = null
    private var listExists = false
    private var original: GiftItem? = null
    private var me: Member? = null
    private var visible = false
    private var initialForm = GiftItemForm()
    private var showErrors = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val repo = gifts ?: return unavailable()
        val memberRepo = members ?: return unavailable()
        val loaded = try {
            val family = memberRepo.observeFamily().first()
            family to repo.observeList(listId).first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return unavailable()
        val (family, stored) = loaded
        val current = family.members.firstOrNull { it.id == currentMemberId } ?: return unavailable()
        val target = stored ?: emptyWishes(family, current) ?: return unavailable()
        if (target.revealed) return unavailable()
        val existing = if (itemId == null || stored == null) {
            null
        } else {
            try {
                repo.observeItems(listId, hiddenFromMe = isKeptFrom(target, current)).first().firstOrNull { it.id == itemId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        if (itemId != null && (existing == null || existing.authorId != current.id)) return unavailable()
        val beneficiary = family.members.firstOrNull { it.id == target.beneficiaryId } ?: return unavailable()

        list = target
        listExists = stored != null
        original = existing
        me = current
        visible = existing?.visibleToBeneficiary ?: addsVisibleItem(target, current, family)
        initialForm = existing?.let {
            GiftItemForm(
                title = it.title,
                url = it.url.orEmpty(),
                price = it.price?.let { p -> if (p % 1.0 == 0.0) p.toLong().toString() else p.toString().replace('.', ',') }.orEmpty(),
                note = it.note.orEmpty(),
            )
        } ?: GiftItemForm()
        _uiState.value = GiftItemEditUiState.Editing(
            form = initialForm,
            isNew = existing == null,
            hiddenFromBeneficiary = !visible && beneficiary.id != current.id,
            beneficiaryName = beneficiary.firstName,
        )
    }

    private fun emptyWishes(family: Family, current: Member): GiftList? {
        val owner = family.members.firstOrNull { wishesListId(it.id) == listId } ?: return null
        if (!keepsListsOf(current, owner)) return null
        return GiftList(listId, GiftListKind.Wishes, "Mes envies", null, null, owner.id, emptyList(), current.id, current.firstName)
    }

    private fun unavailable() {
        _uiState.value = GiftItemEditUiState.Unavailable
    }

    fun update(change: (GiftItemForm) -> GiftItemForm) {
        val state = _uiState.value as? GiftItemEditUiState.Editing ?: return
        val form = change(state.form)
        _uiState.value = state.copy(form = form, errors = errorsFor(form), hasChanges = form != initialForm, saveFailed = false)
    }

    fun save() {
        val state = _uiState.value as? GiftItemEditUiState.Editing ?: return
        val repo = gifts ?: return
        val target = list ?: return
        val author = me ?: return
        if (state.isSaving) return
        showErrors = true
        val errors = errorsFor(state.form)
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(errors = errors)
            return
        }
        val form = state.form
        val item = GiftItem(
            id = original?.id ?: repo.newItemId(listId),
            title = form.title.trim(),
            url = normalizeLink(form.url),
            price = parseEuros(form.price),
            note = form.note.trim().ifEmpty { null },
            authorId = original?.authorId ?: author.id,
            authorName = original?.authorName ?: author.firstName,
            visibleToBeneficiary = visible,
            imageUrl = original?.imageUrl,
        )
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            val ok = write {
                // Two writes in order: the rules of the item read the list, which must exist first.
                if (!listExists) repo.saveList(target, isNew = true)
                repo.saveItem(listId, item, isNew = original == null)
            }
            if (ok) _uiState.value = GiftItemEditUiState.Done else markFailed()
        }
    }

    fun delete() {
        val state = _uiState.value as? GiftItemEditUiState.Editing ?: return
        val repo = gifts ?: return
        val item = original ?: return
        if (state.isSaving) return
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            if (write { repo.deleteItem(listId, item.id) }) _uiState.value = GiftItemEditUiState.Done else markFailed()
        }
    }

    private fun errorsFor(form: GiftItemForm): Set<GiftItemFormError> = if (!showErrors) emptySet() else validateItem(form)

    private fun markFailed() {
        val state = _uiState.value as? GiftItemEditUiState.Editing ?: return
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

/** The same limits as the security rules. */
fun validateItem(form: GiftItemForm): Set<GiftItemFormError> = buildSet {
    val title = form.title.trim()
    if (title.isEmpty()) add(GiftItemFormError.TitleMissing)
    if (title.length > 80) add(GiftItemFormError.TitleTooLong)
    if (form.url.isNotBlank() && normalizeLink(form.url) == null) add(GiftItemFormError.BadLink)
    if (form.price.isNotBlank() && (parseEuros(form.price)?.let { it <= 100_000 } != true)) add(GiftItemFormError.BadPrice)
    if (form.note.trim().length > 500) add(GiftItemFormError.NoteTooLong)
}

/** « amazon.fr/… » becomes « https://amazon.fr/… »; anything that is not a web link gives null. */
fun normalizeLink(text: String): String? {
    val link = text.trim()
    if (link.isEmpty() || link.length > 500 || link.any { it.isWhitespace() }) return null
    val withScheme = if (link.startsWith("http://") || link.startsWith("https://")) link else "https://$link"
    val host = withScheme.substringAfter("://").substringBefore('/')
    return withScheme.takeIf { host.contains('.') && withScheme.length <= 500 }
}
