package fr.familia.app.feature.helprequests

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.familia.app.R
import fr.familia.app.core.model.HelpKind
import fr.familia.app.core.model.HelpRequest
import fr.familia.app.core.ui.dayShortLabel
import fr.familia.app.core.ui.formatTime
import java.time.LocalDate

@Composable
fun kindLabel(kind: HelpKind): String = stringResource(
    when (kind) {
        HelpKind.Childcare -> R.string.helprequest_kind_childcare
        HelpKind.Ride -> R.string.helprequest_kind_ride
        HelpKind.Other -> R.string.helprequest_kind_other
    },
)

/** "Demain · 18 h 00 à 22 h 00", "Samedi 21 novembre · dès 9 h 00" or just the day. */
@Composable
fun whenLabel(request: HelpRequest, today: LocalDate): String {
    val day = dayShortLabel(request.date, today)
    val start = request.startTime
    val end = request.endTime
    return when {
        start != null && end != null -> stringResource(R.string.helprequest_when_range, day, formatTime(start), formatTime(end))
        start != null -> stringResource(R.string.helprequest_when_from, day, formatTime(start))
        end != null -> stringResource(R.string.helprequest_when_until, day, formatTime(end))
        else -> day
    }
}
