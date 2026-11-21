package fr.familia.app.feature.events.carpool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.ui.form.FormTileHeading
import fr.familia.app.core.ui.form.FormOutlineButton
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.Ride
import fr.familia.app.core.place.DirectionsButton
import fr.familia.app.core.place.directionsUri
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.core.ui.formatTime
import fr.familia.app.feature.trips.TripTimeButton
import java.time.LocalTime

/** « On y va ensemble » (PRD, récit 52), under « Je viens » on the page of an event. */
@Composable
fun CarpoolSection(
    container: AppContainer?,
    currentMember: Member,
    eventId: String,
    riderIds: List<String>,
    iAmComing: Boolean,
) {
    val viewModel: CarpoolViewModel = viewModel(
        key = "carpool-$eventId-${riderIds.joinToString(",")}-$iAmComing",
        factory = viewModelFactory {
            initializer {
                CarpoolViewModel(container?.carpoolRepository, container?.memberRepository, currentMember.id, eventId, riderIds, iAmComing)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CarpoolContent(
        state = state,
        meId = currentMember.id,
        onOffer = viewModel::offer,
        onWithdraw = viewModel::withdraw,
        onChoose = viewModel::choose,
        onLetOut = viewModel::letOut,
        onNeeds = viewModel::needs,
        onDismissError = viewModel::dismissError,
    )
}

/** Stateless: every change goes through the callbacks. */
@Composable
fun CarpoolContent(
    state: CarpoolUiState,
    meId: String,
    onOffer: (seats: Int, from: String, leaveAt: LocalTime?) -> Boolean,
    onWithdraw: () -> Unit,
    onChoose: (driverId: String, chosen: Set<String>) -> Unit,
    onLetOut: (memberId: String) -> Unit,
    onNeeds: (chosen: Set<String>) -> Unit,
    onDismissError: () -> Unit,
) {
    var offering by rememberSaveable { mutableStateOf(false) }
    var withdrawing by rememberSaveable { mutableStateOf(false) }
    var choosingFor by rememberSaveable { mutableStateOf<String?>(null) }
    var askingSeat by rememberSaveable { mutableStateOf(false) }
    val enabled = !state.isBusy
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FormTileHeading(Icons.Outlined.DirectionsCar, stringResource(R.string.carpool_title))
        if (!state.isLoading && state.cars.isEmpty() && state.needs.isEmpty()) {
            Text(stringResource(R.string.carpool_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.cars.forEach { car ->
            CarCard(
                car = car,
                meId = meId,
                riders = state.riders,
                enabled = enabled,
                onChoose = { choosingFor = car.ride.driverId },
                onGetInAlone = { onChoose(car.ride.driverId, setOf(meId)) },
                onGetOutAlone = { onChoose(car.ride.driverId, emptySet()) },
                onEdit = { offering = true },
                onWithdraw = { withdrawing = true },
                onLetOut = onLetOut,
            )
        }
        if (state.needs.isNotEmpty()) {
            Text(
                stringResource(R.string.carpool_needs, state.needs.joinToString(", ") { it.firstName }),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (state.canOffer) {
            FormOutlineButton(stringResource(R.string.carpool_offer), onClick = { offering = true }, enabled = enabled, icon = Icons.Outlined.DirectionsCar)
        }
        val askable = state.riders.filter { r -> state.carOf(r.id) == null && state.cars.none { it.isMine && it.driver.id == r.id } }
        if (askable.isNotEmpty()) {
            val asking = askable.any { r -> state.needs.any { it.id == r.id } }
            if (state.riders.size == 1) {
                TextButton(
                    onClick = { onNeeds(if (asking) emptySet() else setOf(meId)) },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(if (asking) R.string.carpool_need_cancel else R.string.carpool_need)) }
            } else {
                TextButton(onClick = { askingSeat = true }, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.carpool_need))
                }
            }
        }
    }

    if (offering) {
        val mine = state.cars.firstOrNull { it.isMine }?.ride
        OfferDialog(
            initial = mine,
            onConfirm = { seats, from, leaveAt -> if (onOffer(seats, from, leaveAt)) offering = false },
            onDismiss = { offering = false },
        )
    }
    if (withdrawing) {
        AlertDialog(
            onDismissRequest = { withdrawing = false },
            title = { Text(stringResource(R.string.carpool_withdraw_title)) },
            text = { Text(stringResource(R.string.carpool_withdraw_body)) },
            confirmButton = {
                TextButton(onClick = { withdrawing = false; onWithdraw() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.carpool_withdraw))
                }
            },
            dismissButton = {
                TextButton(onClick = { withdrawing = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    choosingFor?.let { driverId ->
        val car = state.cars.firstOrNull { it.ride.driverId == driverId }
        if (car == null) {
            choosingFor = null
        } else {
            val inside = car.passengers.map { it.id }.toSet()
            val riders = state.riders.filter { it.id != driverId }
            PickRidersDialog(
                title = stringResource(R.string.carpool_choose_title, car.driver.firstName),
                riders = riders,
                meId = meId,
                initial = riders.map { it.id }.filter { it in inside }.toSet(),
                // Room for those already in that I do not answer for, plus the seats left.
                max = car.ride.seats - car.passengers.count { p -> riders.none { it.id == p.id } },
                onConfirm = { chosen -> choosingFor = null; onChoose(driverId, chosen) },
                onDismiss = { choosingFor = null },
            )
        }
    }
    if (askingSeat) {
        val riders = state.riders.filter { r -> state.carOf(r.id) == null }
        PickRidersDialog(
            title = stringResource(R.string.carpool_need_title),
            riders = riders,
            meId = meId,
            initial = riders.map { it.id }.filter { id -> state.needs.any { it.id == id } }.toSet(),
            max = riders.size,
            onConfirm = { chosen -> askingSeat = false; onNeeds(chosen) },
            onDismiss = { askingSeat = false },
        )
    }
    if (state.failed) {
        AlertDialog(
            onDismissRequest = onDismissError,
            text = { Text(stringResource(R.string.carpool_failed)) },
            confirmButton = {
                TextButton(onClick = onDismissError, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@Composable
private fun CarCard(
    car: CarRow,
    meId: String,
    riders: List<Member>,
    enabled: Boolean,
    onChoose: () -> Unit,
    onGetInAlone: () -> Unit,
    onGetOutAlone: () -> Unit,
    onEdit: () -> Unit,
    onWithdraw: () -> Unit,
    onLetOut: (String) -> Unit,
) {
    val ride = car.ride
    // A car is a pale outlined box inside the tile of the section (plan de refonte du 21/11).
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (car.isMine) stringResource(R.string.carpool_my_car) else stringResource(R.string.carpool_car_of, car.driver.firstName),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                if (ride.seatsLeft == 0) stringResource(R.string.carpool_full)
                else pluralStringResource(R.plurals.carpool_seats_left, ride.seatsLeft, ride.seatsLeft, ride.seats),
                style = MaterialTheme.typography.bodyLarge,
            )
            val leave = listOfNotNull(ride.from, ride.leaveAt?.let(::formatTime)).joinToString(", ")
            if (leave.isNotEmpty()) Text(stringResource(R.string.carpool_leaves, leave), style = MaterialTheme.typography.bodyMedium)
            if (car.passengers.isEmpty()) {
                Text(stringResource(R.string.carpool_nobody_in), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (car.isMine) {
                // The driver lets anyone out, one line each with a written label.
                car.passengers.forEach { p ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.firstName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        val label = stringResource(R.string.carpool_let_out, p.firstName)
                        IconButton(onClick = { onLetOut(p.id) }, enabled = enabled, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = label)
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.carpool_with, car.passengers.joinToString(", ") { it.firstName }), style = MaterialTheme.typography.bodyMedium)
            }
            if (ride.from != null && LocalFeatureFlags.current.isOn(Feature.Places)) DirectionsButton(directionsUri(ride.from))
            if (car.isMine) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.carpool_edit)) }
                    TextButton(onClick = onWithdraw, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.carpool_withdraw)) }
                }
            } else {
                val others = riders.filter { it.id != car.driver.id }
                val meInside = car.passengers.any { it.id == meId }
                when {
                    others.size > 1 -> OutlinedButton(onClick = onChoose, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.carpool_who_gets_in))
                    }
                    others.size == 1 && meInside -> OutlinedButton(onClick = onGetOutAlone, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.carpool_get_out))
                    }
                    others.size == 1 && ride.seatsLeft > 0 -> OutlinedButton(onClick = onGetInAlone, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.carpool_get_in))
                    }
                }
            }
        }
    }
}

