package fr.familia.app.feature.trips.story

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Trip
import fr.familia.app.core.model.TripStep
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.frenchDate
import fr.familia.app.feature.trips.detail.TripDays
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/** The dot under a day that has a word. */
private val Peach = Color(0xFFF2A07B)

/**
 * « Jour par jour » on the page of the trip (récit 90): the row of the days, a dot under those with
 * a word. Shown once one day has a word. [today] is the day in black (or the last day with a word,
 * once the trip is over).
 */
@Composable
fun TripStoryDaysSlot(container: AppContainer?, trip: Trip, steps: List<TripStep>, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    val repository = container?.tripWordRepository ?: return
    val flow = remember(repository, trip.id) { repository.observeWords(trip.id).catch { emit(emptyMap()) } }
    val words by flow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val withWord = remember(trip, steps, words) { storyDays(trip, steps, words, emptyList()).map { it.date }.toSet() }
    if (withWord.isEmpty()) return
    TripStoryDaysTile(TripDays.of(trip), withWord, today, onOpenDay)
}

/** Stateless: the days of the trip, those in [withWord] with a dot and a touch. */
@Composable
fun TripStoryDaysTile(days: List<LocalDate>, withWord: Set<LocalDate>, today: LocalDate, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val chosen = days.firstOrNull { it == today && it in withWord } ?: days.lastOrNull { it in withWord }
    FormTile(
        Icons.Outlined.PlayCircleOutline,
        stringResource(R.string.story_tile_title),
        modifier,
        trailing = {
            Text(
                pluralStringResource(R.plurals.story_tile_count, withWord.size, withWord.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days, key = { it.toString() }) { date ->
                DayChip(date, number = days.indexOf(date) + 1, hasWord = date in withWord, chosen = date == chosen) { onOpenDay(date) }
            }
        }
        FormHint(stringResource(R.string.story_tile_hint))
    }
}

@Composable
private fun DayChip(date: LocalDate, number: Int, hasWord: Boolean, chosen: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val spoken = stringResource(if (hasWord) R.string.story_chip_a11y else R.string.story_chip_empty_a11y, number, frenchDate(date, "d MMMM"))
    val contentColor = when {
        chosen -> Color.White
        hasWord -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        Modifier
            .width(64.dp)
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(if (chosen) Color(0xFF1E1B18) else Color.Transparent)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape)
            .then(if (hasWord) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics {
                contentDescription = spoken
                if (hasWord) role = Role.Button
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("J$number", style = MaterialTheme.typography.labelMedium, color = contentColor)
        Text(frenchDate(date, "d MMM"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = contentColor)
        Box(Modifier.padding(top = 4.dp).size(8.dp).clip(CircleShape).background(if (hasWord) Peach else Color.Transparent))
    }
}
