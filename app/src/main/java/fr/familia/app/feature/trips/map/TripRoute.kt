package fr.familia.app.feature.trips.map

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.TripWord
import fr.familia.app.core.ui.form.FormHint
import fr.familia.app.core.ui.form.FormTile
import fr.familia.app.core.ui.frenchDate
import java.time.LocalDate

/** One line of « Le trajet »: a step in its order, with how many words and photos it holds (récit 94). */
data class RouteEntry(
    val stepId: String,
    val number: Int,
    val total: Int,
    val place: String,
    val date: LocalDate,
    val words: Int,
    val photos: Int,
)

/**
 * The steps of the trip as a plain list, in order, for who does not see or does not want an
 * animated map: TalkBack, « supprimer les animations ». Every step is there, placed on the map or
 * not. [words] are the words of the steps by step id (one word per step).
 */
fun routeEntries(stops: List<MapStop>, words: Map<String, TripWord>): List<RouteEntry> = stops.map { stop ->
    val word = words[stop.step.id]
    RouteEntry(
        stepId = stop.step.id,
        number = stop.number,
        total = stop.total,
        place = stop.step.place,
        date = stop.step.departDate,
        words = if (word != null) 1 else 0,
        photos = word?.photoIds?.size ?: 0,
    )
}

/**
 * Whether the animated map stays still: the phone removes animations (the scale is 0) or TalkBack,
 * or any touch-exploration service, is running. Then the trip opens on the list.
 */
fun prefersStillMap(animatorScale: Float, touchExploration: Boolean): Boolean = animatorScale == 0f || touchExploration

@Composable
fun rememberPrefersStillMap(): Boolean {
    val context = LocalContext.current
    return remember(context) { prefersStillMap(animatorScale(context), touchExploration(context)) }
}

private fun animatorScale(context: Context): Float =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

private fun touchExploration(context: Context): Boolean =
    (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true

/** « Le trajet »: one line per step, each a 56 dp button that opens the step. TalkBack reads the whole line. */
@Composable
fun TripRouteTile(entries: List<RouteEntry>, onOpenStep: (String) -> Unit, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    FormTile(Icons.Outlined.Place, stringResource(R.string.trip_route_title), modifier) {
        entries.forEach { entry -> RouteRow(entry, onOpen = { onOpenStep(entry.stepId) }) }
        FormHint(stringResource(R.string.trip_route_hint))
    }
}

@Composable
private fun RouteRow(entry: RouteEntry, onOpen: () -> Unit) {
    val day = frenchDate(entry.date, "d MMMM")
    val parts = buildList {
        add(day)
        if (entry.words > 0) add(pluralStringResource(R.plurals.trip_route_words, entry.words, entry.words))
        if (entry.photos > 0) add(pluralStringResource(R.plurals.trip_route_photos, entry.photos, entry.photos))
    }
    val spoken = stringResource(R.string.trip_route_row, entry.number, entry.total, entry.place, parts.joinToString(", "))
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = spoken; role = Role.Button }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("${entry.number}", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f)) {
            Text(entry.place, style = MaterialTheme.typography.bodyLarge)
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
