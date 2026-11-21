package fr.familia.app.feature.birthdays

import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClaySpacing
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.dayShortLabel
import java.time.LocalDate

/** Birthdays tab: every member's next birthday, nearest first. Stateless. */
@Composable
fun BirthdaysScreen(
    state: BirthdaysUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** « Ajouter à mon agenda » on each birthday (PRD, récit 30); hidden when null. */
    onAddToCalendar: ((Birthday) -> Unit)? = null,
    /** « Partager » on each birthday (PRD, récit 31), while the Partager module is on; hidden when null. */
    onShare: ((Birthday) -> Unit)? = null,
) {
    when (state) {
        BirthdaysUiState.Loading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        BirthdaysUiState.Error -> EmptyState(
            icon = Icons.Outlined.Cake,
            title = stringResource(R.string.birthdays_error_title),
            body = stringResource(R.string.birthdays_error_body),
            modifier = modifier,
            action = {
                Button(onClick = onRetry, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_retry))
                }
            },
        )
        is BirthdaysUiState.Success -> if (state.birthdays.isEmpty() && state.missingDate.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Cake,
                title = stringResource(R.string.birthdays_empty_title),
                body = stringResource(R.string.birthdays_empty_body),
                modifier = modifier,
            )
        } else {
            // Cards rather than lines between rows (refonte globale, lot 2).
            LazyColumn(
                modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = ClaySpacing.screen, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.birthdays, key = { "birthday-${it.member.id}" }) { birthday ->
                    BirthdayRow(
                        birthday,
                        state.today,
                        onAddToCalendar?.let { add -> { add(birthday) } },
                        onShare?.let { share -> { share(birthday) } },
                    )
                }
                if (state.missingDate.isNotEmpty()) {
                    item(key = "missing") { MissingDates(state.missingDate) }
                }
            }
        }
    }
}

@Composable
private fun BirthdayRow(birthday: Birthday, today: LocalDate, onAddToCalendar: (() -> Unit)?, onShare: (() -> Unit)?) {
    val isToday = birthday.date == today
    // Today's birthday stands out with the birthday tint of the theme.
    val tint = if (isToday) ClayTint.Birthday else ClayTint.Plain
    val secondary = if (isToday) FamiliaClay.colors.onTintVariant(ClayTint.Birthday) else MaterialTheme.colorScheme.onSurfaceVariant
    ClayCard(Modifier.fillMaxWidth(), tint = tint, contentPadding = PaddingValues(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MemberAvatar(birthday.member)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(birthday.member.firstName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        R.string.birthday_when_age,
                        dayShortLabel(birthday.date, today),
                        pluralStringResource(R.plurals.birthday_age, birthday.age, birthday.age),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = secondary,
                )
            }
            if (onAddToCalendar != null) {
                // 56 dp target (Argile); the label names the member for TalkBack.
                IconButton(onClick = onAddToCalendar, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Outlined.EventAvailable,
                        contentDescription = stringResource(R.string.calendar_add_birthday, birthday.member.firstName),
                    )
                }
            }
            if (onShare != null) {
                IconButton(onClick = onShare, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.share_birthday_button, birthday.member.firstName),
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingDates(members: List<Member>) {
    ClayCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            stringResource(R.string.birthdays_missing_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.birthdays_missing_body, members.joinToString(", ") { it.firstName }),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Fictional birthdays for previews and screenshots. */
internal fun previewBirthdays(): BirthdaysUiState.Success {
    fun member(id: String, name: String, birth: LocalDate?) =
        Member(id, name, null, birth, "h1", MemberProfile.Adult, false, emptyList(), null)
    val today = LocalDate.of(2026, 11, 14)
    return BirthdaysUiState.Success(
        today = today,
        birthdays = listOf(
            Birthday(member("a", "Camille", LocalDate.of(1990, 11, 14)), today, 36),
            Birthday(member("b", "Noé", LocalDate.of(2016, 11, 15)), today.plusDays(1), 10),
            Birthday(member("c", "Inès", LocalDate.of(1958, 3, 2)), LocalDate.of(2027, 3, 2), 69),
        ),
        missingDate = listOf(member("d", "Jules", null)),
    )
}

@FamiliaPreviews
@Composable
private fun BirthdaysScreenPreview() {
    FamiliaTheme { Surface { BirthdaysScreen(previewBirthdays(), onRetry = {}) } }
}

@FamiliaPreviews
@Composable
private fun BirthdaysScreenEmptyPreview() {
    FamiliaTheme {
        Surface { BirthdaysScreen(BirthdaysUiState.Success(LocalDate.of(2026, 11, 14), emptyList(), emptyList()), onRetry = {}) }
    }
}
