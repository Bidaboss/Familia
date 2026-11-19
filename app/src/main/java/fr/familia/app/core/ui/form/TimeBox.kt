package fr.familia.app.core.ui.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.ui.formatTime
import java.time.LocalTime

/**
 * The hour of a form (DESIGN-50): a box « À / 14 h 00 » that opens the clock, and « Sans heure »
 * beside it, chosen while there is no hour. [label] says what the hour is (« À », « De », « Jusqu'à »);
 * [onPick] gets null for « Sans heure ». [clearLabel] null hides « Sans heure » (an hour required).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeBox(
    time: LocalTime?,
    onPick: (LocalTime?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.form_time_at),
    clearLabel: String? = stringResource(R.string.event_no_time),
    initial: LocalTime = LocalTime.of(12, 0),
) {
    var clock by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val value = time?.let(::formatTime) ?: stringResource(R.string.form_time_pick)
    val spoken = "$label $value"
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.5.dp, if (time != null) colors.outline else colors.outlineVariant),
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 140.dp)
                .heightIn(min = 64.dp)
                .clickable(role = Role.Button) { clock = true }
                .clearAndSetSemantics {
                    contentDescription = spoken
                    role = Role.Button
                    onClick { clock = true; true }
                },
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(
                    value,
                    fontSize = if (time != null) 20.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (time != null) colors.onSurface else colors.onSurfaceVariant,
                )
            }
        }
        if (clearLabel != null) {
            FormChip(
                clearLabel,
                selected = time == null,
                onClick = { onPick(null) },
                modifier = Modifier.weight(1f).widthIn(min = 120.dp).heightIn(min = 64.dp),
            )
        }
    }
    if (clock) {
        val start = time ?: initial
        val pickerState = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { clock = false },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    onPick(LocalTime.of(pickerState.hour, pickerState.minute))
                    clock = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { clock = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
