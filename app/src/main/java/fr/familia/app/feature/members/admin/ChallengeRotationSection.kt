package fr.familia.app.feature.members.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.ChallengeRotation
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import fr.familia.app.core.ui.ClaySpacing

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)

/**
 * « Ordre des foyers » in Administration (récit 35 bis): the foyers launch the challenge in turn, a
 * month each. The admin principal moves them up or down, then saves.
 */
@Composable
fun ChallengeRotationSection(schedule: ChallengeRotation.Schedule, onSave: (YearMonth, List<String>) -> Unit) {
    val shownIds = schedule.households.map { it.id }
    var order by rememberSaveable(shownIds) { mutableStateOf(shownIds) }
    val names = schedule.households.associate { it.id to it.name }
    fun move(from: Int, to: Int) {
        if (to !in order.indices) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }
    Column(Modifier.padding(horizontal = ClaySpacing.screen, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        order.forEachIndexed { i, id ->
            val month = schedule.start.plusMonths(i.toLong())
                .format(monthFormat).replaceFirstChar { it.titlecase(Locale.FRENCH) }
            val name = names[id].orEmpty()
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    Text(month, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { move(i, i - 1) }, enabled = i > 0, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.administration_rotation_up, name))
                }
                IconButton(onClick = { move(i, i + 1) }, enabled = i < order.lastIndex, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.administration_rotation_down, name))
                }
            }
        }
        val changed = order != shownIds || !schedule.isSaved
        if (changed && order.isNotEmpty()) {
            Button(
                onClick = { onSave(schedule.start, order) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 8.dp),
            ) { Text(stringResource(R.string.administration_rotation_save)) }
        }
    }
}
