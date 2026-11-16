package fr.familia.app.core.data

import fr.familia.app.core.model.FamilyTree
import fr.familia.app.core.model.MonthChallenge
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/** L'arbre et le défi du mois (récit 35): read only, the server writes them. */
interface TreeRepository {
    fun observeTree(): Flow<FamilyTree>

    /** Null until the server has created the challenge of [month]. */
    fun observeChallenge(month: YearMonth): Flow<MonthChallenge?>
}
