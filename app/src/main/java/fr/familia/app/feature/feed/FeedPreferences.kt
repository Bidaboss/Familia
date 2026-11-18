package fr.familia.app.feature.feed

import android.content.Context
import androidx.core.content.edit
import java.time.Instant

/** When each member last looked at « Le fil » on this phone: the badge counts what came since. */
class FeedPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("familia", Context.MODE_PRIVATE)

    fun seenAt(memberId: String): Instant? =
        prefs.getLong(key(memberId), 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli)

    fun setSeenAt(memberId: String, at: Instant) = prefs.edit { putLong(key(memberId), at.toEpochMilli()) }

    private fun key(memberId: String) = "feedSeen-$memberId"
}
