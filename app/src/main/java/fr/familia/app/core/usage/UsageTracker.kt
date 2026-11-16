package fr.familia.app.core.usage

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import fr.familia.app.core.data.UsageRepository
import fr.familia.app.core.model.UsageScreen
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Counts the time the app is in the foreground, screen by screen, on this phone (PRD, récit 1
 * nonies), and sends each day to usage/{memberId}/days/{day}: a finished day once, today at most
 * every 3 hours. Nothing is counted while the « Utilisation » module is off. One instance for the
 * whole app ([fr.familia.app.core.data.AppContainer]), so a rotation does not count a new opening.
 */
class UsageTracker(
    context: Context,
    private val repository: UsageRepository,
    private val today: () -> LocalDate = LocalDate::now,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sending = Mutex()

    private var log: UsageLog = load()
    private var screen: UsageScreen? = null
    private var segmentStart: Long? = null

    /** The app came to the foreground with [memberId] signed in. */
    @Synchronized
    fun start(memberId: String) {
        log = log.forMember(memberId).started(today(), nowMillis())
        segmentStart = elapsedMillis()
        save()
        send()
    }

    /** The member moved to another part of the app. */
    @Synchronized
    fun show(next: UsageScreen?) {
        flush()
        screen = next
    }

    /** The app went to the background, or the module was switched off. */
    @Synchronized
    fun stop() {
        flush()
        segmentStart = null
        log = log.stopped(nowMillis())
        save()
        send()
    }

    private fun flush() {
        val start = segmentStart ?: return
        val now = elapsedMillis()
        val current = screen
        if (current != null) log = log.addSeconds(today(), current, ((now - start) / 1000).toInt())
        // The part of a second left over counts with the next segment.
        segmentStart = now - (now - start) % 1000
    }

    private fun send() {
        val memberId = log.memberId ?: return
        scope.launch {
            sending.withLock {
                val due = synchronized(this@UsageTracker) { log.toSend(today(), nowMillis()) }
                for ((day, usage) in due) {
                    try {
                        repository.saveDay(memberId, day, usage)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Offline or refused: the day stays and goes with the next opening.
                        return@withLock
                    }
                    synchronized(this@UsageTracker) {
                        if (log.memberId == memberId) {
                            log = log.markSent(day, usage, today(), nowMillis())
                            save()
                        }
                    }
                }
            }
        }
    }

    private fun load(): UsageLog =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(UsageLog.serializer(), it) }.getOrNull() } ?: UsageLog()

    private fun save() {
        prefs.edit { putString(KEY, json.encodeToString(UsageLog.serializer(), log)) }
    }

    private companion object {
        const val PREFS = "familia_usage"
        const val KEY = "log"
    }
}
