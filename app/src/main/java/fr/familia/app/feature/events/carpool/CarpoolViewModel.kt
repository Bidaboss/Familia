package fr.familia.app.feature.events.carpool

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.CarpoolRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.Ride
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime

/** One car as this member sees it. */
data class CarRow(
    val ride: Ride,
    val driver: Member,
    /** In the car, in its order; a member who left the family is not shown. */
    val passengers: List<Member>,
    /** My own car: « Modifier » and « Retirer ma proposition ». */
    val isMine: Boolean,
)

data class CarpoolUiState(
    val cars: List<CarRow> = emptyList(),
    /** « J'ai besoin d'une place », without a driver yet. */
    val needs: List<Member> = emptyList(),
    /** Who I get in or out: me, then the children I answer for. */
    val riders: List<Member> = emptyList(),
    /** « J'ai des places »: an adult who said « Je viens » and has no car yet. */
    val canOffer: Boolean = false,
    val isLoading: Boolean = true,
    val failed: Boolean = false,
    val isBusy: Boolean = false,
) {
    /** The car [memberId] is in, if any. */
    fun carOf(memberId: String): CarRow? = cars.firstOrNull { car -> car.passengers.any { it.id == memberId } }
}

/**
 * « On y va ensemble » of an event (PRD, récit 52). [riderIds] are the ones this member answers
 * for (themselves first, then their children: the answerers of the event); [iAmComing] is their own
 * « Je viens ». The rules check each write again; this only keeps the buttons that can work.
 */
class CarpoolViewModel(
    private val carpool: CarpoolRepository?,
    members: MemberRepository?,
    private val currentMemberId: String,
    private val eventId: String,
    riderIds: List<String>,
    iAmComing: Boolean,
    private val writeTimeoutMillis: Long = 4_000,
) : ViewModel() {

    private data class Action(val busy: Boolean = false, val failed: Boolean = false)

    private val action = MutableStateFlow(Action())

    val uiState: StateFlow<CarpoolUiState> = run {
        val repo = carpool
        val memberRepo = members
        if (repo == null || memberRepo == null) {
            flowOf(CarpoolUiState(isLoading = false))
        } else {
            combine(repo.observeRides(eventId), repo.observeNeeds(eventId), memberRepo.observeFamily(), action) { rides, needs, family, act ->
                val byId = family.members.associateBy { it.id }
                val me = byId[currentMemberId]
                val cars = rides.mapNotNull { ride ->
                    val driver = byId[ride.driverId] ?: return@mapNotNull null
                    CarRow(ride, driver, ride.passengers.mapNotNull(byId::get), isMine = ride.driverId == currentMemberId)
                }.sortedWith(compareBy({ !it.isMine }, { it.driver.firstName }))
                val inACar = cars.flatMap { car -> car.passengers.map { it.id } }.toSet()
                CarpoolUiState(
                    cars = cars,
                    needs = needs.mapNotNull { byId[it.memberId] }.filter { it.id !in inACar },
                    riders = riderIds.mapNotNull(byId::get),
                    canOffer = iAmComing && me?.profile == MemberProfile.Adult && cars.none { it.isMine },
                    isLoading = false,
                    failed = act.failed,
                    isBusy = act.busy,
                )
            }.catch { emit(CarpoolUiState(isLoading = false, failed = true)) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CarpoolUiState())

    /** « J'ai X places », or a change of my car. False when the seats or the town do not fit. */
    fun offer(seats: Int, from: String, leaveAt: LocalTime?): Boolean {
        val repo = carpool ?: return false
        val town = from.trim().takeIf { it.isNotEmpty() }
        if (seats !in Ride.MIN_SEATS..Ride.MAX_SEATS || (town?.length ?: 0) > Ride.MAX_FROM) return false
        val mine = uiState.value.cars.firstOrNull { it.isMine }
        if (mine != null && seats < mine.ride.passengers.size) return false
        write { repo.offer(eventId, currentMemberId, seats, town, leaveAt, isNew = mine == null) }
        return true
    }

    fun withdraw() {
        val repo = carpool ?: return
        if (uiState.value.cars.none { it.isMine }) return
        write { repo.withdraw(eventId, currentMemberId) }
    }

    /**
     * Who of [chosen] rides in the car of [driverId]: each one gets in or out, one write at a time.
     * Never beyond the seats, never in my own car through this.
     */
    fun choose(driverId: String, chosen: Set<String>) {
        val repo = carpool ?: return
        val state = uiState.value
        val car = state.cars.firstOrNull { it.ride.driverId == driverId } ?: return
        val riders = state.riders.map { it.id }.filter { it != driverId }
        val inside = car.passengers.map { it.id }.toSet()
        val out = riders.filter { it in inside && it !in chosen }
        val inn = riders.filter { it !in inside && it in chosen }
        if (car.ride.passengers.size - out.size + inn.size > car.ride.seats) return
        if (out.isEmpty() && inn.isEmpty()) return
        write {
            out.forEach { repo.getOut(eventId, driverId, it) }
            inn.forEach { id -> repo.getIn(eventId, driverId, id, leaving = state.carOf(id)?.ride?.driverId) }
        }
    }

    /** The driver lets [memberId] out. */
    fun letOut(memberId: String) {
        val repo = carpool ?: return
        if (uiState.value.cars.none { it.isMine }) return
        write { repo.getOut(eventId, currentMemberId, memberId) }
    }

    /** « J'ai besoin d'une place » for those of [chosen] among my riders, and no longer for the others. */
    fun needs(chosen: Set<String>) {
        val repo = carpool ?: return
        val state = uiState.value
        val asking = state.needs.map { it.id }.toSet()
        val riders = state.riders.map { it.id }
        val add = riders.filter { it in chosen && it !in asking && state.carOf(it) == null }
        val remove = riders.filter { it !in chosen && it in asking }
        if (add.isEmpty() && remove.isEmpty()) return
        write {
            add.forEach { repo.askSeat(eventId, it, currentMemberId) }
            remove.forEach { repo.cancelAsk(eventId, it) }
        }
    }

    fun dismissError() {
        action.update { it.copy(failed = false) }
    }

    private fun write(block: suspend () -> Unit) {
        if (action.value.busy) return
        viewModelScope.launch {
            action.value = Action(busy = true)
            action.value = try {
                withTimeoutOrNull(writeTimeoutMillis) { block() }
                Action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Action(failed = true)
            }
        }
    }
}
