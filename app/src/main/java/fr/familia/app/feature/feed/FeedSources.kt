package fr.familia.app.feature.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Feature
import fr.familia.app.core.model.Member
import fr.familia.app.core.ui.LocalFeatureFlags
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
    return remember(container, me.id, family, today, commentsOn, wishesOn, goodNewsOn, helpOn, weeklyOn, tripsOn, eventAlbumsOn, recipesOn) {
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
            // Recettes ajoutées (fil voyage), with their last comment.
            container?.recipeRepository?.takeIf { recipesOn }?.let { add(RecipeFeedSource(it, container.albumPhotoRepository)) }
        }
    }
}
