package fr.familia.app.feature.goodnews

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.familia.app.R
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.ui.formatDay
import fr.familia.app.core.ui.formatTime
import java.time.Instant
import java.time.ZoneId

/** "Aujourd'hui à 14 h 05", "Hier à 9 h 30" or "Samedi 21 novembre". */
@Composable
fun postedLabel(at: Instant?, now: Instant, zone: ZoneId = FAMILY_ZONE): String {
    if (at == null) return stringResource(R.string.goodnews_just_now)
    val day = at.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val time = formatTime(at.atZone(zone).toLocalTime())
    return when (day) {
        today -> stringResource(R.string.goodnews_today_at, time)
        today.minusDays(1) -> stringResource(R.string.goodnews_yesterday_at, time)
        else -> formatDay(day, today)
    }
}

/** "Camille", "Camille, pour Noé". */
@Composable
fun whoLabel(item: GoodNewsItem): String {
    val author = item.author?.firstName ?: item.news.authorName
    return item.forName?.let { stringResource(R.string.goodnews_author_for, author, it) } ?: author
}
