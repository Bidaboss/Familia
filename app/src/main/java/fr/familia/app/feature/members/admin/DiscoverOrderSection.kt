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
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.feature.discover.Discover
import fr.familia.app.feature.discover.DiscoverModule
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)

/**
 * « Ordre d'À découvrir » in Administration (AVIS-19 partie 2): one module a week from Monday
 * 30/11. The admin principal moves a module up or down, then saves; the family sees the new order at
 * once. A module the tutorial already shows ([inTutorial]) keeps its place but has no week.
 */
@Composable
fun DiscoverOrderSection(saved: List<DiscoverModule>, inTutorial: Set<DiscoverModule>, onSave: (List<DiscoverModule>) -> Unit) {
    val savedKeys = saved.map { it.key }
    var keys by rememberSaveable(savedKeys) { mutableStateOf(savedKeys) }
    val order = keys.mapNotNull { key -> DiscoverModule.entries.firstOrNull { it.key == key } }
    val mondays = Discover.mondays(order, inTutorial)
    fun move(from: Int, to: Int) {
        if (to !in keys.indices) return
        keys = keys.toMutableList().apply { add(to, removeAt(from)) }
    }
    Column(Modifier.padding(horizontal = ClaySpacing.screen, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        order.forEachIndexed { i, module ->
            val name = stringResource(module.titleRes)
            val monday = mondays[module]
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (monday != null) {
                            stringResource(R.string.administration_discover_week, monday.format(dayFormat))
                        } else {
                            stringResource(R.string.administration_discover_in_tutorial)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { move(i, i - 1) }, enabled = i > 0, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.administration_rotation_up, name))
                }
                IconButton(onClick = { move(i, i + 1) }, enabled = i < order.lastIndex, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.administration_rotation_down, name))
                }
            }
        }
        if (keys != savedKeys) {
            Button(
                onClick = { onSave(order) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 8.dp),
            ) { Text(stringResource(R.string.administration_rotation_save)) }
        }
    }
}
