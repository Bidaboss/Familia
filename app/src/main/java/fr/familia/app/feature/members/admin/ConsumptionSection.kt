package fr.familia.app.feature.members.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Consumption
import fr.familia.app.core.ui.ClayCard
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClaySpacing

/**
 * The « Consommation » card (PRD, récit 1 sexies): how much of each free limit of the database
 * the family used on the latest day read, and the estimated cost of the month. Stateless.
 */
@Composable
internal fun ConsumptionSection(card: ConsumptionCard, modifier: Modifier = Modifier) {
    ClayCard(modifier.fillMaxWidth().padding(horizontal = ClaySpacing.screen, vertical = 8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ClayCardTitle(stringResource(R.string.consumption_title), style = MaterialTheme.typography.titleMedium)
            when (card) {
                ConsumptionCard.Waiting -> Text(stringResource(R.string.consumption_waiting), style = MaterialTheme.typography.bodyMedium)
                is ConsumptionCard.Ready -> ReadyConsumption(card.consumption)
            }
        }
    }
}

@Composable
private fun ReadyConsumption(consumption: Consumption) {
    val day = consumption.day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))
    Text(
        stringResource(R.string.consumption_day, day),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (consumption.failed) {
        Text(stringResource(R.string.consumption_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        return
    }
    consumption.gauges.forEach { GaugeLine(it) }
    val cost = NumberFormat.getCurrencyInstance(Locale.FRANCE).format(consumption.monthCost)
    Text(
        stringResource(R.string.consumption_cost, cost),
        style = MaterialTheme.typography.titleSmall,
        color = if (consumption.costNear) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
    Text(stringResource(R.string.consumption_cost_help), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun GaugeLine(gauge: Consumption.Gauge) {
    val label = stringResource(
        when (gauge.limit) {
            Consumption.Limit.Reads -> R.string.consumption_reads
            Consumption.Limit.Writes -> R.string.consumption_writes
            Consumption.Limit.Deletes -> R.string.consumption_deletes
            Consumption.Limit.Storage -> R.string.consumption_storage
        },
    )
    val figures = if (gauge.limit == Consumption.Limit.Storage) {
        stringResource(R.string.consumption_storage_figures, integer(gauge.used / (1024 * 1024)))
    } else {
        stringResource(R.string.consumption_figures, integer(gauge.used), integer(gauge.limit.free))
    }
    val percent = (gauge.share * 100).roundToInt()
    val color = if (gauge.near) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val spoken = stringResource(R.string.consumption_gauge_description, label, figures, percent)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        Text("$label · $figures", style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(progress = { gauge.share }, color = color, modifier = Modifier.fillMaxWidth())
    }
}

private fun integer(n: Long): String = NumberFormat.getIntegerInstance(Locale.FRANCE).format(n)

/** Fictional figures for previews and screenshots. */
internal fun previewConsumption(): ConsumptionCard = ConsumptionCard.Ready(
    Consumption(
        day = LocalDate.of(2026, 11, 20),
        reads = 42_300,
        writes = 1_850,
        deletes = 40,
        storageBytes = 12L * 1024 * 1024,
        monthCost = 0.0,
    ),
)
