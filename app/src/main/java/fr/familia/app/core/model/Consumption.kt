package fr.familia.app.core.model

import java.time.LocalDate

/**
 * The use of the database on one day, read each morning from Cloud Monitoring by the function
 * consumptionCheck (PRD, récit 1 sexies): consumption/{day}, for the admin principal only.
 * [failed]: Monitoring could not be read, most likely because the « Lecteur Monitoring » role is
 * missing (docs/deploiement.md).
 */
data class Consumption(
    val day: LocalDate,
    val reads: Long = 0,
    val writes: Long = 0,
    val deletes: Long = 0,
    val storageBytes: Long? = null,
    val monthCost: Double = 0.0,
    val failed: Boolean = false,
) {
    /** One line of the card: how much of a free limit [used] takes. */
    data class Gauge(val limit: Limit, val used: Long) {
        val share: Float get() = (used.toDouble() / limit.free).toFloat().coerceIn(0f, 1f)

        /** From 80 %, the function warns the admin principal (same threshold as consumption.ts). */
        val near: Boolean get() = used >= limit.free * ALERT_SHARE
    }

    /** The free quotas of Firestore, as in FREE of firebase/functions/src/consumption.ts. */
    enum class Limit(val free: Long) {
        Reads(50_000),
        Writes(20_000),
        Deletes(20_000),
        Storage(1024L * 1024 * 1024),
    }

    /** The four limits, the size only when Monitoring gave it. */
    val gauges: List<Gauge>
        get() = listOfNotNull(
            Gauge(Limit.Reads, reads),
            Gauge(Limit.Writes, writes),
            Gauge(Limit.Deletes, deletes),
            storageBytes?.let { Gauge(Limit.Storage, it) },
        )

    /** Past 0,50 € in the month, the function warns too (COST_ALERT_EUROS). */
    val costNear: Boolean get() = monthCost > COST_ALERT_EUROS

    companion object {
        const val ALERT_SHARE = 0.8
        const val COST_ALERT_EUROS = 0.5
    }
}
