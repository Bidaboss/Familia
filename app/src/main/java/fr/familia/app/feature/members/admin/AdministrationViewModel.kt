package fr.familia.app.feature.members.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.familia.app.core.data.AppConfigRepository
import fr.familia.app.core.data.ConsumptionRepository
import fr.familia.app.core.data.MemberRepository
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Household
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.isOwner
import fr.familia.app.feature.members.MembersViewModel.Companion.MAX_HOUSEHOLD_NAME
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Administration menu of the admin principal (PRD, récit 1 ter): name or remove the chefs de
 * famille, create or delete a household. The security rules refuse these writes to anyone else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdministrationViewModel(
    private val members: MemberRepository?,
    private val currentMemberId: String,
    private val appConfig: AppConfigRepository? = null,
    private val consumptionRepository: ConsumptionRepository? = null,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    /** The last family read, so the actions work on what the screen shows. */
    private var family = Family(emptyList(), emptyList())

    val uiState: StateFlow<AdministrationUiState> = attempt
        .flatMapLatest { n ->
            (members?.observeFamily() ?: emptyFlow())
                .map { family ->
                    this.family = family
                    state(family, currentMemberId)
                }
                .onStart { if (n > 0) emit(AdministrationUiState.Loading) }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(AdministrationUiState.Error)
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdministrationUiState.Loading)

    private val _actionFailed = MutableStateFlow(false)

    /** True when the server refused a change: the screen says so once. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()

    /** The module switches of config/app, live (PRD rév. 120: hidden until ready). */
    val features: StateFlow<Map<String, Boolean>> = (appConfig?.observeAppConfig()?.map { it.features } ?: emptyFlow())
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * The « Consommation » card (PRD, récit 1 sexies): [ConsumptionCard.Waiting] before the first
     * morning, null when it cannot show (no repository, or the read was refused).
     */
    val consumption: StateFlow<ConsumptionCard?> = (
        consumptionRepository?.observeLatest()
            ?.map { latest -> latest?.let { ConsumptionCard.Ready(it) } ?: ConsumptionCard.Waiting }
            ?: emptyFlow()
        )
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Switches [feature] on or off for the family; the admin principal always sees it. */
    fun setFeature(feature: Feature, on: Boolean) {
        val repo = appConfig ?: return
        viewModelScope.launch {
            try {
                repo.setFeature(feature.key, on)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionFailed.value = true
            }
        }
    }

    /** Renames [household] (the admin principal; the rules check it). */
    fun renameHousehold(household: Household, name: String) {
        val repo = members ?: return
        val trimmed = name.trim().take(MAX_HOUSEHOLD_NAME)
        if (trimmed.isEmpty() || trimmed == household.name) return
        viewModelScope.launch {
            try {
                repo.saveHousehold(household.copy(name = trimmed))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionFailed.value = true
            }
        }
    }

    fun retry() {
        attempt.update { it + 1 }
    }

    /** Makes [member] chef de famille of their household, or removes the role. */
    fun setChef(member: Member, chef: Boolean) {
        if (member.profile != MemberProfile.Adult || member.isAdmin == chef) return
        write { saveMember(member.copy(isAdmin = chef), member.email) }
    }

    fun addHousehold(name: String) {
        val trimmed = name.trim().take(MAX_HOUSEHOLD_NAME)
        if (trimmed.isEmpty()) return
        val repo = members ?: return
        write { saveHousehold(Household(repo.newHouseholdId(), trimmed)) }
    }

    /** Deletes [household] once nobody belongs to it (the screen only offers it then). */
    fun deleteHousehold(household: Household) {
        if (family.members.any { it.householdId == household.id }) return
        write { deleteHousehold(householdId = household.id) }
    }

    fun failureShown() {
        _actionFailed.value = false
    }

    /** Offline, Firestore applies the change to the cache at once and sends it later. */
    private fun write(block: suspend MemberRepository.() -> Unit) {
        val repo = members ?: return
        viewModelScope.launch {
            try {
                repo.block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionFailed.value = true
            }
        }
    }

    companion object {
        fun state(family: Family, currentMemberId: String): AdministrationUiState {
            val me = family.members.firstOrNull { it.id == currentMemberId }
            if (me == null || !family.isOwner(me)) return AdministrationUiState.NotAllowed
            val names = family.households.associate { it.id to it.name }
            return AdministrationUiState.Ready(
                adults = family.members
                    .filter { it.profile == MemberProfile.Adult }
                    .sortedBy { it.firstName.lowercase() }
                    .map { AdultRow(it, names[it.householdId], family.isOwner(it)) },
                households = family.households
                    .sortedBy { it.name.lowercase() }
                    .map { household -> HouseholdRow(household, family.members.count { it.householdId == household.id }) },
            )
        }
    }
}
