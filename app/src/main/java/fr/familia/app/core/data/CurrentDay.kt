package fr.familia.app.core.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The family lives in France, and the server counts its days in Paris (parisDay): « today »,
 * midnight and the Sunday at 18 h are Paris ones, even on a phone set to another time zone, so
 * the Accueil and the notices never disagree on the day.
 */
val FAMILY_ZONE: ZoneId = ZoneId.of("Europe/Paris")

/** Today in Paris at [now]. */
fun familyToday(now: Instant = Instant.now()): LocalDate = now.atZone(FAMILY_ZONE).toLocalDate()

/**
 * Today's date, emitted again when the day changes, so "Aujourd'hui" and the lists move on at
 * midnight even when the app stays open. It checks once a minute rather than sleeping until
 * midnight: a long wait would be delayed by the phone's deep sleep, and a date comparison a
 * minute costs nothing. Screens stop collecting 5 seconds after they leave the screen.
 */
fun currentDayFlow(now: () -> LocalDate = { familyToday() }, checkEveryMillis: Long = 60_000): Flow<LocalDate> =
    flow {
        while (true) {
            emit(now())
            delay(checkEveryMillis)
        }
    }.distinctUntilChanged()
