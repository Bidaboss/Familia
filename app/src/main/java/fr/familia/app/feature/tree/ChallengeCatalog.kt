package fr.familia.app.feature.tree

import fr.familia.app.core.model.MonthChallenge
import java.time.YearMonth

/** A challenge of the list the admin principal chooses from (récit 35). */
data class ChallengeChoice(val id: String, val title: String, val emoji: String)

/**
 * The same list as the server (functions/src/tree.ts, CATALOG) and the rules (challengePicks): add
 * an entry on the three sides. Only the server counts: here it is the list shown in Administration.
 */
object ChallengeCatalog {
    val choices = listOf(
        ChallengeChoice("goodNewsStart", "10 bonnes nouvelles pour bien commencer", "✨"),
        ChallengeChoice("winterPhotos", "15 photos de l'hiver", "❄️"),
        ChallengeChoice("springSigns", "10 premiers signes du printemps", "🌱"),
        ChallengeChoice("springNews", "12 bonnes nouvelles du printemps", "🌷"),
        ChallengeChoice("outdoorPhotos", "15 photos en plein air", "🌳"),
        ChallengeChoice("summerNews", "12 bonnes nouvelles avant l'été", "☀️"),
        ChallengeChoice("holidayPhotos", "20 photos de vacances", "🏖️"),
        ChallengeChoice("postcards", "20 cartes postales en photo", "📸"),
        ChallengeChoice("backToSchool", "10 bonnes nouvelles de la rentrée", "🎒"),
        ChallengeChoice("autumnColours", "15 couleurs d'automne", "🍂"),
        ChallengeChoice("littleJoys", "12 petits bonheurs", "🧡"),
        ChallengeChoice("christmasPhotos", "20 photos de Noël", "🎄"),
        ChallengeChoice("kitchen", "10 photos de nos plats", "🍲"),
        ChallengeChoice("pets", "10 photos de nos animaux", "🐾"),
        ChallengeChoice("proudOf", "10 fiertés de la famille", "🏅"),
        ChallengeChoice("together", "15 photos tous ensemble", "🤗"),
    )

    /** The challenge of each month when nobody picked another, January first. */
    private val defaults = choices.take(12)

    fun of(id: String?): ChallengeChoice? = choices.firstOrNull { it.id == id }

    fun defaultFor(month: YearMonth): ChallengeChoice = defaults[month.monthValue - 1]
}

/**
 * A month in the « Défi du mois » card of Administration: the challenge it will have, and whether
 * it can still change. Once the gauge has moved (or the challenge is achieved), it stays: no photo
 * counts for nothing, the server refuses it too.
 */
data class MonthPick(val month: YearMonth, val choice: ChallengeChoice, val locked: Boolean) {
    companion object {
        fun of(month: YearMonth, challenge: MonthChallenge?, pickedId: String?): MonthPick {
            val locked = challenge != null && (challenge.progress > 0 || challenge.achieved)
            val shown = when {
                locked -> challenge?.choiceId
                pickedId != null && ChallengeCatalog.of(pickedId) != null -> pickedId
                else -> challenge?.choiceId
            }
            val choice = ChallengeCatalog.of(shown)
                ?: challenge?.let { ChallengeChoice(it.choiceId.orEmpty(), it.title, it.emoji) }
                ?: ChallengeCatalog.defaultFor(month)
            return MonthPick(month, choice, locked)
        }
    }
}
