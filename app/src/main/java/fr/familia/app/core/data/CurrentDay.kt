package fr.familia.app.core.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

/**
 * Today's date, emitted again when the day changes, so "Aujourd'hui" and the lists move on at
 * midnight even when the app stays open. It checks once a minute rather than sleeping until
 * midnight: a long wait would be delayed by the phone's deep sleep, and a date comparison a
 * minute costs nothing. Screens stop collecting 5 seconds after they leave the screen.
 */
fun currentDayFlow(now: () -> LocalDate = LocalDate::now, checkEveryMillis: Long = 60_000): Flow<LocalDate> =
    flow {
        while (true) {
            emit(now())
            delay(checkEveryMillis)
        }
    }.distinctUntilChanged()
