package fr.familia.app.core.crash

import android.content.Context
import android.os.Build
import fr.familia.app.BuildConfig
import fr.familia.app.core.model.CrashReport
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

/**
 * « Plantages » (PRD, récit 6 sexies, rév. 220). An error the app does not catch is written on the
 * phone, then Android closes the app as usual; the report leaves at the next launch
 * ([CrashUploader]). Nothing goes to a third party: no Crashlytics.
 */
object CrashCatcher {

    /** The screen on display, kept up to date by the navigation (FamiliaApp). */
    @Volatile
    var screen: String = ""

    fun install(context: Context) {
        val store = PendingCrashStore(context.applicationContext)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                store.add(pendingCrash(error, screen, System.currentTimeMillis()))
            } catch (_: Throwable) {
                // Never let the report hide the crash itself.
            }
            previous?.uncaughtException(thread, error)
        }
    }
}

/** A crash kept on the phone until it is sent. */
@Serializable
data class PendingCrash(
    val id: String,
    val appVersion: String,
    val versionCode: Long,
    val android: String,
    val device: String,
    val screen: String,
    val trace: String,
    val signature: String,
    val crashedAtMillis: Long,
)

internal fun pendingCrash(error: Throwable, screen: String, nowMillis: Long): PendingCrash = PendingCrash(
    id = UUID.randomUUID().toString().replace("-", ""),
    appVersion = BuildConfig.VERSION_NAME.take(40),
    versionCode = BuildConfig.VERSION_CODE.toLong(),
    android = Build.VERSION.RELEASE.orEmpty().take(20),
    device = "${Build.MANUFACTURER.orEmpty()} ${Build.MODEL.orEmpty()}".trim().take(80),
    screen = screen.take(80),
    trace = crashTrace(error),
    signature = crashSignature(error),
    crashedAtMillis = nowMillis,
)

private const val FRAMES_PER_CAUSE = 30
private const val MAX_CAUSES = 5

/**
 * The trace without any message: the class of each error, its causes, and the lines of code. A
 * message can hold what the member typed (a title, a name), so it never leaves the phone.
 */
fun crashTrace(error: Throwable): String = buildString {
    var current: Throwable? = error
    var depth = 0
    val seen = HashSet<Throwable>()
    while (current != null && depth < MAX_CAUSES && seen.add(current)) {
        if (depth > 0) append("Caused by: ")
        append(current.javaClass.name)
        current.stackTrace.take(FRAMES_PER_CAUSE).forEach { append("\n\tat ").append(it.toString()) }
        if (current.stackTrace.size > FRAMES_PER_CAUSE) append("\n\t… ${current.stackTrace.size - FRAMES_PER_CAUSE} de plus")
        append('\n')
        current = current.cause
        depth++
    }
}.trimEnd().take(CrashReport.TRACE_MAX)

/**
 * 16 hex characters, the same for the same error at the same place: the class of the deepest
 * cause and its first lines in our code (or its first lines when none is ours). Line numbers
 * count, so a fix that moves code may start a new group: that is fine for a family app.
 */
fun crashSignature(error: Throwable): String {
    var root = error
    val seen = HashSet<Throwable>()
    while (root.cause != null && seen.add(root)) root = root.cause!!
    val ours = root.stackTrace.filter { it.className.startsWith("fr.familia.") }
    val frames = (ours.ifEmpty { root.stackTrace.toList() }).take(3)
    val key = root.javaClass.name + frames.joinToString("") { "|${it.className}.${it.methodName}:${it.lineNumber}" }
    val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
    return digest.take(8).joinToString("") { "%02x".format(it) }
}

/** The crashes waiting on this phone, in SharedPreferences: written at once, before Android closes the app. */
class PendingCrashStore(context: Context) {
    private val prefs = context.getSharedPreferences("familia-crashes", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun all(): List<PendingCrash> = prefs.all.values.mapNotNull { value ->
        (value as? String)?.let { runCatching { json.decodeFromString(PendingCrash.serializer(), it) }.getOrNull() }
    }.sortedBy { it.crashedAtMillis }

    /** commit(), not apply(): the process ends right after. Ten at most: a loop keeps the first ones. */
    fun add(crash: PendingCrash) {
        if (prefs.all.size >= MAX_PENDING) return
        prefs.edit().putString(crash.id, json.encodeToString(PendingCrash.serializer(), crash)).commit()
    }

    fun remove(id: String) {
        prefs.edit().remove(id).apply()
    }

    private companion object {
        const val MAX_PENDING = 10
    }
}
