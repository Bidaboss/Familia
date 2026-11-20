package fr.familia.app.feature.helprequests.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.HelpRequestRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.model.HelpStatus
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.PlaceKind
import fr.familia.app.core.model.helpRequestPhotoBase
import fr.familia.app.core.photo.PhotoEdit
import fr.familia.app.core.photo.SinglePhotoSaver
import fr.familia.app.core.ui.PickGroup
import fr.familia.app.feature.helprequests.canManageHelp
import fr.familia.app.feature.helprequests.helpTargets
import fr.familia.app.feature.helprequests.notifiableAdults
import fr.familia.app.feature.helprequests.requiredNotifyIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/**
 * Ask for help ([requestId] null), duplicate a request ([duplicateOf], carte PRODUIT-23), or edit
 * and widen one (récits 13, 14 and 17). Offline, the write lands in the cache at once and
 * Firestore sends it when the network is back.
 */
class HelpRequestEditViewModel(
    private val requests: HelpRequestRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val requestId: String? = null,
    private val duplicateOf: String? = null,
    private val today: () -> LocalDate = LocalDate::now,
    private val writeTimeoutMillis: Long = 4_000,
    /** Sends and removes the photo of the request (Kévin 16/11); null: no photo can be joined. */
    private val photos: SinglePhotoSaver? = null,
    private val photoTimeoutMillis: Long = 60_000,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HelpRequestEditUiState>(HelpRequestEditUiState.Loading)
    val uiState: StateFlow<HelpRequestEditUiState> = _uiState.asStateFlow()

    private var original: HelpRequest? = null
    private var family: Family? = null
    private var me: Member? = null
    private var editedId = ""
    private var initialForm = HelpRequestForm()
    private var showErrors = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val requestRepo = requests ?: return unavailable()
        val memberRepo = members ?: return unavailable()
        val loaded = try {
            val family = memberRepo.observeFamily().first()
            val existing = requestId?.let { requestRepo.observeRequest(it).first() }
            val source = duplicateOf?.let { requestRepo.observeRequest(it).first() }
            // Récit 14: the people of my previous request are ticked again by default.
            val previous = if (requestId == null && source == null) {
                requestRepo.observeMine(currentMemberId).first()
                    .filter { it.authorId == currentMemberId }
                    .maxByOrNull { it.createdAt ?: java.time.Instant.EPOCH }
            } else {
                null
            }
            Loaded(family, existing, source, previous)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return unavailable()

        // Read live: rights follow the family as it is now, not as it was at sign-in.
        val current = loaded.family.members.firstOrNull { it.id == currentMemberId } ?: return unavailable()
        val existing = loaded.existing
        if (requestId != null &&
            (existing == null || existing.status == HelpStatus.Cancelled || !canManageHelp(existing, current, loaded.family))
        ) {
            return unavailable()
        }
        family = loaded.family
        me = current
        original = existing
        editedId = existing?.id ?: requestRepo.newRequestId()

        val targets = existing?.let { e -> loaded.family.members.filter { it.id == e.forMemberId } }
            ?: helpTargets(current, loaded.family)
        val adults = notifiableAdults(existing?.authorId?.let { id -> loaded.family.members.firstOrNull { it.id == id } } ?: current, loaded.family)
        val adultIds = adults.map { it.id }.toSet()
        val form = when {
            existing != null -> HelpRequestForm.from(existing)
            loaded.source != null && loaded.source.forMemberId in targets.map { it.id } ->
                HelpRequestForm.from(loaded.source, keepDate = false)
            else -> HelpRequestForm(
                forMemberId = current.id,
                notifyIds = loaded.previous?.notifyIds?.toSet().orEmpty(),
            )
        }
        // Only adults who are still in the family can stay ticked.
        initialForm = form.copy(notifyIds = form.notifyIds.intersect(adultIds)).withCurrentHousehold(loaded.family)
        val authorId = existing?.authorId ?: current.id
        _uiState.value = HelpRequestEditUiState.Editing(
            form = initialForm,
            isNew = existing == null,
            targets = targets,
            adults = adults,
            shortcuts = shortcuts(adults),
            groups = notifyGroups(adults, loaded.family, current),
            households = loaded.family.households.sortedBy { it.name.lowercase() },
            lockedIds = locked(authorId, initialForm.forMemberId, existing),
            errors = emptySet(),
            storedPhoto = existing?.photo,
        )
    }

    private data class Loaded(val family: Family, val existing: HelpRequest?, val source: HelpRequest?, val previous: HelpRequest?)

    private fun shortcuts(adults: List<Member>): List<NotifyShortcut> =
        listOf(NotifyShortcut(null, adults.map { it.id }.toSet()))

    private fun locked(authorId: String, forMemberId: String, existing: HelpRequest?): Set<String> {
        val f = family ?: return emptySet()
        return requiredNotifyIds(authorId, forMemberId, f) + setOfNotNull(existing?.volunteerId)
    }

    private fun unavailable() {
        _uiState.value = HelpRequestEditUiState.Unavailable
    }

    fun update(change: (HelpRequestForm) -> HelpRequestForm) {
        val state = _uiState.value as? HelpRequestEditUiState.Editing ?: return
        val form = change(state.form)
        val authorId = original?.authorId ?: currentMemberId
        val next = state.copy(form = form, lockedIds = locked(authorId, form.forMemberId, original), saveFailed = false)
        _uiState.value = next.copy(errors = errorsFor(next), hasChanges = form != initialForm)
    }

    /** Ticks or unticks one person. */
    fun toggle(memberId: String) = update { form ->
        form.copy(notifyIds = if (memberId in form.notifyIds) form.notifyIds - memberId else form.notifyIds + memberId)
    }

    /**
     * The picker's new choice (one person, or a whole household). The people always warned stay warned
     * whatever is ticked: they are added back on saving, so they are left out of the form.
     */
    fun setNotify(memberIds: Set<String>) = update { form ->
        val state = _uiState.value as? HelpRequestEditUiState.Editing
        form.copy(notifyIds = memberIds - state?.lockedIds.orEmpty())
    }

    /** A shortcut ticks its whole group, or unticks it when it is already all ticked. */
    fun applyShortcut(shortcut: NotifyShortcut) = update { form ->
        form.copy(
            notifyIds = if (form.notifyIds.containsAll(shortcut.memberIds)) form.notifyIds - shortcut.memberIds else form.notifyIds + shortcut.memberIds,
        )
    }

    fun save() {
        val state = _uiState.value as? HelpRequestEditUiState.Editing ?: return
        val repo = requests ?: return
        val author = me ?: return
        if (state.isSaving) return
        showErrors = true
        val errors = errorsFor(state)
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(errors = errors)
            return
        }
        val existing = original
        val form = state.form
        val authorId = existing?.authorId ?: author.id
        val notifyIds = (form.notifyIds + state.lockedIds - authorId).sorted()
        val (place, placeKind, placeHouseholdId) = form.savedPlace()
        fun request(photo: String?) = HelpRequest(
            id = editedId,
            kind = form.kind,
            title = form.title.trim(),
            date = requireNotNull(form.date),
            startTime = form.startTime,
            endTime = form.endTime,
            place = place,
            placeKind = placeKind,
            placeHouseholdId = placeHouseholdId,
            note = form.note.trim().ifEmpty { null },
            // Only who is warned can be named: the others would not see the request.
            mentionIds = if (form.note.isBlank()) emptyList() else form.mentionIds.filter { it in notifyIds }.distinct(),
            authorId = authorId,
            authorName = existing?.authorName ?: author.firstName,
            forMemberId = form.forMemberId,
            notifyIds = notifyIds,
            // Widening a request someone could not take clears their « Je ne peux pas ».
            declinedBy = existing?.declinedBy?.takeIf { existing.notifyIds.sorted() == notifyIds },
            photo = photo,
        )
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            // The photo goes first: the rules only accept a photo already sent. It needs the
            // network, so a request with a new photo is not queued offline.
            val previous = existing?.photo
            val photo = if (photos == null) {
                previous.takeIf { form.photo == PhotoEdit.Unchanged }
            } else {
                try {
                    withTimeoutOrNull(photoTimeoutMillis) {
                        photos.prepare(previous, form.photo) { helpRequestPhotoBase(editedId, currentMemberId) }
                    // A new photo is never null once sent: null here means it timed out.
                    } ?: if (form.photo is PhotoEdit.Picked) throw IllegalStateException("photo not sent") else null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val failed = _uiState.value as? HelpRequestEditUiState.Editing ?: return@launch
                    _uiState.value = failed.copy(isSaving = false, saveFailed = true)
                    return@launch
                }
            }
            if (write { repo.saveRequest(request(photo), isNew = existing == null) }) {
                photos?.cleanUp(previous, photo)
                _uiState.value = HelpRequestEditUiState.Done
            } else {
                // Not saved: the photo just sent is named by nothing.
                if (photo != previous) photos?.cleanUp(photo, null)
                val failed = _uiState.value as? HelpRequestEditUiState.Editing ?: return@launch
                _uiState.value = failed.copy(isSaving = false, saveFailed = true)
            }
        }
    }

    private fun errorsFor(state: HelpRequestEditUiState.Editing): Set<HelpFormError> {
        if (!showErrors) return emptySet()
        val authorId = original?.authorId ?: currentMemberId
        val warned = (state.warnedIds - authorId).size
        return validate(state.form, warned, today(), dateUnchanged = state.form.date == original?.date)
    }

    /** True once the write is accepted, or still pending after the timeout (queued offline). */
    private suspend fun write(block: suspend () -> Unit): Boolean = try {
        withTimeoutOrNull(writeTimeoutMillis) { block() }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}

