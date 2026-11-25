package fr.familia.app.feature.gifts.surprise

import fr.familia.app.core.data.GiftRepository
import fr.familia.app.core.data.SurpriseRepository
import fr.familia.app.core.model.GiftItem
import fr.familia.app.core.model.SurpriseToRate
import fr.familia.app.core.model.isToConfirm
import fr.familia.app.core.model.toRateCount
import fr.familia.app.core.model.wishesListId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/**
 * Accueil « À répondre »: the surprises in preparation where [memberId] still has proposals to note,
 * the soonest first. Not the ones from the day itself (« C'est le jour J » says it, `SurprisesToConfirm`),
 * nor the ones they said « Je ne participe pas » to. Empty while nothing is to note.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun observeSurprisesToRate(
    surprises: SurpriseRepository,
    gifts: GiftRepository?,
    memberId: String,
    today: LocalDate,
): Flow<List<SurpriseToRate>> = surprises.observeMine(memberId).flatMapLatest { mine ->
    val open = mine.filter { !it.isToConfirm(today) }.sortedBy { it.date }
    if (open.isEmpty()) return@flatMapLatest flowOf(emptyList())
    combine(
        open.map { surprise ->
            val wishes: Flow<List<GiftItem>> = gifts?.observeItems(wishesListId(surprise.beneficiaryId), hiddenFromMe = false)
                ?.catch { emit(emptyList()) } ?: flowOf(emptyList())
            combine(
                wishes,
                surprises.observeProposals(surprise.id).catch { emit(emptyList()) },
                surprises.observeMyRatings(surprise.id, memberId).catch { emit(emptyMap()) },
                surprises.observeMemberRows(surprise.id).catch { emit(emptyList()) },
            ) { items, proposals, ratings, rows ->
                val declined = rows.any { it.memberId == memberId && it.declined }
                val count = if (declined) 0 else toRateCount(items.map { it.id }, proposals, ratings, memberId)
                SurpriseToRate(surprise.id, surprise.beneficiaryId, count)
            }
        },
    ) { rows -> rows.filter { it.count > 0 } }
}
