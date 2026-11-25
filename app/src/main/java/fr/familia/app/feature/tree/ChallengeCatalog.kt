package fr.familia.app.feature.tree

import fr.familia.app.core.model.MonthChallenge
import java.time.YearMonth

/**
 * A challenge of the list the admin principal chooses from (récit 35). [hint] says in one sentence
 * what to share, under the title of the Accueil card (l'admin, 20/11); the app only, not the server.
 */
data class ChallengeChoice(val id: String, val title: String, val emoji: String, val hint: String = "")

/**
 * The same list as the server (functions/src/tree.ts, CATALOG) and the rules (challengePicks): add
 * an entry on the three sides. Only the server counts: here it is the list shown in Administration.
 */
object ChallengeCatalog {
    val choices = listOf(
        ChallengeChoice(
            "goodNewsStart", "10 actualités pour bien commencer", "✨",
            "Publiez des actualités pour lancer l'année ensemble.",
        ),
        ChallengeChoice(
            "winterPhotos", "15 photos de l'hiver", "❄️",
            "Une photo de l'hiver : neige, givre, chocolat chaud…",
        ),
        ChallengeChoice(
            "springSigns", "10 premiers signes du printemps", "🌱",
            "Le premier bourgeon, la première fleur, le premier soleil.",
        ),
        ChallengeChoice(
            "springNews", "12 actualités du printemps", "🌷",
            "Une nouvelle de chez vous, petite ou grande.",
        ),
        ChallengeChoice(
            "outdoorPhotos", "15 photos en plein air", "🌳",
            "Une photo dehors : balade, jardin, parc…",
        ),
        ChallengeChoice(
            "summerNews", "12 actualités avant l'été", "☀️",
            "Ce qui se passe chez vous avant les vacances.",
        ),
        ChallengeChoice(
            "holidayPhotos", "20 photos de vacances", "🏖️",
            "Une photo de vos vacances, où que vous soyez.",
        ),
        ChallengeChoice(
            "postcards", "20 cartes postales en photo", "📸",
            "Une photo comme une carte postale, d'où vous êtes.",
        ),
        ChallengeChoice(
            "backToSchool", "10 actualités de la rentrée", "🎒",
            "La rentrée de chacun : école, travail, nouveautés.",
        ),
        ChallengeChoice(
            "autumnColours", "15 couleurs d'automne", "🍂",
            "Une photo aux couleurs de l'automne.",
        ),
        ChallengeChoice(
            "littleJoys", "12 petits bonheurs", "🧡",
            "Un petit moment qui fait du bien : un café, un fou rire, un coucher de soleil.",
        ),
        ChallengeChoice(
            "christmasPhotos", "20 photos de Noël", "🎄",
            "Le sapin, les décorations, les préparatifs de Noël.",
        ),
        ChallengeChoice(
            "kitchen", "10 photos de nos plats", "🍲",
            "Un plat que vous avez préparé ou partagé.",
        ),
        ChallengeChoice(
            "pets", "10 photos de nos animaux", "🐾",
            "Une photo de vos animaux.",
        ),
        ChallengeChoice(
            "proudOf", "10 fiertés de la famille", "🏅",
            "Une réussite, un effort, un beau moment dont vous êtes fiers.",
        ),
        ChallengeChoice(
            "together", "15 photos tous ensemble", "🤗",
            "Une photo où vous êtes plusieurs.",
        ),
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
