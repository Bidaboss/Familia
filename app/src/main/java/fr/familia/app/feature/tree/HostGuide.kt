package fr.familia.app.feature.tree

import fr.familia.app.core.model.ChallengeRotation
import fr.familia.app.core.model.HostedChallenge
import fr.familia.app.core.model.HostedIllustration
import fr.familia.app.core.model.HostedKind
import fr.familia.app.core.model.HostedStatus
import fr.familia.app.core.model.MonthChallenge
import java.time.LocalDate
import java.time.YearMonth

/** What the guide offers for each kind (step 1): a title with the target, an emoji and a drawing. */
data class KindStart(val title: String, val emoji: String, val illustration: HostedIllustration)

/** The guide of the challenge of a foyer (récit 35 bis): the logic, without Android. */
object HostGuide {

    /** From this many days before its month, the foyer sees « C'est à vous » (it is told at 7). */
    const val DAYS_BEFORE = 7

    /** Emojis to choose from at step 3. */
    val emojis = listOf("📸", "🌳", "🍲", "💛", "🎨", "⭐", "☀️", "🌈", "🎶", "🌻")

    fun start(kind: HostedKind, target: Int): KindStart = when (kind) {
        HostedKind.Photo -> KindStart("$target photos de notre mois", "📸", HostedIllustration.Camera)
        HostedKind.Outing -> KindStart("$target sorties au grand air", "🌳", HostedIllustration.Leaf)
        HostedKind.Cooking -> KindStart("$target plats faits maison", "🍲", HostedIllustration.Pot)
        HostedKind.Kindness -> KindStart("$target gestes gentils", "💛", HostedIllustration.Heart)
        HostedKind.Creative -> KindStart("$target créations de nos mains", "🎨", HostedIllustration.Star)
    }

    /** About one Bonne nouvelle per member over the month, within the limits of the rules. */
    fun suggestedTarget(familySize: Int): Int = familySize.coerceIn(5, 30)

    /**
     * The month the foyer [householdId] can prepare now, or null: the month in progress while
     * nobody has moved its gauge, else the next one from [DAYS_BEFORE] days before it. Not once
     * the foyer has published or passed its turn.
     */
    fun turn(
        rotation: ChallengeRotation?,
        householdId: String,
        today: LocalDate,
        challenge: MonthChallenge?,
        hostedNow: HostedChallenge?,
        hostedNext: HostedChallenge?,
    ): YearMonth? {
        val month = YearMonth.from(today)
        val open = { hosted: HostedChallenge? -> hosted == null || hosted.status == HostedStatus.Draft }
        val gaugeStill = challenge == null || (challenge.progress == 0 && !challenge.achieved)
        if (rotation?.hostOf(month) == householdId && open(hostedNow) && gaugeStill) return month
        val next = month.plusMonths(1)
        val soon = !today.isBefore(next.atDay(1).minusDays(DAYS_BEFORE.toLong()))
        if (rotation?.hostOf(next) == householdId && open(hostedNext) && soon) return next
        return null
    }
}
