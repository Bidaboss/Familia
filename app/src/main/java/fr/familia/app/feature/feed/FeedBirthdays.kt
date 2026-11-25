package fr.familia.app.feature.feed

import fr.familia.app.core.data.gazetteThreadId
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.FAMILY_ZONE
import fr.familia.app.core.model.Birthday
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.upcomingBirthdays
import fr.familia.app.core.theme.dayHeaderTextStyle
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.frenchDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

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

/**
 * The card of the jour J (DESIGN-59, maquette 1): a large peach card, the photo in a peach ring,
 * « Léon fête ses 10 ans » in the title font, the date, and a black « Lui souhaiter ». Mine: the same
 * card, « C'est ton anniversaire ! » and no button. Pinned at the top of « Aujourd'hui », the
 * youngest first ([pinRank] is the age). Nothing about gifts or surprises: the person sees it.
 */
class BirthdayFeedCard(val birthday: Birthday, private val wishes: Boolean) : FeedCard {
    override val key: String = "birthday:${birthday.member.id}:${birthday.date}"

    /** J'aime and comments on its thread (récit 115). */
    override val social: FeedSocial = FeedSocial(gazetteThreadId(key), title = R.string.feed_detail_birthday)

    /** The start of the day: the card belongs to its day, and [pinRank] puts it first there. */
    override val at: Instant = birthday.date.atStartOfDay(FAMILY_ZONE).toInstant()
    override val authorId: String? = null
    override val pinRank: Int = birthday.age

    @Composable
    override fun Content(context: FeedContext) {
        val navigation = LocalFeedNavigation.current
        val member = birthday.member
        val mine = member.id == context.me.id
        val open = { navigation.onOpenMember(member.id) }
        val title = if (mine) {
            stringResource(R.string.feed_birthday_mine)
        } else {
            pluralStringResource(R.plurals.feed_birthday_turns, birthday.age, member.firstName, birthday.age)
        }
        val spoken = if (mine) {
            stringResource(R.string.feed_birthday_mine_a11y)
        } else {
            pluralStringResource(R.plurals.feed_birthday_a11y, birthday.age, member.firstName, birthday.age)
        }
        val date = frenchDate(birthday.date, "EEEE d MMMM").replaceFirstChar { it.titlecase(Locale.FRANCE) }
        val shape = RoundedCornerShape(22.dp)
        Surface(
            shape = shape,
            color = BirthdayPeach,
            contentColor = BirthdayInk,
            border = BorderStroke(1.5.dp, FeedTone.Peach.bar),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.clickable(onClick = open)) {
                Confetti(Modifier.matchParentSize())
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        Modifier.clearAndSetSemantics { contentDescription = spoken },
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.feed_birthday_pill),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = FeedTone.Peach.label,
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(
                                Modifier
                                    .border(3.dp, BirthdayRing, CircleShape)
                                    .padding(5.dp),
                            ) {
                                MemberAvatar(member, size = 72.dp)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(title, style = dayHeaderTextStyle(), color = BirthdayInk)
                                Text(date, style = MaterialTheme.typography.bodyLarge, color = BirthdayInkSoft)
                            }
                        }
                    }
                    if (wishes && !mine && !context.readOnly) {
                        Button(
                            onClick = open,
                            colors = ButtonDefaults.buttonColors(containerColor = BirthdayInk, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            Text(stringResource(R.string.feed_birthday_wish), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** Kept the same in light and dark, like the tones of the fil: the card sits on its own peach. */
private val BirthdayPeach = Color(0xFFFDEDE4)
private val BirthdayRing = Color(0xFFF2A07F)
private val BirthdayInk = Color(0xFF1F1B18)
private val BirthdayInkSoft = Color(0xFF5A4A40)

/** Light confetti along the top, fixed: decorative, nothing moves. */
private val ConfettiPieces = listOf(
    Triple(0.06f, 0.05f, 0xFFC9B8F5), Triple(0.20f, 0.03f, 0xFFF6D27A), Triple(0.34f, 0.06f, 0xFFF9B99A),
    Triple(0.62f, 0.02f, 0xFFF2A9C9), Triple(0.75f, 0.05f, 0xFFA8E0C2), Triple(0.92f, 0.10f, 0xFFA9D2F2),
)

@Composable
private fun Confetti(modifier: Modifier) {
    Canvas(modifier) {
        val piece = Size(5.dp.toPx(), 11.dp.toPx())
        ConfettiPieces.forEachIndexed { index, (x, y, color) ->
            val topLeft = Offset(size.width * x, size.height * y + 4.dp.toPx())
            rotate(if (index % 2 == 0) -25f else 30f, pivot = topLeft + Offset(piece.width / 2, piece.height / 2)) {
                drawRoundRect(Color(color), topLeft, piece, CornerRadius(piece.width / 2))
            }
        }
    }
}
