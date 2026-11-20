package fr.familia.app.core.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.familia.app.core.model.EventSeries
import fr.familia.app.core.model.MonthlyBy
import fr.familia.app.core.model.RepeatFreq
import fr.familia.app.core.model.SeriesRule
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime

/**
 * « Événements qui reviennent » (PRD, récit 54): eventSeries/{seriesId}. The app writes only the
 * series; the server writes, rewrites and takes out its occurrences. See docs/data-model.md.
 */
interface EventSeriesRepository {
    suspend fun getSeries(id: String): EventSeries?

    fun newSeriesId(): String

    /** Creates the series, or updates it when [isNew] is false (author and creation time never change). */
    suspend fun saveSeries(series: EventSeries, isNew: Boolean)

    /** Takes the series out: the server takes its occurrences to come with it. */
    suspend fun deleteSeries(id: String)
}

class FirestoreEventSeriesRepository(private val db: FirebaseFirestore) : EventSeriesRepository {

    private fun doc(id: String) = db.collection(SERIES).document(id)

    override suspend fun getSeries(id: String): EventSeries? =
        doc(id).get().await().takeIf { it.exists() }?.toSeries()

    override fun newSeriesId(): String = db.collection(SERIES).document().id

    override suspend fun saveSeries(series: EventSeries, isNew: Boolean) {
        val rule = series.rule
        val fields = mapOf(
            "title" to series.title,
            "time" to series.time?.let { LocalTime.of(it.hour, it.minute).toString() },
            "place" to series.place,
            "note" to series.note,
            "targetType" to series.target.type(),
            "targetMemberId" to series.target.targetMemberId(),
            "householdId" to series.target.targetHouseholdId(),
            "rule" to mapOf(
                "freq" to rule.freq.key(),
                "interval" to rule.interval.toLong(),
                "monthlyBy" to rule.monthlyBy?.let { if (it == MonthlyBy.Weekday) "weekday" else "date" },
                "start" to rule.start.toString(),
                "until" to rule.until?.toString(),
                "count" to rule.count?.toLong(),
            ),
            "exdates" to series.exdates.map { it.toString() },
            "applyFrom" to series.applyFrom?.toString(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        val place = placeFields(series.placeKind, series.placeHouseholdId)
        if (isNew) {
            doc(series.id).set(
                fields + place.filterValues { it != null } +
                    series.mentionIds.takeIf { it.isNotEmpty() }?.let { mapOf("mentionIds" to it) }.orEmpty() + mapOf(
                    "authorId" to series.authorId,
                    "authorName" to series.authorName,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } else {
            val mentions = "mentionIds" to (series.mentionIds.takeIf { it.isNotEmpty() } ?: FieldValue.delete())
            doc(series.id).update(fields + place.mapValues { (_, v) -> v ?: FieldValue.delete() } + mentions).await()
        }
    }

    override suspend fun deleteSeries(id: String) {
        doc(id).delete().await()
    }

    private fun DocumentSnapshot.toSeries(): EventSeries? {
        val target = readTarget() ?: return null
        @Suppress("UNCHECKED_CAST")
        val r = get("rule") as? Map<String, Any?> ?: return null
        val freq = when (r["freq"]) {
            "weekly" -> RepeatFreq.Weekly
            "monthly" -> RepeatFreq.Monthly
            "yearly" -> RepeatFreq.Yearly
            else -> null
        } ?: return null
        val start = (r["start"] as? String)?.toDay() ?: return null
        val rule = SeriesRule(
            freq = freq,
            interval = (r["interval"] as? Number)?.toInt() ?: 1,
            monthlyBy = when (r["monthlyBy"]) {
                "weekday" -> MonthlyBy.Weekday
                "date" -> MonthlyBy.Date
                else -> null
            },
            start = start,
            until = (r["until"] as? String)?.toDay(),
            count = (r["count"] as? Number)?.toInt(),
        )
        return EventSeries(
            id = id,
            title = getString("title").orEmpty(),
            time = getString("time")?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
            place = getString("place"),
            note = getString("note"),
            target = target,
            authorId = getString("authorId").orEmpty(),
            authorName = getString("authorName").orEmpty(),
            rule = rule,
            exdates = (get("exdates") as? List<*>).orEmpty().mapNotNull { (it as? String)?.toDay() },
            applyFrom = getString("applyFrom")?.toDay(),
            placeKind = readPlaceKind(),
            placeHouseholdId = readPlaceHouseholdId(),
            mentionIds = (get("mentionIds") as? List<*>).orEmpty().filterIsInstance<String>(),
        )
    }

    private fun String.toDay(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

    private fun RepeatFreq.key() = when (this) {
        RepeatFreq.Weekly -> "weekly"
        RepeatFreq.Monthly -> "monthly"
        RepeatFreq.Yearly -> "yearly"
    }

    private companion object {
        const val SERIES = "eventSeries"
    }
}
