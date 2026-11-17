package fr.familia.app.core.model

import java.time.Duration
import java.time.Instant

/**
 * « Mode présentation » (PRD, récit 48), config/presentation: while it is on, the functions hold the
 * arrivals, the reactions, the gifts and the capsules, then send one summary at its end. [until]
 * null: until the admin principal turns it off.
 */
data class PresentationMode(val on: Boolean, val until: Instant?) {
    fun isOn(now: Instant): Boolean = on && (until == null || until.isAfter(now))
}

/** The three choices of Administration. */
enum class PresentationLength(val hours: Long?) {
    TwoHours(2),
    FourHours(4),
    UntilOff(null),
    ;

    /** When a mode started at [now] with this length ends; null: until turned off. */
    fun until(now: Instant): Instant? = hours?.let { now.plus(Duration.ofHours(it)) }
}
