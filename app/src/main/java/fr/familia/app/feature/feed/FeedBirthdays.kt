package fr.familia.app.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.upcomingBirthdays
import fr.familia.app.core.ui.MemberAvatar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate

/**
 * « Anniversaire du jour » (récit 67): the birthdays of [today] only, read from the family like the
 * Famille tab (every member sees every birthday). [wishes]: « Lui souhaiter » opens their page.
 */
class BirthdayFeedSource(
    private val members: List<Member>,
    private val today: LocalDate,
    private val wishes: Boolean,
) : FeedSource {
    override fun cards(window: FeedWindow): Flow<List<FeedCard>> =
        flowOf(birthdaysOn(members, today).map { BirthdayFeedCard(it, wishes) })
}

/** Whose birthday is [day], with the age they reach: the rule of the rest of the app. */
fun birthdaysOn(members: List<Member>, day: LocalDate): List<Birthday> =
    upcomingBirthdays(members, day).filter { it.date == day }

class BirthdayFeedCard(val birthday: Birthday, private val wishes: Boolean) : FeedCard {
    override val key: String = "birthday:${birthday.member.id}:${birthday.date}"

    /** The start of the day: under « Aujourd'hui », after what happened during it. */
    override val at: Instant = birthday.date.atStartOfDay(FAMILY_ZONE).toInstant()
    override val authorId: String? = null

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val tone = FeedTone.Peach
        val member = birthday.member
        val mine = member.id == context.me.id
        FeedCardShell(tone = tone, onOpen = { navigation.onOpenMember(member.id) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MemberAvatar(member, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.feed_label_birthday).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = tone.label,
                    )
                    Text(
                        if (mine) {
                            stringResource(R.string.feed_birthday_mine)
                        } else {
                            pluralStringResource(R.plurals.feed_birthday_age, birthday.age, member.firstName, birthday.age)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                if (wishes && !mine && !context.readOnly) {
                    FilledTonalButton(onClick = { navigation.onOpenMember(member.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.feed_birthday_wish))
                    }
                }
            }
        }
    }
}
