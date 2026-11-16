package fr.familia.app.feature.crashes

import fr.familia.app.core.model.CrashGroup
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val claudeDay = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH'h'mm", Locale.FRANCE)

/**
 * « Copier pour Claude » (récit 6 sexies, rév. 220), in the shape of « Partager pour Claude »
 * (#179): what crashed, how often, on which phones and versions, and the latest trace. It never
 * says who had it: the first names stay in the app.
 */
fun crashForClaude(group: CrashGroup, zone: ZoneId = ZoneId.systemDefault()): String {
    val latest = group.latest
    val phones = group.reports
        .map { r -> listOfNotNull(r.android.takeIf { it.isNotBlank() }?.let { "Android $it" }, r.device.takeIf { it.isNotBlank() }).joinToString(" · ") }
        .filter { it.isNotEmpty() }
        .distinct()
    return buildString {
        appendLine("Plantage reçu dans l'appli Familia (Administration > Plantages) : ${group.errorName}.")
        group.place?.let { appendLine("Dans notre code : $it.") }
        val times = if (group.count == 1) "1 fois" else "${group.count} fois"
        val people = if (group.people == 1) "1 personne" else "${group.people} personnes"
        if (group.count == 1) {
            appendLine("Vu $times, chez $people, le ${claudeDay.format(latest.crashedAt.atZone(zone))}.")
        } else {
            appendLine("Vu $times, chez $people, du ${claudeDay.format(group.firstAt.atZone(zone))} au ${claudeDay.format(latest.crashedAt.atZone(zone))}.")
        }
        appendLine("Versions : ${group.versions.joinToString(", ") { "Familia $it" }}.")
        if (phones.isNotEmpty()) appendLine("Téléphones : ${phones.joinToString(" ; ")}.")
        if (latest.screen.isNotBlank()) appendLine("Écran : ${latest.screen}.")
        appendLine()
        appendLine("Trace du dernier (sans le message de l'erreur, qui reste sur le téléphone) :")
        appendLine(latest.trace)
        appendLine()
        append("Peux-tu chercher la cause dans le code de Familia et proposer une correction ?")
    }
}
