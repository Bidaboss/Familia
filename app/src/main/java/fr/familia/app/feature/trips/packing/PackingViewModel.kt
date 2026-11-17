package fr.familia.app.feature.trips.packing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.data.PackingRepository
import fr.familia.app.core.data.TripRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.PackingBag
import fr.familia.app.core.model.PackingLine
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.householdGroups
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whose bag it is: mine, or a child I am one of the adults of. */
data class BagOwner(val memberId: String, val firstName: String, val isMe: Boolean)

/** One bag as shown: its lines, or the list to start from while it was never written. */
data class BagUiState(val owner: BagOwner, val lines: List<PackingLine>) {
    val left: Int get() = PackingBag(lines).left
}

data class PackingUiState(
    /** Mine first, then my children who take part, by household (règle du 17/11). Empty: not a participant. */
    val bags: List<BagUiState> = emptyList(),
    val sendFailed: Boolean = false,
)

/**
 * The bags I see in a trip (récit 62): mine when I take part, and those of the children I am a
 * responsible adult of who take part too. A bag never opened shows the list to start from (mine
 * for my bag, the default one for a child's); the first change writes it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PackingViewModel(
    private val packing: PackingRepository?,
    trips: TripRepository?,
    members: MemberRepository?,
    private val memberId: String,
    private val tripId: String,
    /** « Papiers d'identité », « Chargeur »… from the strings. */
    private val defaultLabels: List<String>,
) : ViewModel() {

    private val sendFailed = MutableStateFlow(false)

    private fun <T> Flow<T>.orElse(value: T): Flow<T> = catch { e -> if (e is CancellationException) throw e else emit(value) }

    private val owners: Flow<List<BagOwner>> =
        if (packing == null || trips == null || members == null) {
            flowOf(emptyList())
        } else {
            combine(trips.observeTrip(tripId), members.observeFamily()) { trip, family -> owners(trip, family, memberId) }.orElse(emptyList())
        }

    private val template: Flow<List<String>?> = packing?.observeTemplate(memberId)?.orElse(null) ?: flowOf(null)

    val uiState: StateFlow<PackingUiState> = owners
        .flatMapLatest { list ->
            val repo = packing
            if (list.isEmpty() || repo == null) {
                flowOf(emptyList())
            } else {
                combine(list.map { owner -> repo.observeBag(tripId, owner.memberId).orElse(null) }) { it.toList() }
                    .let { bags -> combine(bags, template) { b, t -> list.zip(b) { owner, bag -> bagState(owner, bag, t) } } }
            }
        }
        .let { bags -> combine(bags, sendFailed) { b, failed -> PackingUiState(b, failed) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PackingUiState())

    private fun bagState(owner: BagOwner, bag: PackingBag?, template: List<String>?): BagUiState =
        BagUiState(owner, bag?.lines ?: startLines(if (owner.isMe) template else null))

    private fun startLines(template: List<String>?): List<PackingLine> =
        (template ?: defaultLabels).map { PackingLine(it) }

    fun toggle(ownerId: String, index: Int, done: Boolean) = change(ownerId) { lines ->
        lines.mapIndexed { i, line -> if (i == index) line.copy(done = done) else line }
    }

    /** « Ajouter un objet »: blank or repeated labels are ignored. */
    fun add(ownerId: String, label: String) = change(ownerId) { lines ->
        val clean = label.trim().take(PackingLine.MAX_LABEL)
        if (clean.isEmpty() || lines.size >= PackingLine.MAX_LINES || lines.any { it.label.equals(clean, ignoreCase = true) }) {
            lines
        } else {
            lines + PackingLine(clean)
        }
    }

    /** Removes a line, after the screen asked. */
    fun remove(ownerId: String, index: Int) = change(ownerId) { lines -> lines.filterIndexed { i, _ -> i != index } }

    /** « Garder comme ma liste de départ »: my next bags start from these lines, unticked. */
    fun keepAsMyList() {
        val repo = packing ?: return
        val mine = uiState.value.bags.firstOrNull { it.owner.isMe } ?: return
        write { repo.saveTemplate(memberId, mine.lines.map { it.label }) }
    }

    private fun change(ownerId: String, block: (List<PackingLine>) -> List<PackingLine>) {
        val repo = packing ?: return
        val bag = uiState.value.bags.firstOrNull { it.owner.memberId == ownerId } ?: return
        val lines = block(bag.lines)
        if (lines == bag.lines) return
        write { repo.saveBag(tripId, ownerId, lines, by = memberId) }
    }

    private fun write(block: suspend () -> Unit) {
        sendFailed.value = false
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sendFailed.value = true
            }
        }
    }

    fun dismissFailure() {
        sendFailed.value = false
    }
}

/**
 * My bag when I take part, then the bags of the children who take part and whose adult I am (an
 * adult only), in the order of the households.
 */
fun owners(trip: Trip?, family: Family, memberId: String): List<BagOwner> {
    trip ?: return emptyList()
    val me = family.members.firstOrNull { it.id == memberId } ?: return emptyList()
    val mine = listOfNotNull(BagOwner(me.id, me.firstName, isMe = true).takeIf { me.id in trip.participantIds })
    if (me.profile != MemberProfile.Adult) return mine
    val children = householdGroups(family).flatMap { it.members }
        .filter { it.id != me.id && it.profile != MemberProfile.Adult && me.id in it.parentIds && it.id in trip.participantIds }
        .map { BagOwner(it.id, it.firstName, isMe = false) }
    return mine + children
}
