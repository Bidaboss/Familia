package fr.familia.app.core.data

import fr.familia.app.core.model.PackingBag
import fr.familia.app.core.model.PackingLine
import kotlinx.coroutines.flow.Flow

/**
 * « Ma valise » (PRD, récit 62): trips/{tripId}/bags/{memberId} and the list each one starts from,
 * members/{memberId}/settings/packing. See docs/data-model.md.
 */
interface PackingRepository {
    /** The bag of [memberId] for [tripId]; null while it does not exist yet. */
    fun observeBag(tripId: String, memberId: String): Flow<PackingBag?>

    /** Writes the whole bag, signed by [by] (the member or one of their adults). */
    suspend fun saveBag(tripId: String, memberId: String, lines: List<PackingLine>, by: String)

    /** The list [memberId] starts a bag from; null while they never changed it. */
    fun observeTemplate(memberId: String): Flow<List<String>?>

    suspend fun saveTemplate(memberId: String, labels: List<String>)
}
