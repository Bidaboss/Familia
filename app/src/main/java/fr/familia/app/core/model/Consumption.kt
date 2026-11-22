package fr.familia.app.core.model

import java.time.LocalDate

/**
 * The use of the database on one day, read each morning from Cloud Monitoring by the
 * 30-minute task of the functions (PRD, récit 1 sexies): consumption/{day}, for the admin principal only.
 * [failed]: Monitoring could not be read, most likely because the « Lecteur Monitoring » role is
 * missing (docs/deploiement.md).
 */
data class Consumption(
    val day: LocalDate,
    val reads: Long = 0,
    val writes: Long = 0,
    val deletes: Long = 0,
    val storageBytes: Long? = null,
    /** The photos kept in Cloud Storage; null when Monitoring gave no figure that day. */
    val photoBytes: Long? = null,
    /** What the phones downloaded from Cloud Storage since the 1st of the month. */
    val monthDownloadBytes: Long? = null,
    val monthCost: Double = 0.0,
    /** What each limit costs since the 1st of the month, in euros; empty for a day read before the detail. */
    val monthCosts: Map<Limit, Double> = emptyMap(),
    val failed: Boolean = false,
    /** What Google answered when [failed] (status and message), to tell a missing role from an API switched off. */
    val errorDetail: String? = null,
) {
    /** One line of the card: how much of a free limit [used] takes, and what it costs this month ([cost]). */
    data class Gauge(val limit: Limit, val used: Long, val cost: Double? = null) {
        val share: Float get() = (used.toDouble() / limit.free).toFloat().coerceIn(0f, 1f)

        /** From 80 %, the function warns the admin principal (same threshold as consumption.ts). */
        val near: Boolean get() = used >= limit.free * ALERT_SHARE
    }

    /** The free quotas of Firestore and Cloud Storage, as in FREE of firebase/functions/src/consumption.ts. */
    enum class Limit(val free: Long) {
        // The key of each one in consumption/{day}.monthCosts is its name in lower case.
        Reads(50_000),
        Writes(20_000),
        Deletes(20_000),
        Storage(1024L * 1024 * 1024),
        Photos(5_000_000_000),
        Downloads(100_000_000_000),
    }

    /** The limits, the sizes and the downloads only when Monitoring gave them. */
    val gauges: List<Gauge>
        get() = listOfNotNull(
            Gauge(Limit.Reads, reads, monthCosts[Limit.Reads]),
            Gauge(Limit.Writes, writes, monthCosts[Limit.Writes]),
            Gauge(Limit.Deletes, deletes, monthCosts[Limit.Deletes]),
            storageBytes?.let { Gauge(Limit.Storage, it, monthCosts[Limit.Storage]) },
            photoBytes?.let { Gauge(Limit.Photos, it, monthCosts[Limit.Photos]) },
            monthDownloadBytes?.let { Gauge(Limit.Downloads, it, monthCosts[Limit.Downloads]) },
        )

    /** Past 0,50 € in the month, the function warns too (COST_ALERT_EUROS). */
    val costNear: Boolean get() = monthCost > COST_ALERT_EUROS

    companion object {
        const val ALERT_SHARE = 0.8
        const val COST_ALERT_EUROS = 0.5
    }
}
