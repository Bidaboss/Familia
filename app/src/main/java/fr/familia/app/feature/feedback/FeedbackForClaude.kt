package fr.familia.app.feature.feedback

import fr.familia.app.core.model.Feedback
import fr.familia.app.core.model.FeedbackOrigin
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val claudeDay = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH'h'mm", Locale.FRANCE)

/**
 * « Copier pour Claude » (récit 6 sexies): the request as a text to paste into a Claude session
 * that codes the app. It says what, where and on which phone, but never who sent it: the first
 * name stays in the app.
 */
fun feedbackForClaude(item: Feedback, zone: ZoneId = ZoneId.systemDefault()): String {
    val c = item.context
    val kind = when (item.kind) {
        Feedback.Kind.Idea -> "une idée"
        Feedback.Kind.Problem -> "un problème"
    }
    val status = when (item.status) {
        Feedback.Status.New -> "Nouvelle"
        Feedback.Status.Planned -> "Prévue"
        Feedback.Status.Done -> "Faite"
        Feedback.Status.Declined -> "Pas retenue"
    }
    val screen = if (c.screen == FeedbackOrigin.Help) "l'Aide" else "l'onglet Famille"
    val phone = listOfNotNull(
        c.appVersion.takeIf { it.isNotBlank() }?.let { "Familia $it" },
        c.android.takeIf { it.isNotBlank() }?.let { "Android $it" },
        c.device.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    return buildString {
        appendLine("Demande reçue dans l'appli Familia (« Une idée ? Un souci ? ») : $kind.")
        item.createdAt?.let { appendLine("Envoyée le ${claudeDay.format(it.atZone(zone))}, depuis $screen.") }
            ?: appendLine("Envoyée depuis $screen.")
        if (phone.isNotEmpty()) appendLine("Téléphone : $phone.")
        appendLine("État : $status.")
        item.note?.let { appendLine("Déjà répondu : $it") }
        if (item.screenshot != null) appendLine("Une capture d'écran est jointe dans l'appli, dans Administration > Retours.")
        appendLine()
        appendLine("Texte :")
        appendLine(item.text)
        appendLine()
        append(
            if (item.kind == Feedback.Kind.Problem) {
                "Peux-tu chercher la cause dans le code de Familia et proposer une correction ?"
            } else {
                "Peux-tu dire comment l'ajouter à Familia, et ce que ça demanderait ?"
            },
        )
    }
}
