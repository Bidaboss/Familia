package fr.familia.app.core.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import fr.familia.app.core.model.DayUsage
import fr.familia.app.core.model.UsageDay
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.tasks.await

class FirestoreUsageRepository(private val db: FirebaseFirestore) : UsageRepository {

    private fun days(memberId: String) = db.collection("usage").document(memberId).collection("days")

    override suspend fun saveDay(memberId: String, day: LocalDate, usage: DayUsage) {
        days(memberId).document(day.toString()).set(
            mapOf(
                "day" to day.toString(),
                "opens" to usage.opens,
                "seconds" to usage.seconds,
                // Erased by the TTL policy of firestore.indexes.json (PRD: 13 months).
                "expireAt" to Timestamp(day.plusMonths(KEEP_MONTHS).atStartOfDay(ZoneId.systemDefault()).toInstant()),
            ),
        ).await()
    }

    override suspend fun fetchDays(memberId: String, since: LocalDate): List<UsageDay> =
        days(memberId).whereGreaterThanOrEqualTo("day", since.toString()).get().await().documents
            .mapNotNull { toDay(memberId, it) }

    override suspend fun fetchLastDay(memberId: String): LocalDate? =
        days(memberId).orderBy("day", Query.Direction.DESCENDING).limit(1).get().await().documents
            .firstNotNullOfOrNull { toDay(memberId, it)?.day }

    private fun toDay(memberId: String, doc: DocumentSnapshot): UsageDay? {
        val day = doc.getString("day")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val seconds = (doc.get("seconds") as? Map<*, *>).orEmpty()
            .mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.toInt()?.let { key to it } } }
            .toMap()
        return UsageDay(memberId, day, DayUsage((doc.getLong("opens") ?: 0).toInt(), seconds))
    }

    private companion object {
        const val KEEP_MONTHS = 13L
    }
}
