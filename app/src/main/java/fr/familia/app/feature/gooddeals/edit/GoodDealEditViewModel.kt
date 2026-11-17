package fr.familia.app.feature.gooddeals.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.GoodDealRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.GoodDeal
import fr.familia.app.core.model.GoodDealKind
import fr.familia.app.core.model.goodDealPhotoBase
import fr.familia.app.core.photo.PhotoEdit
import fr.familia.app.core.photo.SinglePhotoSaver
import fr.familia.app.feature.gooddeals.DEFAULT_DEAL_DAYS
import fr.familia.app.feature.gooddeals.PARIS
import fr.familia.app.feature.gooddeals.canManageGoodDeal
import fr.familia.app.feature.gooddeals.canPublishGoodDeal
import fr.familia.app.feature.gooddeals.dealDayAllowed
import fr.familia.app.feature.gooddeals.dealEnd
import fr.familia.app.feature.gooddeals.dealLastDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** What the member types on the form of a bon plan. */
data class GoodDealForm(
    val title: String = "",
    val text: String = "",
    val link: String = "",
    val place: String = "",
    val lastDay: LocalDate? = null,
    val photo: PhotoEdit = PhotoEdit.Unchanged,
)

enum class GoodDealFormError { TitleMissing, LinkInvalid, EndInvalid }

sealed interface GoodDealEditUiState {
    data object Loading : GoodDealEditUiState

    /** Not found, not mine to change, or a child: the screen says so and goes back. */
    data object Unavailable : GoodDealEditUiState

    data object Done : GoodDealEditUiState

    data class Editing(
        val form: GoodDealForm,
        val isNew: Boolean,
        val today: LocalDate,
        /** The photo already saved, as a Storage base. */
        val storedPhoto: String? = null,
        val errors: Set<GoodDealFormError> = emptySet(),
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : GoodDealEditUiState
}

/** A link as typed: "exemple.fr/promo" becomes "https://exemple.fr/promo". Blank stays empty. */
fun normalizeLink(link: String): String {
    val trimmed = link.trim()
    if (trimmed.isEmpty()) return ""
    return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) trimmed else "https://$trimmed"
}

private val LINK = Regex("^https?://[^ ]+$")

/**
 * Publish a bon plan ([dealId] null) or change one (PRD, récit 45). Its end is a day, by default
 * in 7 days, never more than 3 months on. Offline, the write lands in the cache at once; a new
 * photo needs the network, as on a coup de main.
 */
class GoodDealEditViewModel(
    private val deals: GoodDealRepository?,
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val dealId: String? = null,
    private val photos: SinglePhotoSaver? = null,
    private val today: () -> LocalDate = { LocalDate.now(PARIS) },
    private val writeTimeoutMillis: Long = 4_000,
    private val photoTimeoutMillis: Long = 60_000,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GoodDealEditUiState>(GoodDealEditUiState.Loading)
    val uiState: StateFlow<GoodDealEditUiState> = _uiState.asStateFlow()

    private var original: GoodDeal? = null
    private var authorName = ""
    private var editedId = ""
    private var showErrors = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val dealRepo = deals ?: return unavailable()
        val memberRepo = members ?: return unavailable()
        val (family, existing) = try {
            memberRepo.observeFamily().first() to dealId?.let { dealRepo.get(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return unavailable()
        }
        val me = family.members.firstOrNull { it.id == currentMemberId } ?: return unavailable()
        if (dealId == null && !canPublishGoodDeal(me)) return unavailable()
        if (dealId != null && (existing == null || existing.kind != GoodDealKind.Deal || !canManageGoodDeal(existing, me, family))) {
            return unavailable()
        }
        original = existing
        authorName = me.firstName
        editedId = existing?.id ?: dealRepo.newId()
        val day = today()
        val form = existing?.let {
            GoodDealForm(it.title, it.text, it.link.orEmpty(), it.place.orEmpty(), it.endsAt?.let(::dealLastDay))
        } ?: GoodDealForm(lastDay = day.plusDays(DEFAULT_DEAL_DAYS))
        _uiState.value = GoodDealEditUiState.Editing(form, isNew = existing == null, today = day, storedPhoto = existing?.photo)
    }

    private fun unavailable() {
        _uiState.value = GoodDealEditUiState.Unavailable
    }

    fun update(change: (GoodDealForm) -> GoodDealForm) {
        val state = _uiState.value as? GoodDealEditUiState.Editing ?: return
        val next = state.copy(form = change(state.form), saveFailed = false)
        _uiState.value = next.copy(errors = errorsFor(next))
    }

    private fun errorsFor(state: GoodDealEditUiState.Editing): Set<GoodDealFormError> {
        if (!showErrors) return emptySet()
        val form = state.form
        val link = normalizeLink(form.link)
        val endKept = original?.endsAt?.let(::dealLastDay) == form.lastDay
        return buildSet {
            if (form.title.isBlank()) add(GoodDealFormError.TitleMissing)
            if (link.isNotEmpty() && (link.length > 500 || !LINK.matches(link))) add(GoodDealFormError.LinkInvalid)
            // An ended bon plan can be fixed without moving its end; a new end is from today on.
            if (form.lastDay == null || (!endKept && !dealDayAllowed(form.lastDay, state.today))) add(GoodDealFormError.EndInvalid)
        }
    }

    fun save() {
        val state = _uiState.value as? GoodDealEditUiState.Editing ?: return
        val repo = deals ?: return
        if (state.isSaving) return
        showErrors = true
        val errors = errorsFor(state)
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(errors = errors)
            return
        }
        val existing = original
        val form = state.form
        val endsAt = if (existing?.endsAt?.let(::dealLastDay) == form.lastDay) existing?.endsAt else dealEnd(requireNotNull(form.lastDay))
        fun deal(photo: String?) = GoodDeal(
            id = editedId,
            kind = GoodDealKind.Deal,
            title = form.title.trim().take(80),
            text = form.text.trim().take(500),
            link = normalizeLink(form.link).ifEmpty { null },
            place = form.place.trim().take(80).ifEmpty { null },
            endsAt = endsAt,
            photo = photo,
            interestedIds = existing?.interestedIds.orEmpty(),
            authorId = existing?.authorId ?: currentMemberId,
            authorName = existing?.authorName ?: authorName,
        )
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            // The photo goes first: the rules only accept a photo already sent.
            val previous = existing?.photo
            val photo = if (photos == null) {
                previous.takeIf { form.photo == PhotoEdit.Unchanged }
            } else {
                try {
                    withTimeoutOrNull(photoTimeoutMillis) {
                        photos.prepare(previous, form.photo) { goodDealPhotoBase(editedId, currentMemberId) }
                    // A new photo is never null once sent: null here means it timed out.
                    } ?: if (form.photo is PhotoEdit.Picked) throw IllegalStateException("photo not sent") else null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@launch failed()
                }
            }
            val written = try {
                withTimeoutOrNull(writeTimeoutMillis) { repo.save(deal(photo), isNew = existing == null) }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (written) {
                photos?.cleanUp(previous, photo)
                _uiState.value = GoodDealEditUiState.Done
            } else {
                // Not saved: the photo just sent is named by nothing.
                if (photo != previous) photos?.cleanUp(photo, null)
                failed()
            }
        }
    }

    private fun failed() {
        val state = _uiState.value as? GoodDealEditUiState.Editing ?: return
        _uiState.value = state.copy(isSaving = false, saveFailed = true)
    }
}
