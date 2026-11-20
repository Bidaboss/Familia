package fr.familia.app.feature.usage

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import fr.familia.app.core.ui.FamiliaTopBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.UsageScreen
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.MemberAvatar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import fr.familia.app.core.ui.LoadingState

/**
 * « Utilisation » (PRD, récit 1 nonies): for the admin principal, the family then each person; for
 * a responsible adult, the time of their children (carte EXPERT-5). Never what anyone read or wrote.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageReportScreen(state: UsageUiState, onRetry: () -> Unit, onBack: () -> Unit, today: LocalDate = LocalDate.now()) {
    val full = (state as? UsageUiState.Ready)?.family != null
    Scaffold(
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(if (full || state !is UsageUiState.Ready) R.string.usage_title else R.string.usage_children_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                UsageUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                UsageUiState.Error -> EmptyState(
                    icon = Icons.Outlined.QueryStats,
                    title = stringResource(R.string.usage_error_title),
                    body = stringResource(R.string.usage_error_body),
                    action = {
                        Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_retry)) }
                    },
                )
                is UsageUiState.Ready -> ReadyContent(state, today)
            }
        }
    }
}

@Composable
private fun ReadyContent(state: UsageUiState.Ready, today: LocalDate) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        val family = state.family
        if (family != null) {
            item(key = "family") { FamilyCard(family) }
            item(key = "health") { HealthCard(family.health) }
            item(key = "people-title") { SectionTitle(stringResource(R.string.usage_people_title)) }
        }
        if (state.people.isEmpty()) {
            item(key = "empty") {
                Text(stringResource(R.string.usage_children_empty), style = MaterialTheme.typography.bodyLarge)
            }
        }
        items(state.people, key = { it.member.id }) { person -> PersonCard(person, today, showLastDay = family != null) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
private fun FamilyCard(family: FamilyUsage) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.usage_family_title))
            Text(
                stringResource(R.string.usage_active, family.active7, family.active30, family.withApp),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(stringResource(R.string.usage_opens_title), style = MaterialTheme.typography.titleMedium)
            OpensChart(family.opensPerDay)
            Text(stringResource(R.string.usage_screens_title), style = MaterialTheme.typography.titleMedium)
            if (family.screens.isEmpty()) {
                Text(stringResource(R.string.usage_nothing_yet), style = MaterialTheme.typography.bodyMedium)
            }
            ScreenTable(family.screens)
        }
    }
}

/** Openings per day over 30 days, as bars; read aloud as a sentence. */
@Composable
private fun OpensChart(opens: List<Pair<LocalDate, Int>>) {
    val total = opens.sumOf { it.second }
    val busiest = opens.maxByOrNull { it.second }?.takeIf { it.second > 0 }
    val description = if (busiest == null) {
        stringResource(R.string.usage_opens_none)
    } else {
        stringResource(R.string.usage_opens_description, total, busiest.second, shortDate(busiest.first))
    }
    val bar = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val max = (busiest?.second ?: 1).coerceAtLeast(1)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val slot = size.width / opens.size.coerceAtLeast(1)
        val gap = slot * 0.25f
        val radius = CornerRadius(gap, gap)
        opens.forEachIndexed { i, (_, count) ->
            val h = if (count == 0) 2.dp.toPx() else size.height * count / max
            drawRoundRect(
                color = if (count == 0) empty else bar,
                topLeft = Offset(i * slot + gap / 2, size.height - h),
                size = Size(slot - gap, h),
                cornerRadius = radius,
            )
        }
    }
    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One line per part of the app: its name, then 7 and 30 days. */