/**
 * The adults who can be warned, by household: mine first, then the others by name; in each, by first
 * name. An adult whose household is unknown comes last, in a group with a blank name that the screen
 * shows as « Autres ».
 */
internal fun notifyGroups(adults: List<Member>, family: Family, me: Member): List<PickGroup> {
    val households = family.households.sortedWith(compareBy({ it.id != me.householdId }, { it.name.lowercase() }))
    val known = households.map { it.id }.toSet()
    val byName = compareBy<Member> { it.firstName.lowercase() }
    val groups = households.mapNotNull { household ->
        adults.filter { it.householdId == household.id }.sortedWith(byName)
            .takeIf { it.isNotEmpty() }?.let { PickGroup(household.name, it) }
    }
    val rest = adults.filter { it.householdId !in known }.sortedWith(byName)
    return if (rest.isEmpty()) groups else groups + PickGroup("", rest)
}

/**
 * « Chez un foyer » shows the household's name as it is now; a household gone leaves the name that
 * was saved, as a plain text (« Autre »).
 */
private fun HelpRequestForm.withCurrentHousehold(family: Family): HelpRequestForm {
    if (placeKind != PlaceKind.Household) return this
    val household = family.households.firstOrNull { it.id == placeHouseholdId }
        ?: return copy(placeKind = PlaceKind.Other, placeHouseholdId = null)
    return atHousehold(household)
}
