package fr.familia.app.feature.trips.packing

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.FamiliaTopBar
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.feature.trips.detail.ChecklistActions
import fr.familia.app.feature.trips.detail.TripChecklist
import fr.familia.app.feature.trips.detail.TripDetailUiState
import fr.familia.app.feature.trips.detail.TripDetailViewModel
import fr.familia.app.feature.trips.detail.checklistActions

/** The number on the round « Valise »: what is left in my bag, plus the lines nobody has taken. */
fun packingCount(items: List<fr.familia.app.core.model.TripItem>, bags: PackingUiState, packingOn: Boolean): Int =
    items.count { it.assigneeId == null && !it.done } + if (packingOn) bags.bags.firstOrNull { it.owner.isMe }?.left ?: 0 else 0

/** The chip of the list shared by everyone, next to those of the bags. */
const val PACKING_EVERYONE = "everyone"

/**
 * « Valise » (récit 98): « Pour tout le monde », « Ma valise » and one chip per child who goes, one
 * list at a time. Only for those who go; with packingList off, only « À prévoir ».
 */
@Composable
fun TripPackingRoute(container: AppContainer?, currentMember: Member, tripId: String, open: String?, onBack: () -> Unit) {
    val detail: TripDetailViewModel = viewModel(
        key = "trip-valise-$tripId",
        factory = viewModelFactory {
            initializer { TripDetailViewModel(container?.tripRepository, container?.memberRepository, currentMember.id, tripId) }
        },
    )
    val packing = rememberPackingViewModel(container, currentMember, tripId)
    val state by detail.uiState.collectAsStateWithLifecycle()
    val bags by packing.uiState.collectAsStateWithLifecycle()
    val packingOn = LocalFeatureFlags.current.isOn(Feature.PackingList)
    TripPackingScreen(
        state = state,
        bags = if (packingOn) bags else PackingUiState(),
        checklist = remember(detail, packingOn) { detail.checklistActions(currentMember.id, packingOn) },
        actions = packing.actions(),
        open = open,
        onBack = onBack,
    )
}

/** Stateless. */
@Composable
fun TripPackingScreen(
    state: TripDetailUiState,
    bags: PackingUiState,
    checklist: ChecklistActions,
    actions: PackingActions,
    open: String?,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { FamiliaTopBar(title = { Text(stringResource(R.string.packing_screen_title)) }, navigationIcon = { BackButton(onClick = onBack) }) }) { padding ->
        val success = state as? TripDetailUiState.Success
        if (success == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        // « À prévoir » is for those who go (récit 86).
        if (!success.isParticipant) {
            Text(stringResource(R.string.packing_followers), Modifier.padding(padding).padding(ClaySpacing.screen), style = MaterialTheme.typography.bodyLarge)
            return@Scaffold
        }
        val needsSomeone = success.items.any { it.assigneeId == null && !it.done }
        var chosen by rememberSaveable(open) { mutableStateOf(open) }
        val shown = chosen?.takeIf { it == PACKING_EVERYONE || bags.bags.any { bag -> bag.owner.memberId == it } }
            ?: if (needsSomeone || bags.bags.isEmpty()) PACKING_EVERYONE else bags.bags.first().owner.memberId
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(ClaySpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (bags.bags.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.packing_everyone), shown == PACKING_EVERYONE) { chosen = PACKING_EVERYONE }
                    bags.bags.forEach { bag ->
                        val label = if (bag.owner.isMe) stringResource(R.string.packing_my_bag) else bag.owner.firstName
                        Chip(label, shown == bag.owner.memberId) { chosen = bag.owner.memberId }
                    }
                }
            }
            if (shown == PACKING_EVERYONE) {
                Text(stringResource(R.string.packing_everyone_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TripChecklist(success.items, success.assignees, true, checklist)
            } else {
                val bag = bags.bags.first { it.owner.memberId == shown }
                if (bag.owner.isMe) {
                    Text(stringResource(R.string.packing_private_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PackingBags(PackingUiState(listOf(bag), bags.sendFailed), actions)
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, modifier = Modifier.heightIn(min = 56.dp))
}
