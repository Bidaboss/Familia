package fr.familia.app.feature.firststeps

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

/*
 * « Mes premiers pas » (PRD rév. 260, récit 61): during a member's first 14 days, a card of the
 * Accueil offers three gestures, each ticked by itself once done. No notification, no counter,
 * no ranking. Everything it keeps stays on the phone, so it works offline and « Voir comme » never
 * writes it.
 */

/** The three gestures, in the order of the card. */
enum class FirstStep { Photo, Tastes, Reaction }

/** How long the card is offered, counted from the first day it shows on this phone. */
const val FIRST_STEPS_DAYS = 14L

/** One line of the card: the gesture and whether it is done. */
data class FirstStepLine(val step: FirstStep, val done: Boolean)

/** What the card shows; none at all when the screen gets null. */
sealed interface FirstStepsCard {
    data class Steps(val lines: List<FirstStepLine>) : FirstStepsCard

    /** « Bravo, tu es prêt », the day the last gesture was done; gone the next day. */
    data object Bravo : FirstStepsCard
}

/**
 * What this phone keeps for a member, as days since the epoch. [startDay] is the first day the card
 * showed; [snoozedDay] the day of « Plus tard »; [doneDay] the day every gesture was done.
 * [sawUndone] is true once the card showed a gesture to do: a member who had done everything before
 * the card came never sees it, nor its « Bravo ».
 */
data class FirstStepsMarks(
    val startDay: Long? = null,
    val snoozedDay: Long? = null,
    val dismissed: Boolean = false,
    val doneDay: Long? = null,
    val sawUndone: Boolean = false,
)

/**
 * The card for [done] (the gestures offered, done or not) on [today], and the marks to keep.
 * Hidden once « Ne plus afficher » was touched, past [FIRST_STEPS_DAYS], the day of « Plus tard »,
 * and the day after « Bravo ».
 */
fun firstSteps(done: Map<FirstStep, Boolean>, marks: FirstStepsMarks, today: LocalDate): Pair<FirstStepsCard?, FirstStepsMarks> {
    val day = today.toEpochDay()
    if (marks.dismissed || done.isEmpty()) return null to marks
    val start = marks.startDay ?: day
    var kept = marks.copy(startDay = start)
    if (day - start >= FIRST_STEPS_DAYS || day < start) return null to kept
    if (done.values.all { it }) {
        if (!kept.sawUndone) return null to kept
        val doneDay = kept.doneDay ?: day
        kept = kept.copy(doneDay = doneDay)
        return (if (doneDay == day) FirstStepsCard.Bravo else null) to kept
    }
    kept = kept.copy(sawUndone = true, doneDay = null)
    if (kept.snoozedDay == day) return null to kept
    val lines = FirstStep.entries.mapNotNull { step -> done[step]?.let { FirstStepLine(step, it) } }
    return FirstStepsCard.Steps(lines) to kept
}

/** Where the marks of each member are kept. */
interface FirstStepsStore {
    fun load(memberId: String): FirstStepsMarks
    fun save(memberId: String, marks: FirstStepsMarks)
}

/** The phone's preferences, one set of keys per member (several members may share a phone). */
class FirstStepsPreferences(context: Context) : FirstStepsStore {
    private val prefs = context.getSharedPreferences("familia", Context.MODE_PRIVATE)

    override fun load(memberId: String) = FirstStepsMarks(
        startDay = prefs.long(key(memberId, "start")),
        snoozedDay = prefs.long(key(memberId, "snoozed")),
        dismissed = prefs.getBoolean(key(memberId, "dismissed"), false),
        doneDay = prefs.long(key(memberId, "done")),
        sawUndone = prefs.getBoolean(key(memberId, "sawUndone"), false),
    )

    override fun save(memberId: String, marks: FirstStepsMarks) = prefs.edit {
        putOrRemove(key(memberId, "start"), marks.startDay)
        putOrRemove(key(memberId, "snoozed"), marks.snoozedDay)
        putBoolean(key(memberId, "dismissed"), marks.dismissed)
        putOrRemove(key(memberId, "done"), marks.doneDay)
        putBoolean(key(memberId, "sawUndone"), marks.sawUndone)
    }

    private fun key(memberId: String, name: String) = "firstSteps-$name-$memberId"

    private fun android.content.SharedPreferences.long(key: String): Long? = if (contains(key)) getLong(key, 0) else null

    private fun android.content.SharedPreferences.Editor.putOrRemove(key: String, value: Long?) {
        if (value == null) remove(key) else putLong(key, value)
    }
}