/** « J'ai X places »: the seats, a town and a time, both optional. */
@Composable
private fun OfferDialog(initial: Ride?, onConfirm: (Int, String, LocalTime?) -> Unit, onDismiss: () -> Unit) {
    var seats by rememberSaveable { mutableStateOf(initial?.seats ?: 3) }
    var from by rememberSaveable { mutableStateOf(initial?.from.orEmpty()) }
    var leaveAt by rememberSaveable { mutableStateOf(initial?.leaveAt?.toString()) }
    val minSeats = maxOf(Ride.MIN_SEATS, initial?.passengers?.size ?: 0)
    val tooLong = from.trim().length > Ride.MAX_FROM
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.carpool_offer else R.string.carpool_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val fewer = stringResource(R.string.carpool_seats_fewer)
                    val more = stringResource(R.string.carpool_seats_more)
                    IconButton(onClick = { seats-- }, enabled = seats > minSeats) { Icon(Icons.Outlined.Remove, contentDescription = fewer) }
                    Text(
                        pluralStringResource(R.plurals.carpool_seats, seats, seats),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { seats++ }, enabled = seats < Ride.MAX_SEATS) { Icon(Icons.Outlined.Add, contentDescription = more) }
                }
                OutlinedTextField(
                    value = from,
                    onValueChange = { from = it },
                    label = { Text(stringResource(R.string.carpool_from)) },
                    placeholder = { Text(stringResource(R.string.carpool_from_hint)) },
                    isError = tooLong,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                TripTimeButton(
                    time = leaveAt?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                    placeholder = stringResource(R.string.carpool_leave_at),
                    onPick = { leaveAt = it?.toString() },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(seats, from, leaveAt?.let { runCatching { LocalTime.parse(it) }.getOrNull() }) },
                enabled = !tooLong,
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.carpool_offer_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Who of me and my children: one line each, never more than [max] checked. */
@Composable
private fun PickRidersDialog(
    title: String,
    riders: List<Member>,
    meId: String,
    initial: Set<String>,
    max: Int,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by rememberSaveable { mutableStateOf(initial.toList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                riders.forEach { r ->
                    val checked = r.id in chosen
                    val canCheck = checked || chosen.size < max
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .toggleable(value = checked, enabled = canCheck, role = Role.Checkbox) { on ->
                                chosen = if (on) chosen + r.id else chosen - r.id
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = canCheck)
                        Spacer(Modifier.width(16.dp))
                        Text(if (r.id == meId) stringResource(R.string.rsvp_me) else r.firstName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen.toSet()) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
