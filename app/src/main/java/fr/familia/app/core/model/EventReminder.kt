package fr.familia.app.core.model

import androidx.annotation.StringRes
import fr.familia.app.R
import java.time.LocalTime

/**
 * « Me le rappeler » (PRD rév. 248, récit 56): when a reminder rings, the same keys as
 * functions/src/reminders.ts and the rules.
 */
enum class ReminderOffset(val key: String, @StringRes val label: Int) {
    OneHour("h1", R.string.reminder_h1),
    ThreeHours("h3", R.string.reminder_h3),
    Morning("morning", R.string.reminder_morning),
    TwoDays("d2", R.string.reminder_d2),
    OneWeek("w1", R.string.reminder_w1),
    ;

    companion object {
        fun fromKey(key: String?): ReminderOffset? = entries.firstOrNull { it.key == key }

        /** Known keys only, once each, in their order. */
        fun fromKeys(keys: Collection<*>?): Set<ReminderOffset> = entries.filter { o -> keys?.contains(o.key) == true }.toSet()

        /**
         * The choices that ring for an event at [time] (null: all day). An all-day event has no
         * « 1 h avant » nor « 3 h avant »; an event at 8 h or before has no « le matin à 8 h ».
         */
        fun choicesFor(time: LocalTime?): List<ReminderOffset> = entries.filter { o ->
            when (o) {
                OneHour, ThreeHours -> time != null
                Morning -> time == null || time.isAfter(LocalTime.of(8, 0))
                TwoDays, OneWeek -> true
            }
        }
    }
}

/**
 * What a member asked for on one event: [own] on this event, [series] on every occurrence of its
 * series, [defaults] for the events they come to. Null: nothing asked at that level.
 */
data class EventReminders(
    val own: Set<ReminderOffset>? = null,
    val series: Set<ReminderOffset>? = null,
    val defaults: Set<ReminderOffset> = emptySet(),
) {
    /** What rings, the way the server reads it. */
    fun effective(coming: Boolean): Set<ReminderOffset> = own ?: series ?: if (coming) defaults else emptySet()

    /** The defaults are what rings: nothing of its own and nothing on the series. */
    fun byDefault(coming: Boolean): Boolean = own == null && series == null && coming && defaults.isNotEmpty()
}
