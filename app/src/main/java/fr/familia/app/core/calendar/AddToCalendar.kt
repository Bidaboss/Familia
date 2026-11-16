package fr.familia.app.core.calendar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import fr.familia.app.R

/**
 * Opens the phone's calendar on a new event already filled in (PRD, récit 30): the insert intent
 * of the Android calendar, so no file and no permission. The member confirms in their calendar.
 */
fun addToCalendar(context: Context, entry: CalendarEntry) {
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, entry.title)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, entry.beginMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, entry.endMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, entry.allDay)
        .putExtra(CalendarContract.Events.DESCRIPTION, entry.description)
        .putExtra(CalendarContract.Events.EVENT_TIMEZONE, CalendarEntry.PARIS.id)
    entry.location?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
    entry.rrule?.let { intent.putExtra(CalendarContract.Events.RRULE, it) }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.calendar_none, Toast.LENGTH_LONG).show()
    }
}
