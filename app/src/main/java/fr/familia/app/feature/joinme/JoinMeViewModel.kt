package fr.familia.app.feature.joinme

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.JoinMeRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.JoinMe
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.place.Place
import fr.familia.app.core.place.PlaceLocator
import fr.familia.app.core.place.isShortMapsLink
import fr.familia.app.core.place.linkIn
import fr.familia.app.core.place.parsePastedPlace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * « Rejoignez-moi » (PRD, récit 33): the position of the moment, read once when the member asks
 * for it, or a place they type or paste; a name and a note, both optional; the recipients, nobody
 * ticked at first (carte AVIS-13).
 */
class JoinMeViewModel(
    private val members: MemberRepository?,
    private val shares: JoinMeRepository?,
    private val locator: PlaceLocator?,
    private val currentMemberId: String,
    /** Keeps what is typed when Android stops the app in the background (appareil photo, WhatsApp…). */
    private val savedState: SavedStateHandle? = null,
    /** Offline, the share waits in Firestore's queue: after this, the screen closes as sent. */
    private val sendTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private val _uiState = MutableStateFlow<JoinMeUiState>(JoinMeUiState.Loading)
    val uiState: StateFlow<JoinMeUiState> = _uiState.asStateFlow()

    private var me: Member? = null
    private var lookupJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val family = try {
            members?.observeFamily()?.first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val current = family?.members?.firstOrNull { it.id == currentMemberId }
        if (family == null || current == null || shares == null || locator == null) {
            _uiState.value = JoinMeUiState.Unavailable
            return
        }
        // Stopped by Android while the share was leaving: it is in Firestore's queue, never sent twice.
        if (savedState?.get<Boolean>(KEY_SENT) == true) {
            _uiState.value = JoinMeUiState.Sent
            return
        }
        me = current
        val groups = recipientGroups(family, current)
        // Only those who can still be ticked: a member who has become a child without the app since, or
        // who has left the family, never stays a recipient of a position.
        val tickable = groups.flatMap { group -> group.members.map { it.id } }.toSet()
        val fresh = JoinMeUiState.Editing(groups = groups, shortcuts = shortcuts(groups, current))
        val restored = savedState?.let { restoreJoinMe(it, fresh) }?.let { it.copy(recipientIds = it.recipientIds.filter { id -> id in tickable }.toSet()) }
        _uiState.value = restored?.copy(errors = errorsOf(restored)) ?: fresh
    }

    private fun update(change: (JoinMeUiState.Editing) -> JoinMeUiState.Editing) {
        val state = _uiState.value as? JoinMeUiState.Editing ?: return
        val next = change(state)
        savedState?.let { saveJoinMe(it, next) }
        _uiState.value = next.copy(errors = errorsOf(next), sendFailed = if (next.sending) false else next.sendFailed)
    }

    fun chooseSource(source: PlaceSource) {
        lookupJob?.cancel()
        update { it.copy(source = source, lookup = PlaceLookup.Idle, sendFailed = false) }
    }

    /** The member refused the permission: the screen says how to choose a place instead. */
    fun permissionDenied() = update { it.copy(lookup = PlaceLookup.NoPermission) }

    /** Reads the position once (the permission is granted), then its address. */
    fun locate() {
        val locator = locator ?: return
        lookupJob?.cancel()
        update { it.copy(source = PlaceSource.Here, lookup = PlaceLookup.Searching) }
        lookupJob = viewModelScope.launch {
            val point = locator.currentPosition()
            val result = if (point == null) {
                PlaceLookup.NoPosition
            } else {
                PlaceLookup.Found(Place(point.first, point.second, address = locator.addressOf(point.first, point.second)))
            }
            update { if (it.source == PlaceSource.Here) it.copy(lookup = result) else it }
        }
    }

    fun changeQuery(query: String) = update { it.copy(query = query, lookup = if (it.source == PlaceSource.Elsewhere) PlaceLookup.Idle else it.lookup) }

    /**
     * An address, a Google Maps link (a short one is opened once to read it) or coordinates. The name
     * a link gives fills the name, unless the member already wrote one.
     */
    fun search() {
        val locator = locator ?: return
        val state = _uiState.value as? JoinMeUiState.Editing ?: return
        val text = state.query.trim()
        if (text.isEmpty()) return
        lookupJob?.cancel()
        update { it.copy(source = PlaceSource.Elsewhere, lookup = PlaceLookup.Searching) }
        lookupJob = viewModelScope.launch {
            val found = findPlace(locator, text)
            update { current ->
                if (current.source != PlaceSource.Elsewhere) return@update current
                if (found == null) {
                    current.copy(lookup = PlaceLookup.NotFound)
                } else {
                    val name = current.name.ifBlank { found.name?.take(Place.MAX_NAME).orEmpty() }
                    current.copy(lookup = PlaceLookup.Found(found.copy(name = null)), name = name)
                }
            }
        }
    }

    fun changeName(name: String) = update { it.copy(name = name) }

    fun changeNote(note: String) = update { it.copy(note = note) }

    fun toggle(memberId: String) = update {
        it.copy(recipientIds = if (memberId in it.recipientIds) it.recipientIds - memberId else it.recipientIds + memberId)
    }

    /** The picker's new selection (a member, or a whole household). */
    fun setRecipients(memberIds: Set<String>) = update { it.copy(recipientIds = memberIds) }

    /** A shortcut ticks its group; tapped again while it is exactly what is ticked, it unticks it. */
    fun applyShortcut(shortcut: RecipientShortcut) = update {
        it.copy(recipientIds = if (it.recipientIds == shortcut.memberIds) emptySet() else shortcut.memberIds)
    }

    fun send() {
        val state = _uiState.value as? JoinMeUiState.Editing ?: return
        val author = me ?: return
        val repo = shares ?: return
        val place = state.place ?: return
        if (!state.canSend) return
        update { it.copy(sending = true) }
        savedState?.set(KEY_SENT, true)
        viewModelScope.launch {
            try {
                // Still pending after the timeout: queued offline, sent when the network is back.
                withTimeoutOrNull(sendTimeoutMillis) {
                    repo.send(
                        authorId = author.id,
                        authorName = author.firstName,
                        recipientIds = state.recipientIds.sorted(),
                        place = place.copy(name = state.name.trim().ifEmpty { null }),
                        note = state.note.trim().ifEmpty { null },
                    )
                }
                _uiState.value = JoinMeUiState.Sent
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                savedState?.set(KEY_SENT, false)
                update { it.copy(sending = false, sendFailed = true) }
            }
        }
    }
}

