package fr.familia.app.feature.planning

import fr.familia.app.feature.home.DayItem
import fr.familia.app.feature.home.DayOpen
import fr.familia.app.feature.home.DayTone
import fr.familia.app.feature.home.isFestive
import fr.familia.app.feature.home.shortWord
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Event
import fr.familia.app.core.model.EventTarget
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.model.Trip
import fr.familia.app.feature.events.edit.TargetChoice
import java.time.LocalDate
import java.time.LocalTime
import fr.familia.app.core.ui.LoadingState

/**
 * The Planning tab (PRODUIT-126, the only one since DESIGN-29 / PRODUIT-131): while it loads, a
 * wheel; on an error, « Réessayer »; then the new Planning, Semaine or Mois. Its filter and its « + »
 * are round buttons of the bar of the tab. Stateless.
 */
@Composable
fun PlanningScreen(
    state: PlanningUiState,
    onRetry: () -> Unit,
    me: Member?,
    onOpenEvent: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenTrip: (tripId: String) -> Unit = {},
    /** « Partager » on a birthday (PRD, récit 31); hidden when null. */
    onShareBirthday: ((Birthday) -> Unit)? = null,
    initialMode: PlanningMode = PlanningMode.Week,
) {
    Box(modifier.fillMaxSize()) {
        when (state) {
            PlanningUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
            PlanningUiState.Error -> EmptyState(
                icon = Icons.Outlined.Event,
                title = stringResource(R.string.planning_error_title),
                body = stringResource(R.string.planning_error_body),
                action = {
                    Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.action_retry))
                    }
                },
            )
            is PlanningUiState.Success -> PlanningV2(state, me, onOpenEvent, onOpenTrip, onShareBirthday, initialMode = initialMode)
        }
    }
}

/** Fictional planning for previews and screenshots. */
internal fun previewPlanning(): PlanningUiState.Success {
    fun item(id: String, title: String, date: LocalDate, time: LocalTime?, place: String?, target: TargetChoice, author: String, manage: Boolean) =
        PlanningItem(Event(id, title, date, time, place, null, target.target, "a", author), target, author, canManage = manage)
    val today = LocalDate.of(2026, 12, 19)
    val day1 = LocalDate.of(2026, 12, 20)
    val day2 = LocalDate.of(2026, 12, 24)
    val noe = Member("c", "Noé", null, LocalDate.of(2016, 12, 20), "h1", MemberProfile.ChildWithoutApp, false, listOf("a"), null)
    return PlanningUiState.Success(
        today = today,
        tripDays = setOf(LocalDate.of(2026, 12, 22), LocalDate.of(2026, 12, 23)),
        days = listOf(
            PlanningDay(
                today,
                listOf(item("0", "Cinéma", today, LocalTime.of(14, 30), null, TargetChoice(EventTarget.ForSelf("a"), null), "Camille", true)),
            ),
            PlanningDay(
                day1,
                birthdays = listOf(Birthday(noe, day1, 10)),
                items = listOf(
                    item("1", "Piscine", day1, LocalTime.of(10, 0), "Centre aquatique", TargetChoice(EventTarget.ForChild("c"), "Noé"), "Camille", true),
                    item("2", "Marché de Noël", day1, null, null, TargetChoice(EventTarget.ForHousehold("h2"), "Foyer Bernard"), "Inès", false),
                ),
            ),
            PlanningDay(
                LocalDate.of(2026, 12, 22),
                items = emptyList(),
                trips = listOf(
                    PlanningTrip(
                        Trip("t1", "Ski à Morzine", LocalDate.of(2026, 12, 22), LocalDate.of(2026, 12, 23), listOf("a"), listOf("a"), "a", "Camille"),
                        listOf("Camille", "Inès"),
                        ongoing = false,
                    ),
                ),
            ),
            PlanningDay(
                day2,
                listOf(item("3", "Repas de Noël", day2, LocalTime.of(19, 30), "Chez les parents", TargetChoice(EventTarget.ForFamily, null), "Camille", true).copy(coming = 7, maybe = 2)),
            ),
        ),
    )
}

@FamiliaPreviews
@Composable
private fun PlanningScreenErrorPreview() {
    FamiliaTheme { Surface { PlanningScreen(PlanningUiState.Error, {}, null, {}) } }
}

/** A day of the planning as the frise shows it: the birthdays, then the events by hour. */
internal fun planningDayItems(day: PlanningDay): List<DayItem> =
    day.birthdays.map {
        DayItem(null, it.member.firstName, null, shortWord(it.member.firstName), DayTone.Party, DayOpen.Birthday(it.member.id))
    } + day.items.map {
        DayItem(it.event.time, it.event.title, null, shortWord(it.event.title), if (isFestive(it.event.title)) DayTone.Party else DayTone.Organise, DayOpen.Event(it.event.id))
    }.sortedWith(compareBy(nullsLast()) { it.time })
