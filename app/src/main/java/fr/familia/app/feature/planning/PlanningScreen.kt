package fr.familia.app.feature.planning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.events.edit.TargetChoice
import fr.familia.app.feature.events.edit.targetLabel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Planning tab: upcoming events day by day, and the button to add one. Stateless. */
@Composable
fun PlanningScreen(
    state: PlanningUiState,
    onRetry: () -> Unit,
    onAddEvent: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        when (state) {
            PlanningUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            PlanningUiState.Error -> EmptyState(
                icon = Icons.Outlined.Event,
                title = stringResource(R.string.planning_error_title),
                body = stringResource(R.string.planning_error_body),
                action = {
                    Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.members_retry))
                    }
                },
            )
            is PlanningUiState.Success -> if (state.days.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Event,
                    title = stringResource(R.string.planning_empty_title),
                    body = stringResource(R.string.planning_empty_body),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Room at the bottom so the add button never hides the last event.
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                ) {
                    state.days.forEach { day ->
                        item(key = "day-${day.date}") { DayHeader(day.date) }
                        items(day.items, key = { "event-${it.event.id}" }) { item ->
                            EventRow(item, onClick = if (item.canManage) ({ onOpenEvent(item.event.id) }) else null)
                        }
                    }
                }
            }
        }
        if (state !is PlanningUiState.Loading) {
            ExtendedFloatingActionButton(
                onClick = onAddEvent,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.planning_add)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    Text(
        text = date.format(dayFormatter).replaceFirstChar { it.titlecase(Locale.FRANCE) },
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun EventRow(item: PlanningItem, onClick: (() -> Unit)?) {
    val event = item.event
    val editLabel = stringResource(R.string.members_edit_action)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClickLabel = editLabel, onClick = onClick) else Modifier)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = event.time?.format(timeFormatter) ?: stringResource(R.string.planning_all_day),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 72.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                event.place?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    stringResource(R.string.planning_for_by, targetLabel(item.target), item.authorName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(Modifier.padding(start = 100.dp))
    }
}

private val dayFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRANCE)

/** Fictional planning for previews and screenshots. */
internal fun previewPlanning(): PlanningUiState.Success {
    fun item(id: String, title: String, date: LocalDate, time: LocalTime?, place: String?, target: TargetChoice, author: String, manage: Boolean) =
        PlanningItem(Event(id, title, date, time, place, null, target.target, "a", author), target, author, manage)
    val day1 = LocalDate.of(2026, 12, 20)
    val day2 = LocalDate.of(2026, 12, 24)
    return PlanningUiState.Success(
        listOf(
            PlanningDay(
                day1,
                listOf(
                    item("1", "Piscine", day1, LocalTime.of(10, 0), "Centre aquatique", TargetChoice(EventTarget.ForChild("c"), "Noé"), "Camille", true),
                    item("2", "Marché de Noël", day1, null, null, TargetChoice(EventTarget.ForHousehold("h2"), "Foyer Bernard"), "Inès", false),
                ),
            ),
            PlanningDay(
                day2,
                listOf(item("3", "Repas de Noël", day2, LocalTime.of(19, 30), "Chez les parents", TargetChoice(EventTarget.ForFamily, null), "Camille", true)),
            ),
        ),
    )
}

@FamiliaPreviews
@Composable
private fun PlanningScreenPreview() {
    FamiliaTheme { Surface { PlanningScreen(previewPlanning(), {}, {}, {}) } }
}

@FamiliaPreviews
@Composable
private fun PlanningScreenEmptyPreview() {
    FamiliaTheme { Surface { PlanningScreen(PlanningUiState.Success(emptyList()), {}, {}, {}) } }
}