/** What [text] points to: coordinates or a link read on the phone, else the address looked up. */
internal suspend fun findPlace(locator: PlaceLocator, text: String): Place? {
    val expanded = if (isShortMapsLink(text)) linkIn(text)?.let { locator.expand(it) } ?: return null else text
    val pasted = parsePastedPlace(expanded)
    return when {
        pasted == null -> locator.find(text)
        pasted.hasPosition -> {
            val lat = pasted.lat!!
            val lng = pasted.lng!!
            Place(lat, lng, name = pasted.name, address = locator.addressOf(lat, lng))
        }
        pasted.query != null -> locator.find(pasted.query)?.copy(name = pasted.name)
        else -> null
    }
}

private fun errorsOf(state: JoinMeUiState.Editing): Set<JoinMeError> = buildSet {
    if (state.name.trim().length > Place.MAX_NAME) add(JoinMeError.NameTooLong)
    if (state.note.trim().length > JoinMe.MAX_NOTE) add(JoinMeError.NoteTooLong)
}

/** Who can receive it: the members with the app, me aside. My household first, adults first. */
internal fun recipientGroups(family: Family, me: Member): List<RecipientGroup> {
    val households = family.households.sortedWith(compareBy({ it.id != me.householdId }, { it.name.lowercase() }))
    return households.mapNotNull { household ->
        family.members
            .filter { it.householdId == household.id && it.id != me.id && it.profile != MemberProfile.ChildWithoutApp }
            .sortedWith(compareBy({ it.profile != MemberProfile.Adult }, { it.firstName.lowercase() }))
            .takeIf { it.isNotEmpty() }
            ?.let { RecipientGroup(household.name, it) }
    }
}

internal fun shortcuts(groups: List<RecipientGroup>, me: Member): List<RecipientShortcut> {
    val mine = groups.flatMap { it.members }.filter { it.householdId == me.householdId }.map { it.id }.toSet()
    val everyone = groups.flatMap { g -> g.members.map { it.id } }.toSet()
    return listOfNotNull(
        RecipientShortcut(RecipientShortcut.Kind.MyHousehold, mine).takeIf { mine.isNotEmpty() },
        RecipientShortcut(RecipientShortcut.Kind.Family, everyone).takeIf { everyone.isNotEmpty() && everyone != mine },
    )
}

private const val KEY_SENT = "joinMeDraft.sent"
private const val KEY_DRAFT = "joinMeDraft."

/** What is typed, and the place found, as plain values the SavedStateHandle can keep. */
internal fun saveJoinMe(handle: SavedStateHandle, state: JoinMeUiState.Editing) {
    handle[KEY_DRAFT + "source"] = state.source.name
    handle[KEY_DRAFT + "query"] = state.query
    handle[KEY_DRAFT + "name"] = state.name
    handle[KEY_DRAFT + "note"] = state.note
    handle[KEY_DRAFT + "recipients"] = ArrayList(state.recipientIds)
    val place = state.place
    handle[KEY_DRAFT + "lat"] = place?.lat
    handle[KEY_DRAFT + "lng"] = place?.lng
    handle[KEY_DRAFT + "address"] = place?.address
}

/** The form kept before Android stopped the app, on top of [fresh]; null when nothing was kept. */
internal fun restoreJoinMe(handle: SavedStateHandle, fresh: JoinMeUiState.Editing): JoinMeUiState.Editing? {
    val source = handle.get<String>(KEY_DRAFT + "source")?.let { name -> PlaceSource.entries.firstOrNull { it.name == name } } ?: return null
    val lat = handle.get<Double>(KEY_DRAFT + "lat")
    val lng = handle.get<Double>(KEY_DRAFT + "lng")
    val place = if (lat != null && lng != null) Place(lat, lng, address = handle.get<String>(KEY_DRAFT + "address")) else null
    return fresh.copy(
        source = source,
        lookup = place?.let { PlaceLookup.Found(it) } ?: PlaceLookup.Idle,
        query = handle.get<String>(KEY_DRAFT + "query").orEmpty(),
        name = handle.get<String>(KEY_DRAFT + "name").orEmpty(),
        note = handle.get<String>(KEY_DRAFT + "note").orEmpty(),
        recipientIds = handle.get<ArrayList<String>>(KEY_DRAFT + "recipients").orEmpty().toSet(),
    )
}
