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
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.frenchDate
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

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
    val day = frenchDate(consumption.day, "EEEE d MMMM")
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
            Consumption.Limit.Photos -> R.string.consumption_photos
            Consumption.Limit.Downloads -> R.string.consumption_downloads
        },
    )
    val figures = when (gauge.limit) {
        Consumption.Limit.Storage -> stringResource(R.string.consumption_storage_figures, sizeText(gauge.used, binary = true))
        Consumption.Limit.Photos -> stringResource(R.string.consumption_photos_figures, sizeText(gauge.used, binary = false))
        Consumption.Limit.Downloads -> stringResource(R.string.consumption_downloads_figures, sizeText(gauge.used, binary = false))
        else -> stringResource(R.string.consumption_figures, integer(gauge.used), integer(gauge.limit.free))
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

/**
 * A size for the card: in Mo below 1 Go, so a small figure never reads « 0 Go », then in Go with
 * one decimal. Firestore counts in binary units ([binary]), Cloud Storage in decimal ones.
 */
internal fun sizeText(bytes: Long, binary: Boolean): String {
    val mega = if (binary) 1024.0 * 1024 else 1_000_000.0
    val giga = if (binary) 1024.0 * 1024 * 1024 else 1_000_000_000.0
    if (bytes < giga) return "${integer((bytes / mega).toLong())} Mo"
    val format = NumberFormat.getNumberInstance(Locale.FRANCE).apply { maximumFractionDigits = 1 }
    return "${format.format(bytes / giga)} Go"
}

/** Fictional figures for previews and screenshots. */
internal fun previewConsumption(): ConsumptionCard = ConsumptionCard.Ready(
    Consumption(
        day = LocalDate.of(2026, 11, 20),
        reads = 42_300,
        writes = 1_850,
        deletes = 40,
        storageBytes = 12L * 1024 * 1024,
        photoBytes = 86_000_000,
        monthDownloadBytes = 1_200_000_000,
        monthCost = 0.0,
    ),
)
