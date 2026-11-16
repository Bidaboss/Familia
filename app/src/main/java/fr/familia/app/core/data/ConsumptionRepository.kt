package fr.familia.app.core.data

import fr.familia.app.core.model.Consumption
import kotlinx.coroutines.flow.Flow

/** consumption/{day} (PRD, récit 1 sexies): written by the server, read by the admin principal. */
interface ConsumptionRepository {
    /** The latest day read, kept up to date; null before the first morning. */
    fun observeLatest(): Flow<Consumption?>
}
