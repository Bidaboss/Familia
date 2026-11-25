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
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.ideasListId
import fr.familia.app.core.model.wishesListId
import fr.familia.app.core.photo.PhotoEdit
import fr.familia.app.core.photo.SinglePhoto
import fr.familia.app.core.photo.SinglePhotoSaver
import fr.familia.app.feature.gifts.addsVisibleItem
import fr.familia.app.feature.gifts.isKeptFrom
import fr.familia.app.feature.gifts.keepsListsOf
import fr.familia.app.feature.gifts.parseEuros
import fr.familia.app.core.model.householdGroups
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
    /** « Je l'ai déjà acheté, une participation me ferait plaisir » (récit 68). */
    val alreadyBought: Boolean = false,
    /** « Cadeau commun avec… » (récit 73): the member of the household it is shared with, or null. */
    val coBeneficiaryId: String? = null,
)

/** Where a link shared from another app goes (récit 50): my wishes when [memberId] is null, else an idea for them. */
data class ShareTarget(val memberId: String?, val name: String)

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
        /** Récit 50: « Pour moi » first, then « Une idée pour… » each other member; empty hides the choice. */
        val targets: List<ShareTarget> = emptyList(),
        /** The member of the chosen idea, null for my wishes. */
        val targetId: String? = null,
        /** « Déjà acheté » (récit 68): on a wish its keeper writes, while the switch is on. */
        val showAlreadyBought: Boolean = false,
        /** Someone already takes part: the box stays checked. */
        val alreadyBoughtLocked: Boolean = false,
        /**
         * « Cadeau commun avec… » (récit 73): the other members of the household of the person, on a
         * wish their keeper writes; empty hides the choice.
         */
        val coChoices: List<ShareTarget> = emptyList(),
        /**
         * The other person of a wish already shared (récit 73): « Cadeau commun avec » stays as it is,
         * so that the member it was shared with never reads its reservation.
         */
        val sharedWith: String? = null,
        /** Edited by whoever shares it (récit 73): it is not removed here. */
        val sharedByOther: String? = null,
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
    startListId: String,
    private val itemId: String?,
    /** Keeps the draft when Android closes the app in the background. */
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val writeTimeoutMillis: Long = 4_000,
    /** A new item filled from a link shared by another app (récit 8 bis): a change to keep or drop. */
    private val shared: GiftItemForm? = null,
    /** Reads the title, picture and price of a shared link (récit 8 bis). */
    previews: LinkPreviewRepository? = null,
    /** Sends and removes the photo (PRD, récit 44); null hides it, as when « Plus de photos » is off. */
    private val photos: SinglePhotoSaver? = null,
    /**
     * Récit 50: a new item from « Ajouter à mes envies ». An adult may then make it « Une idée pour… »
     * another member, hidden from them; a child adds only to their own wishes.
     */
    private val fromShare: Boolean = false,
    /** The switch « Déjà acheté » (récit 68), read with isOnForFamily. */
    private val alreadyBought: Boolean = false,
) : ViewModel() {

    /** Récit 50 reads nothing from the shop: no network call on a link from « Ajouter à mes envies ». */
    private val previews: LinkPreviewRepository? = previews.takeIf { !fromShare }

    /** The list the item goes to: [startListId], or the one chosen with [chooseTarget]. */
    private var listId: String = startListId
    private var family: Family? = null

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

    /** Who edits the wish because they share it (récit 73), not as its author. Declared before init. */
    private var sharedEditor = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val repo = gifts ?: return unavailable()
        val memberRepo = members ?: return unavailable()
        val loadedFamily = try {
            memberRepo.observeFamily().first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return unavailable()
        val current = loadedFamily.members.firstOrNull { it.id == currentMemberId } ?: return unavailable()
        family = loadedFamily
        me = current
        val targets = if (fromShare && itemId == null) shareTargets(loadedFamily, current) else emptyList()
        // The choice made before Android closed the app in the background.
        val savedTarget = saved.get<String>(DRAFT_TARGET)?.takeIf { id -> targets.any { it.memberId == id } }
        if (savedTarget != null) listId = ideasListId(savedTarget)
        val stored = try {
            repo.observeList(listId).first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val target = stored ?: emptyWishes(loadedFamily, current, listId) ?: return unavailable()
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
        // Récit 73: whoever shares the wish, or keeps the lists of a child who does, edits it too.
        val coEditor = existing?.coBeneficiaryId
            ?.let { co -> loadedFamily.members.firstOrNull { it.id == co } }
            ?.takeIf { co -> target.kind == GiftListKind.Wishes && keepsListsOf(current, co) }
        if (itemId != null && (existing == null || (existing.authorId != current.id && coEditor == null))) return unavailable()
        val beneficiary = loadedFamily.members.firstOrNull { it.id == target.beneficiaryId } ?: return unavailable()
        sharedEditor = coEditor != null && existing?.authorId != current.id

        list = target
        listExists = stored != null
        original = existing
        visible = existing?.visibleToBeneficiary ?: addsVisibleItem(target, current, loadedFamily)
        initialForm = existing?.let {
            GiftItemForm(
                title = it.title,
                url = it.url.orEmpty(),
                price = it.price?.let(::priceText).orEmpty(),
                note = it.note.orEmpty(),
                occasion = it.occasion,
                alreadyBought = it.alreadyBought,
                coBeneficiaryId = it.coBeneficiaryId,
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
            showOccasion = target.kind == GiftListKind.Wishes,
            forMe = beneficiary.id == current.id,
            showPhoto = photos != null,
            storedPhoto = existing?.photo,
            targets = targets,
            targetId = savedTarget,
            showAlreadyBought = showsAlreadyBought(target),
            alreadyBoughtLocked = existing?.let { it.alreadyBought && it.contributedTotal > 0 } == true,
            coChoices = if (existing?.coBeneficiaryId != null) emptyList() else coChoices(loadedFamily, target),
            sharedWith = if (sharedEditor) beneficiary.firstName else coEditorName(loadedFamily, existing),
            sharedByOther = if (sharedEditor) beneficiary.firstName else null,
        )
        if (shared != null && existing == null) readPreview(shared.url)
    }

    /**
     * « Cadeau commun avec… » (récit 73): the other members of the household of the person, children
     * included, in the order of the Famille tab; only on a wish of « Mes envies » the person sees.
     */
    private fun coChoices(family: Family, target: GiftList): List<ShareTarget> {
        if (target.kind != GiftListKind.Wishes || !visible || original?.coBeneficiaryId != null) return emptyList()
        val owner = family.members.firstOrNull { it.id == target.beneficiaryId } ?: return emptyList()
        return householdGroups(family).flatMap { it.members }
            .filter { it.householdId == owner.householdId && it.id != owner.id }
            .map { ShareTarget(it.id, it.firstName) }
    }

    /** Who an existing wish is already shared with, as its keeper sees it. */
    private fun coEditorName(family: Family, existing: GiftItem?): String? =
        existing?.coBeneficiaryId?.let { co -> family.members.firstOrNull { it.id == co }?.firstName }

    /** « Déjà acheté » only on a wish the person sees: the one their keeper writes (récit 68). */
    private fun showsAlreadyBought(target: GiftList): Boolean = alreadyBought && target.kind == GiftListKind.Wishes && visible

    /** « Pour moi », then « Une idée pour… » each other member: for an adult only (récit 50). */
    private fun shareTargets(family: Family, me: Member): List<ShareTarget> {
        if (me.profile != MemberProfile.Adult) return emptyList()
        // By household, in the order of the Famille tab (retour d'essai du 17/11).
        val others = householdGroups(family).flatMap { group -> group.members.filter { it.id != me.id } }
        if (others.isEmpty()) return emptyList()
        return listOf(ShareTarget(null, me.firstName)) + others.map { ShareTarget(it.id, it.firstName) }
    }

    /**
     * Récit 50: the shared link goes to my wishes ([memberId] null) or becomes an idea for that
     * member, in their « Idées de la famille », hidden from them. What was typed stays.
     */
    fun chooseTarget(memberId: String?) {
        val state = _uiState.value as? GiftItemEditUiState.Editing ?: return
        if (state.isSaving || state.targetId == memberId || state.targets.none { it.memberId == memberId }) return
        val repo = gifts ?: return
        val current = me ?: return
        val known = family ?: return
        val id = memberId?.let(::ideasListId) ?: wishesListId(current.id)
        viewModelScope.launch {
            val stored = try {
                repo.observeList(id).first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch markFailed()
            }
            val target = (stored ?: emptyWishes(known, current, id))?.takeIf { !it.revealed } ?: return@launch markFailed()
            val beneficiary = known.members.firstOrNull { it.id == target.beneficiaryId } ?: return@launch markFailed()
            // A save started meanwhile keeps the list it started with.
            val now = (_uiState.value as? GiftItemEditUiState.Editing)?.takeIf { !it.isSaving } ?: return@launch
            listId = id
            list = target
            listExists = stored != null
            visible = addsVisibleItem(target, current, known)
            saved[DRAFT_TARGET] = memberId
            val showOccasion = target.kind == GiftListKind.Wishes
            _uiState.value = now.copy(
                targetId = memberId,
                hiddenFromBeneficiary = !visible && beneficiary.id != current.id,
                beneficiaryName = beneficiary.firstName,
                showOccasion = showOccasion,
                forMe = beneficiary.id == current.id,
                saveFailed = false,
                showAlreadyBought = showsAlreadyBought(target),
                coChoices = coChoices(known, target),
                form = now.form.copy(coBeneficiaryId = now.form.coBeneficiaryId?.takeIf { co -> coChoices(known, target).any { it.memberId == co } }),
            )
        }
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

    private fun emptyWishes(family: Family, current: Member, listId: String): GiftList? {
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
        return GiftItemForm(
            title, saved[DRAFT_URL] ?: "", saved[DRAFT_PRICE] ?: "", saved[DRAFT_NOTE] ?: "", occasion,
            alreadyBought = saved[DRAFT_BOUGHT] ?: false,
            coBeneficiaryId = saved[DRAFT_CO],
        )
    }

    fun update(change: (GiftItemForm) -> GiftItemForm) {
        val state = _uiState.value as? GiftItemEditUiState.Editing ?: return
        val form = change(state.form)
        saved[DRAFT_TITLE] = form.title
        saved[DRAFT_URL] = form.url
        saved[DRAFT_PRICE] = form.price
        saved[DRAFT_NOTE] = form.note
        saved[DRAFT_OCCASION] = form.occasion?.name
        saved[DRAFT_BOUGHT] = form.alreadyBought
        saved[DRAFT_CO] = form.coBeneficiaryId
        _uiState.value = state.copy(
            form = form,
            errors = errorsFor(form),
            hasChanges = form != initialForm,
            saveFailed = false,
            imageUrl = imageFor(form),
        )
    }

    /**
     * « Coller » (correctif E6): the link of the clipboard, then what its shop page says fills what
     * the member has not typed yet, as for a shared link (récit 8 bis).
     */
    fun pasteLink(url: String) {
        update { it.copy(url = url) }
        val state = _uiState.value as? GiftItemEditUiState.Editing ?: return
        if (previews == null || normalizeLink(url) == null) return
        _uiState.value = state.copy(previewLoading = true)
        viewModelScope.launch { readPreview(url) }
    }

    fun save() {
        val state = _uiState.value as? GiftItemEditUiState.Editing ?: return
        val repo = gifts ?: return
        val target = list ?: return
        val author = me ?: return
        val listId = listId
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
            // Switch off, the wish keeps what it had; while someone takes part, it stays checked.
            alreadyBought = if (state.showAlreadyBought) form.alreadyBought || state.alreadyBoughtLocked else original?.alreadyBought == true,
            // Récit 73: chosen once by the keeper of the wish, then nobody changes it.
            coBeneficiaryId = form.coBeneficiaryId?.takeIf { co -> state.coChoices.any { it.memberId == co } }
                ?: original?.coBeneficiaryId?.takeIf { state.coChoices.isEmpty() },
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
private const val DRAFT_TARGET = "draft.target"
private const val DRAFT_BOUGHT = "draft.alreadyBought"
private const val DRAFT_CO = "draft.coBeneficiary"

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
