package fr.familia.app.core.data

import fr.familia.app.core.model.FamilyTree
import fr.familia.app.core.model.MonthChallenge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.YearMonth

/** L'arbre et le défi du mois (récit 35): the server writes them; the admin principal picks a challenge. */
interface TreeRepository {
    fun observeTree(): Flow<FamilyTree>

    /** Null until the server has created the challenge of [month]. */
    fun observeChallenge(month: YearMonth): Flow<MonthChallenge?>

    /** The challenge the admin principal picked for [month] (challengePicks), or null. Their eyes only. */
    fun observePick(month: YearMonth): Flow<String?> = flowOf(null)

    /** Picks [choiceId] for [month]; the server applies it while nobody has moved the gauge. */
    suspend fun pick(month: YearMonth, choiceId: String) = Unit
}
