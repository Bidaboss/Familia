package fr.familia.app.feature.trips.packing

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.feature.trips.TripPage
import fr.familia.app.feature.trips.detail.ChecklistActions
import fr.familia.app.feature.trips.detail.TripChecklist
import fr.familia.app.feature.trips.detail.TripDetailUiState
import fr.familia.app.feature.trips.detail.TripDetailViewModel
import fr.familia.app.feature.trips.detail.TripPathColors
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

/** Stateless. The light blue page of the trips (fiche F3), the trip under « Valise ». */
@Composable
fun TripPackingScreen(
    state: TripDetailUiState,
    bags: PackingUiState,
    checklist: ChecklistActions,
    actions: PackingActions,
    open: String?,
    onBack: () -> Unit,
) {
    val success = state as? TripDetailUiState.Success
    TripPage(title = stringResource(R.string.packing_screen_title), subtitle = success?.trip?.title, onBack = onBack) {
        if (success == null) {
            LoadingState()
            return@TripPage
        }
        // « À prévoir » is for those who go (récit 86).
        if (!success.isParticipant) {
            Text(stringResource(R.string.packing_followers), Modifier.padding(16.dp), fontSize = 16.sp)
            return@TripPage
        }
        val needsSomeone = success.items.any { it.assigneeId == null && !it.done }
        var chosen by rememberSaveable(open) { mutableStateOf(open) }
        val shown = chosen?.takeIf { it == PACKING_EVERYONE || bags.bags.any { bag -> bag.owner.memberId == it } }
            ?: if (needsSomeone || bags.bags.isEmpty()) PACKING_EVERYONE else bags.bags.first().owner.memberId
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (bags.bags.isNotEmpty()) {
                // The two pills, then the bags of the children who go, smaller (DESIGN, F3).
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pill(stringResource(R.string.packing_tab_everyone), shown == PACKING_EVERYONE, badge = null) { chosen = PACKING_EVERYONE }
                    val mine = bags.bags.firstOrNull { it.owner.isMe }
                    if (mine != null) {
                        Pill(stringResource(R.string.packing_my_bag), shown == mine.owner.memberId, badge = mine.left.takeIf { it > 0 }) { chosen = mine.owner.memberId }
                    }
                    bags.bags.filterNot { it.owner.isMe }.forEach { bag ->
                        SmallTab(bag.owner.firstName, shown == bag.owner.memberId) { chosen = bag.owner.memberId }
                    }
                }
            }
            if (shown == PACKING_EVERYONE) {
                Text(
                    stringResource(R.string.packing_everyone),
                    fontFamily = Fredoka,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.packing_everyone_note), fontSize = 13.sp, color = TripPathColors.Ink.copy(alpha = 0.75f))
                TripChecklist(success.items, success.assignees, true, checklist, onPage = true)
            } else {
                val bag = bags.bags.first { it.owner.memberId == shown }
                if (bag.owner.isMe) {
                    Text(stringResource(R.string.packing_private_note), fontSize = 13.sp, color = TripPathColors.Ink.copy(alpha = 0.75f))
                }
                PackingBags(PackingUiState(listOf(bag), bags.sendFailed), actions)
            }
        }
    }
}

/** The peach of the number on « Ma valise » (fiche F3). */
private val BadgePeach = Color(0xFFF9C6A5)

/** « Tout le monde » and « Ma valise »: dark when chosen, white otherwise, 48 high in a 56 touch (fiche F3). */
@Composable
private fun Pill(label: String, selected: Boolean, badge: Int?, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 56.dp).selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(48.dp)
                .background(if (selected) TripPathColors.Ink else Color.White, CircleShape)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) Color.White else TripPathColors.Ink)
            if (badge != null) {
                Box(Modifier.size(24.dp).background(BadgePeach, CircleShape), contentAlignment = Alignment.Center) {
                    Text("$badge", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TripPathColors.Ink)
                }
            }
        }
    }
}

/** The bag of a child who goes: a smaller tab after the two pills, still 56 dp to touch. */
@Composable
private fun SmallTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 56.dp).selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(36.dp)
                .background(if (selected) TripPathColors.Ink else Color.White.copy(alpha = 0.7f), CircleShape)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else TripPathColors.Ink)
        }
    }
}
