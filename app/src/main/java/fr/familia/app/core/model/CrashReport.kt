package fr.familia.app.core.model

import java.time.Instant

/**
 * crashReports/{reportId} (PRD, récit 6 sexies, rév. 220): an error the app did not catch. The
 * trace holds class names and lines only, never the message of the error: no typed text, photo or
 * list ever leaves the phone this way.
 */
data class CrashReport(
    val id: String,
    val memberId: String,
    val appVersion: String,
    val versionCode: Long,
    val android: String,
    val device: String,
    /** The screen shown at the time, by the name of its destination (« Home », « EventDetail »). */
    val screen: String,
    val trace: String,
    /** The same for the same error at the same place: the reports are grouped by it. */
    val signature: String,
    val crashedAt: Instant,
) {
    companion object {
        const val TRACE_MAX = 8000
    }
}

/** One kind of crash: every report of the same [signature], the latest first. */
data class CrashGroup(
    val signature: String,
    val reports: List<CrashReport>,
) {
    val latest: CrashReport get() = reports.first()
    val count: Int get() = reports.size
    val people: Int get() = reports.map { it.memberId }.distinct().size
    val firstAt: Instant get() = reports.last().crashedAt
    val versions: List<String> get() = reports.map { it.appVersion }.distinct()

    /** « IllegalStateException », from the first line of the trace. */
    val errorName: String get() = crashErrorName(latest.trace)

    /** Where it broke in our code: « HomeViewModel.kt:42 », or null when no line is ours. */
    val place: String? get() = crashPlace(latest.trace)
}

/** The groups, the one that crashed last first; each with its reports, the latest first. */
fun crashGroups(reports: List<CrashReport>): List<CrashGroup> =
    reports.groupBy { it.signature }
        .map { (signature, list) -> CrashGroup(signature, list.sortedByDescending { it.crashedAt }) }
        .sortedByDescending { it.latest.crashedAt }

fun crashErrorName(trace: String): String =
    trace.lineSequence().firstOrNull()?.trim()?.split('.', '$')?.lastOrNull()?.takeIf { it.isNotEmpty() } ?: "Erreur"

private val ourFrame = Regex("""at fr\.familia\.[\w.$]+\(([\w]+\.kt:\d+)\)""")

fun crashPlace(trace: String): String? = ourFrame.find(trace)?.groupValues?.get(1)
