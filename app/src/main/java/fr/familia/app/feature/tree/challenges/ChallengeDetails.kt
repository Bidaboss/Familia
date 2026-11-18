package fr.familia.app.feature.tree.challenges

import fr.familia.app.core.model.ChallengeMeasure
import fr.familia.app.core.model.MonthChallenge
import java.time.LocalDate
import java.time.YearMonth

/** The first challenge of the family (récit 35) and the first question of Monday (récit 37). */
internal val FIRST_CHALLENGE: YearMonth = YearMonth.of(2026, 12)
internal val FIRST_QUESTION: LocalDate = LocalDate.of(2026, 11, 30)

/** The useful button of « Comment ça marche », or none. */
enum class ChallengeAction { AddPhoto, ShareGoodNews, Done, None }

/**
 * What « Comment ça marche » says about a challenge (PRD récit 65): what counts, the goal and where
 * the family is, until when, what it wins, and the useful button. Never a name, never a ranking.
 * [endsOn] is the last day of the month while it runs; null for a challenge of a month gone by.
 * [hostNote] is the word the foyer added when it prepared it, or null for the automatic text.
 */
data class ChallengeExplanation(
    val month: YearMonth,
    val title: String,
    val emoji: String,
    val measure: ChallengeMeasure,
    val progress: Int,
    val target: Int,
    val achieved: Boolean,
    val endsOn: LocalDate?,
    val hostNote: String?,
    val action: ChallengeAction,
)

/** What the sheet shows: a challenge, or the principle of the challenges or of the question, with its first day while it is to come. */
sealed interface ChallengeSheet {
    data class Challenge(val explanation: ChallengeExplanation) : ChallengeSheet
    data class ChallengePrinciple(val startsOn: LocalDate?) : ChallengeSheet
    data class QuestionPrinciple(val startsOn: LocalDate?) : ChallengeSheet
}

/** [canShareGoodNews]: Bonnes nouvelles is on, so the button has somewhere to go. */
fun explain(challenge: MonthChallenge, today: LocalDate, hostNote: String?, canShareGoodNews: Boolean): ChallengeExplanation {
    val running = challenge.month == YearMonth.from(today)
    val action = when {
        challenge.achieved -> ChallengeAction.Done
        !running -> ChallengeAction.None
        challenge.measure == ChallengeMeasure.Linked -> ChallengeAction.AddPhoto
        canShareGoodNews -> ChallengeAction.ShareGoodNews
        else -> ChallengeAction.None
    }
    return ChallengeExplanation(
        month = challenge.month,
        title = challenge.title,
        emoji = challenge.emoji,
        measure = challenge.measure,
        progress = challenge.progress.coerceAtMost(challenge.target),
        target = challenge.target,
        achieved = challenge.achieved,
        endsOn = challenge.month.atEndOfMonth().takeIf { running },
        hostNote = hostNote?.trim()?.takeIf { it.isNotEmpty() && challenge.hostHouseholdId != null },
        action = action,
    )
}

/** The card of the month without a challenge: the principle, with the first day while it is to come. */
fun challengePrinciple(today: LocalDate): ChallengeSheet =
    ChallengeSheet.ChallengePrinciple(FIRST_CHALLENGE.atDay(1).takeIf { today.isBefore(it) })

/** The card of the question while there is none: the principle, with the first Monday while it is to come. */
fun questionPrinciple(today: LocalDate): ChallengeSheet =
    ChallengeSheet.QuestionPrinciple(FIRST_QUESTION.takeIf { today.isBefore(it) })