@Composable
private fun ScreenTable(screens: List<ScreenTime>) {
    if (screens.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text("", Modifier.weight(1f))
            HeaderCell(stringResource(R.string.usage_7_days))
            HeaderCell(stringResource(R.string.usage_30_days))
        }
        screens.forEach { time ->
            val name = stringResource(time.screen.labelRes)
            val week = duration(time.seconds7)
            val month = duration(time.seconds30)
            val line = stringResource(R.string.usage_screen_line, name, week, month)
            Row(Modifier.clearAndSetSemantics { contentDescription = line }) {
                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                ValueCell(week)
                ValueCell(month)
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = Modifier.padding(start = 12.dp),
    )
}

@Composable
private fun ValueCell(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End, modifier = Modifier.padding(start = 12.dp))
}

@Composable
private fun HealthCard(weeks: List<WeekHealth>) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.usage_health_title))
            weeks.forEach { week ->
                Text(
                    stringResource(
                        R.string.usage_health_week,
                        shortDate(week.weekStart),
                        pluralStringResource(R.plurals.usage_health_posts, week.posts, week.posts),
                        pluralStringResource(R.plurals.usage_health_reactions, week.reactions, week.reactions),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun PersonCard(person: PersonUsage, today: LocalDate, showLastDay: Boolean) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MemberAvatar(person.member)
                Column(Modifier.weight(1f)) {
                    Text(person.member.firstName, style = MaterialTheme.typography.titleMedium)
                    if (showLastDay) {
                        Text(
                            lastVisit(person.lastDay, today),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.usage_person_totals, duration(person.seconds7), duration(person.seconds30)),
                style = MaterialTheme.typography.bodyLarge,
            )
            ScreenTable(person.screens)
        }
    }
}

@Composable
private fun lastVisit(day: LocalDate?, today: LocalDate): String = when {
    day == null -> stringResource(R.string.usage_never)
    day == today -> stringResource(R.string.usage_last_today)
    day == today.minusDays(1) -> stringResource(R.string.usage_last_yesterday)
    else -> stringResource(R.string.usage_last_on, shortDate(day))
}

private val shortDateFormat = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)

private fun shortDate(day: LocalDate): String = day.format(shortDateFormat)

/** « 0 min », « 12 min », « 1 h 05 ». */
@Composable
internal fun duration(seconds: Int): String {
    val minutes = seconds / 60
    return if (minutes < 60) {
        stringResource(R.string.usage_minutes, minutes)
    } else {
        stringResource(R.string.usage_hours, minutes / 60, (minutes % 60).toString().padStart(2, '0'))
    }
}

/** A fictional family for the previews and the screenshots (no real names). */
internal fun previewUsageState(today: LocalDate): UsageUiState.Ready {
    fun member(id: String, name: String, profile: MemberProfile = MemberProfile.Adult) =
        Member(id = id, firstName = name, email = null, birthDate = null, householdId = "h1", profile = profile, isAdmin = false, parentIds = emptyList(), uid = null)
    val screens = listOf(
        ScreenTime(UsageScreen.Planning, 2_700, 9_900),
        ScreenTime(UsageScreen.Home, 1_500, 6_000),
        ScreenTime(UsageScreen.Gifts, 600, 3_000),
    )
    return UsageUiState.Ready(
        family = FamilyUsage(
            active7 = 5,
            active30 = 7,
            withApp = 9,
            opensPerDay = (29 downTo 0).map { today.minusDays(it.toLong()) to listOf(3, 5, 8, 2, 0, 6, 9)[it % 7] },
            screens = screens,
            health = (3 downTo 0).map { WeekHealth(today.minusWeeks(it.toLong()), 2 + it, 7 + 2 * it) },
        ),
        people = listOf(
            PersonUsage(member("a", "Camille"), today, 3_900, 15_000, screens),
            PersonUsage(member("b", "Bastien"), today.minusDays(1), 600, 2_400, screens.take(1)),
            PersonUsage(member("c", "Inès", MemberProfile.ChildWithApp), null, 0, 0, emptyList()),
        ),
    )
}

@FamiliaPreviews
@Composable
private fun UsageReportScreenPreview() {
    val today = LocalDate.of(2026, 11, 30)
    FamiliaTheme { Surface { UsageReportScreen(previewUsageState(today), onRetry = {}, onBack = {}, today = today) } }
}
