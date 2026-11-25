package fr.familia.app.feature.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.LocalFeatureFlags
import fr.familia.app.feature.yearago.rememberYearAgoMemory
import fr.familia.app.feature.yearinreview.openableReviewYear
import java.time.LocalDate

/**
 * Every module of the fil (récit 67), each under its own switch: only a module that shows has its
 * cards. One line per module; the fil coups de main adds the bonnes nouvelles, the coups de main and
 * the question of the week, the fil voyage the photos of the albums and trips and the recipes.
 * Never the Cadeaux, the surprises nor the capsules.
 */
@Composable
fun rememberFeedSources(container: AppContainer?, me: Member, family: Family?, today: LocalDate): List<FeedSource> {
    val flags = LocalFeatureFlags.current
    val commentsOn = flags.isOn(Feature.Comments)
    val wishesOn = flags.isOn(Feature.BirthdayWishes)
    val goodNewsOn = flags.isOn(Feature.GoodNews)
    val helpOn = flags.isOn(Feature.HelpRequests)
    val weeklyOn = flags.isOn(Feature.WeeklyQuestion)
    val tripsOn = flags.isOn(Feature.Trips)
    val eventAlbumsOn = flags.isOn(Feature.EventAlbums)
    val recipesOn = flags.isOn(Feature.Recipes)
    // Envies et bons plans (récit 116): each under the switch of its module.
    val giftsOn = flags.isOn(Feature.Gifts)
    val dealsOn = flags.isOn(Feature.GoodDeals)
    // Se souvenir en un seul lieu (récit 71): each souvenir under the switch of its module.
    val capsulesOn = flags.isOn(Feature.Capsules)
    val recapOn = flags.isOn(Feature.Recap)
    val yearAgo = if (flags.isOn(Feature.YearAgo)) rememberYearAgoMemory(container, me) else null
    val reviewYear = openableReviewYear(today, forCard = true)
    return remember(
        container, me.id, family, today, commentsOn, wishesOn, goodNewsOn, helpOn, weeklyOn, tripsOn, eventAlbumsOn, recipesOn,
        capsulesOn, recapOn, yearAgo, reviewYear, giftsOn, dealsOn,
    ) {
        buildList {
            // Planning (fil principal): the events put in the Planning, with « J'y vais » and their last comment.
            container?.eventRepository?.let { events ->
                add(EventFeedSource(events, container.rsvpRepository, container.eventCommentRepository.takeIf { commentsOn }, me.id))
            }
            // Anniversaires du jour (fil principal).
            if (family != null) add(BirthdayFeedSource(family.members, today, wishesOn))
            // Bonnes nouvelles, with their comments (fil coups de main).
            if (goodNewsOn) container?.goodNewsRepository?.let { add(GoodNewsFeedSource(it, container.goodNewsCommentRepository, me.id)) }
            // Coups de main demandés ou pris, as Entraide shows them to me (fil coups de main).
            if (helpOn) container?.helpRequestRepository?.let { add(HelpFeedSource(it, me.id)) }
            // Question de la semaine (fil coups de main).
            if (weeklyOn) container?.weeklyQuestionRepository?.let { add(WeeklyFeedSource(it, me.id)) }
            // Photos des albums des événements et des voyages (fil voyage): never a surprise trip.
            val events = container?.eventRepository
            val albums = container?.albumPhotoRepository
            if (family != null && events != null && albums != null && (tripsOn || eventAlbumsOn)) {
                add(AlbumFeedSource(container.tripRepository, events, albums, family, me.id, today, tripsOn, eventAlbumsOn))
            }
            // Le mot de l'étape (fil voyage): never a trip hidden from someone.
            if (family != null && tripsOn) add(WordFeedSource(container?.tripRepository, container?.tripWordRepository, family, me.id, today))
            // Film du jour (récit 108, PRD 156): the same trips as the words.
            if (family != null && tripsOn) add(FilmFeedSource(container?.tripRepository, container?.tripFilmRepository, family, me.id, today))
            // Recettes ajoutées (fil voyage), with their last comment.
            container?.recipeRepository?.takeIf { recipesOn }?.let { add(RecipeFeedSource(it, container.albumPhotoRepository)) }
            // Envies (récit 116): one card per person and per day, never the person's own.
            if (giftsOn) container?.giftRepository?.let { add(WishesFeedSource(it, me.id)) }
            // Bons plans et bonnes adresses d'Entraide (récit 116).
            if (dealsOn) container?.goodDealRepository?.let { add(DealFeedSource(it)) }
            // Capsule ouverte, Il y a un an, La semaine de la famille (le dimanche), Notre année (récit 71).
            if (capsulesOn) container?.capsuleRepository?.let { add(CapsuleFeedSource(it, me.id)) }
            yearAgo?.let { add(YearAgoFeedSource(it, today)) }
            if (recapOn && family != null) {
                add(WeekFeedSource(container?.goodNewsRepository.takeIf { goodNewsOn }, container?.tripRepository.takeIf { tripsOn }, family.members, me.id, today))
            }
            add(YearInReviewFeedSource(reviewYear))
        }
    }
}
