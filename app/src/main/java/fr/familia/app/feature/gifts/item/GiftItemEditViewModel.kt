package fr.familia.app.feature.gifts.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.data.LinkPreviewRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.GiftList
import fr.familia.app.core.model.GiftOccasion
import fr.familia.app.core.model.GiftListKind
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.ideasListId
import fr.familia.app.core.model.wishesListId
import fr.familia.app.core.photo.PhotoEdit
import fr.familia.app.core.photo.SinglePhoto
import fr.familia.app.core.photo.SinglePhotoSaver
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

data class GiftItemForm(
    val title: String = "",
    val url: String = "",
    val price: String = "",
    val note: String = "",
    /** Récit 9 ter: Christmas or a birthday; null for « Quand tu veux ». */
    val occasion: GiftOccasion? = null,
    /** The photo (récit 44): sent by the ViewModel before the item, not kept in the draft. */
    val photo: PhotoEdit = PhotoEdit.Unchanged,
)

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
        /** The picture of the shop page, kept with the item while its link stays the same. */
        val imageUrl: String? = null,
        /** The preview of a shared link is being read. */
        val previewLoading: Boolean = false,
        /** A wish says what it is for (récit 9 ter): « Quand tu veux », Christmas or the birthday. */
        val showOccasion: Boolean = false,
        /** The wish is for the member themselves: « mon anniversaire », else « son anniversaire ». */
        val forMe: Boolean = true,
        /** « Plus de photos » is on: the form shows the photo, [storedPhoto] being the saved one. */
        val showPhoto: Boolean = false,
        val storedPhoto: String? = null,
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
    /** Keeps the draft when Android closes the app in the background. */
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val writeTimeoutMillis: Long = 4_000,
    /** A new item filled from a link shared by another app (récit 8 bis): a change to keep or drop. */
    private val shared: GiftItemForm? = null,
    /** Reads the title, picture and price of a shared link (récit 8 bis). */
    private val previews: LinkPreviewRepository? = null,
    /** The switch « Liste unique » (récit 9 ter): a wish then says what it is for. */
    private val singleList: Boolean = false,
    /** Sends and removes the photo (PRD, récit 44); null hides it, as when « Plus de photos » is off. */
    private val photos: SinglePhotoSaver? = null,
) : ViewModel() {

    /** The picture read for [previewedUrl]: dropped if the member changes the link. */
    private var previewImage: String? = null
    private var previewedUrl: String? = null

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
                price = it.price?.let(::priceText).orEmpty(),
                note = it.note.orEmpty(),
                occasion = it.occasion,
            )
        } ?: GiftItemForm()
        val form = restoredDraft() ?: shared?.takeIf { existing == null } ?: initialForm
        _uiState.value = GiftItemEditUiState.Editing(
            form = form,
            hasChanges = form != initialForm,
            isNew = existing == null,
            hiddenFromBeneficiary = !visible && beneficiary.id != current.id,
            beneficiaryName = beneficiary.firstName,
            imageUrl = existing?.imageUrl,
            previewLoading = shared != null && existing == null && previews != null,
            showOccasion = singleList && target.kind == GiftListKind.Wishes,
            forMe = beneficiary.id == current.id,
            showPhoto = photos != null,
            storedPhoto = existing?.photo,
        )
        if (shared != null && existing == null) readPreview(shared.url)
    }

    /** Fills what the member has not typed yet with what the shop page says. */
    private suspend fun readPreview(url: String) {
        val repo = previews ?: return
        val preview = try {
            repo.preview(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val state = _uiState.value as? GiftItemEditUiState.Editing ?: return
        if (preview == null) {
            _uiState.value = state.copy(previewLoading = false)
            return
        }
        previewImage = preview.imageUrl
        previewedUrl = url
        val form = state.form.copy(
            title = state.form.title.ifBlank { preview.title.orEmpty() },
            price = state.form.price.ifBlank { preview.price?.let(::priceText).orEmpty() },
        )
        _uiState.value = state.copy(
            form = form,
            hasChanges = form != initialForm,
            imageUrl = imageFor(form),
            previewLoading = false,
        )
    }

    /** The picture goes with its link: none once the member types another one. */
    private fun imageFor(form: GiftItemForm): String? {
        val link = normalizeLink(form.url)
        return if (link != null && previewedUrl != null && link == normalizeLink(previewedUrl.orEmpty())) {
            previewImage
        } else {
            original?.imageUrl?.takeIf { link == original?.url }
        }
    }

    private fun emptyWishes(family: Family, current: Member): GiftList? {
        // The first idea of the family for someone creates their « Idées de la famille » (récit 9 ter).
        family.members.firstOrNull { ideasListId(it.id) == listId }?.let { owner ->
            if (owner.id == current.id) return null
            return GiftList(listId, GiftListKind.Ideas, "Idées de la famille", null, null, owner.id, emptyList(), current.id, current.firstName)
        }
        val owner = family.members.firstOrNull { wishesListId(it.id) == listId } ?: return null
        if (!keepsListsOf(current, owner)) return null
        return GiftList(listId, GiftListKind.Wishes, "Mes envies", null, null, owner.id, emptyList(), current.id, current.firstName)
    }

    private fun unavailable() {
        _uiState.value = GiftItemEditUiState.Unavailable
    }

    private fun restoredDraft(): GiftItemForm? {
        val title = saved.get<String>(DRAFT_TITLE) ?: return null
        val occasion = GiftOccasion.entries.firstOrNull { it.name == saved.get<String>(DRAFT_OCCASION) }
        return GiftItemForm(title, saved[DRAFT_URL] ?: "", saved[DRAFT_PRICE] ?: "", saved[DRAFT_NOTE] ?: "", occasion)
    }

    fun update(change: (GiftItemForm) -> GiftItemForm) {
        val state = _uiState.value as? GiftItemEditUiState.Editing ?: return
        val form = change(state.form)
        saved[DRAFT_TITLE] = form.title
        saved[DRAFT_URL] = form.url
        saved[DRAFT_PRICE] = form.price
        saved[DRAFT_NOTE] = form.note
        saved[DRAFT_OCCASION] = form.occasion?.name
        _uiState.value = state.copy(
            form = form,
            errors = errorsFor(form),
            hasChanges = form != initialForm,
            saveFailed = false,
            imageUrl = imageFor(form),
        )
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
            imageUrl = state.imageUrl,
            // Only a wish says what it is for; an idea keeps none.
            occasion = form.occasion?.takeIf { state.showOccasion } ?: original?.occasion?.takeIf { !state.showOccasion },
        )
        val before = original?.photo
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            // In order: the rules of the photo and of the item read the list, which must exist first,
            // and the item names only a photo already sent.
            if (!listExists) {
                if (!write { repo.saveList(target, isNew = true) }) return@launch markFailed()
                listExists = true
            }
            val photo = try {
                // A removed photo gives null, which must stay null: no « ?: before » here.
                photos?.prepare(before, form.photo) { SinglePhoto.giftItem(listId, visible, author.id) }
                    ?: before.takeIf { photos == null }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch markFailed()
            }
            if (write { repo.saveItem(listId, item.copy(photo = photo), isNew = original == null) }) {
                photos?.cleanUp(previous = before, saved = photo)
                _uiState.value = GiftItemEditUiState.Done
            } else {
                photos?.cleanUp(previous = photo, saved = before)
                markFailed()
            }
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

private const val DRAFT_TITLE = "draft.title"
private const val DRAFT_URL = "draft.url"
private const val DRAFT_PRICE = "draft.price"
private const val DRAFT_NOTE = "draft.note"
private const val DRAFT_OCCASION = "draft.occasion"

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

/** « 25 » or « 12,50 », as the member would type it. */
internal fun priceText(price: Double): String =
    if (price % 1.0 == 0.0) price.toLong().toString() else String.format(java.util.Locale.FRANCE, "%.2f", price)
