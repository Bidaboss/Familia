package fr.familia.app.core.data

import fr.familia.app.core.model.ChallengeRotation
import fr.familia.app.core.model.FamilyTree
import fr.familia.app.core.model.HostedChallenge
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

    /** The order of the foyers (challengeRotation/current, récit 35 bis), or null when none is set. */
    fun observeRotation(): Flow<ChallengeRotation?> = flowOf(null)

    /** Saves the order; the admin principal only. */
    suspend fun setRotation(rotation: ChallengeRotation) = Unit

    /** The challenge a foyer prepares for [month] (householdChallenges), or null. */
    fun observeHosted(month: YearMonth): Flow<HostedChallenge?> = flowOf(null)

    /** Saves [challenge] as it stands: draft, published or skipped. The rules check who may. */
    suspend fun saveHosted(challenge: HostedChallenge, memberId: String) = Unit
}
