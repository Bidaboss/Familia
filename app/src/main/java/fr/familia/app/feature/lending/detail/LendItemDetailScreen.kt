package fr.familia.app.feature.lending.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.LendCategory
import fr.familia.app.core.model.LendItem
import fr.familia.app.core.model.LendStatus
import fr.familia.app.core.photo.SinglePhoto
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.core.ui.formatDay
import fr.familia.app.feature.lending.lendCategoryLabel
import fr.familia.app.feature.lending.list.lendWhereLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

/** One thing to lend (PRD, récit 51). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LendItemDetailScreen(
    state: LendItemDetailUiState,
    today: LocalDate,
    onAsk: (LocalDate?, LocalDate?) -> Unit,
    onAccept: () -> Unit,
    /** « Pas cette fois », « Retirer ma demande » and « Rendu ». */
    onRelease: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.lend_title)) }, navigationIcon = { BackButton(onClick = onBack) })
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                LendItemDetailUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                LendItemDetailUiState.Missing -> EmptyState(
                    icon = Icons.Outlined.Category,
                    title = stringResource(R.string.lend_missing_title),
                    body = stringResource(R.string.lend_missing_body),
                )
                is LendItemDetailUiState.Ready -> Content(state, today, onAsk, onAccept, onRelease, onEdit, onRemove)
            }
        }
    }
}

@Composable
private fun Content(
    state: LendItemDetailUiState.Ready,
    today: LocalDate,
    onAsk: (LocalDate?, LocalDate?) -> Unit,
    onAccept: () -> Unit,
    onRelease: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val item = state.item
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    var asking by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ClaySpacing.screen, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.between),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(lendCategoryLabel(item.category), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(item.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                if (state.isMine) stringResource(R.string.lend_owner_me) else stringResource(R.string.lend_owner, item.ownerName),
                style = MaterialTheme.typography.bodyLarge,
                color = muted,
            )
        }
        item.photo?.let { base ->
            StorageImage(
                path = SinglePhoto.photoPath(base),
                contentDescription = stringResource(R.string.lend_photo_label),
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)),
            )
        }
        if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodyLarge)
        LoanCard(state, today, onAskClick = { asking = true }, onAccept = onAccept, onRelease = onRelease)
        if (state.actionFailed) {
            Text(stringResource(R.string.lend_action_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        if (state.canManage) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.lend_edit))
            }
            TextButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.lend_remove), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (asking) AskDialog(today, onDismiss = { asking = false }, onAsk = { from, until -> asking = false; onAsk(from, until) })
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.lend_remove_title)) },
            text = { Text(stringResource(R.string.lend_remove_body)) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemove() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.lend_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** « du samedi 12 décembre au mardi 15 décembre », or half of it, or nothing. */
@Composable
private fun datesLabel(item: LendItem, today: LocalDate): String? {
    fun day(at: Instant) = formatDay(at.atZone(PARIS).toLocalDate(), today).lowercase(Locale.FRANCE)
    val from = item.from
    val until = item.until
    return when {
        from != null && until != null -> stringResource(R.string.lend_dates_both, day(from), day(until))
        from != null -> stringResource(R.string.lend_dates_from, day(from))
        until != null -> stringResource(R.string.lend_dates_until, day(until))
        else -> null
    }
}

/** Where the thing is, and the one action each can take: ask, answer, drop the ask, « Rendu ». */
@Composable
private fun LoanCard(state: LendItemDetailUiState.Ready, today: LocalDate, onAskClick: () -> Unit, onAccept: () -> Unit, onRelease: () -> Unit) {
    val item = state.item
    val dates = datesLabel(item, today)
    ClayCard(modifier = Modifier.fillMaxWidth(), emphasis = state.canAsk || state.canAnswer) {
        val line = when {
            state.canAnswer -> stringResource(R.string.lend_asked_of_me, item.borrowerName.orEmpty())
            state.canDropAsk -> stringResource(R.string.lend_asked_by_me, item.ownerName)
            else -> lendWhereLabel(item, state.iAmBorrower)
        }
        Text(line, style = MaterialTheme.typography.bodyLarge)
        if (item.status != LendStatus.Available && dates != null) {
            Text(dates, style = MaterialTheme.typography.bodyMedium, color = FamiliaClay.colors.onTintVariant(ClayTint.Plain))
        }
        when {
            state.canAsk -> Button(onClick = onAskClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.lend_ask))
            }
            state.canAnswer -> {
                Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.lend_accept))
                }
                OutlinedButton(onClick = onRelease, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.lend_decline))
                }
            }
            state.canDropAsk -> OutlinedButton(onClick = onRelease, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.lend_drop_ask))
            }
            state.canReturn -> Button(onClick = onRelease, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.lend_returned))
            }
        }
    }
}

/** « Je voudrais l'emprunter » with two optional days, within the year the rules accept. */
@Composable
private fun AskDialog(today: LocalDate, onDismiss: () -> Unit, onAsk: (LocalDate?, LocalDate?) -> Unit) {
    var from by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var until by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lend_ask_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.lend_ask_body), style = MaterialTheme.typography.bodyMedium)
                DayButton(from, today, first = today, R.string.lend_pick_from, R.string.lend_from_on) { from = it }
                DayButton(until, today, first = from ?: today, R.string.lend_pick_until, R.string.lend_until_on) { until = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAsk(from, until?.takeIf { u -> from == null || !u.isBefore(from) }) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.lend_ask_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayButton(day: LocalDate?, today: LocalDate, first: LocalDate, pickLabel: Int, dayLabel: Int, onPick: (LocalDate) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(day?.let { stringResource(dayLabel, formatDay(it, today).lowercase(Locale.FRANCE)) } ?: stringResource(pickLabel))
    }
    if (open) {
        val firstMillis = first.toUtcMillis()
        val lastMillis = today.plusDays(LEND_MAX_DAYS).toUtcMillis()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (day ?: first).toUtcMillis(),
            yearRange = today.year..today.plusDays(LEND_MAX_DAYS).year,
            selectableDates = remember(firstMillis, lastMillis) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in firstMillis..lastMillis
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = pickerState) }
    }
}

/** The rules accept dates up to 366 days on; the calendar stops a little before. */
private const val LEND_MAX_DAYS = 360L

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@FamiliaPreviews
@Composable
private fun LendItemDetailScreenPreview() {
    val item = LendItem(
        "i1", "Appareil à raclette", note = "À rendre propre.", category = LendCategory.Kitchen, ownerId = "camille", ownerName = "Camille",
    )
    FamiliaTheme {
        Surface {
            LendItemDetailScreen(
                LendItemDetailUiState.Ready(item, isMine = false, iAmBorrower = false, canManage = false, canAsk = true, canAnswer = false, canReturn = false, canDropAsk = false),
                LocalDate.of(2026, 12, 1), { _, _ -> }, {}, {}, {}, {}, {},
            )
        }
    }
}
