package fr.familia.app.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.ui.graphics.vector.ImageVector
import fr.familia.app.R
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import kotlinx.serialization.Serializable
import fr.familia.app.core.model.FeatureFlags
import fr.familia.app.core.model.Feature

/** Type-safe routes of the tabs. */
@Serializable
data object PlanningRoute

/** The Accueil tab (PRD, récit 29): first in the bar once its module is switched on. */
@Serializable
data object HomeRoute

/** Every birthday, opened from the Cadeaux tab (carte DESIGN-4: no Anniversaires tab any more). */
@Serializable
data object BirthdaysRoute

/** Cadeaux tab: birthdays, « Mes envies » and the gift lists (PRD, V2). */
@Serializable
data object GiftsRoute

@Serializable
data object MembersRoute

/** The « Coups de main » tab, labelled « Entraide » in the bar (DESIGN-4). */
@Serializable
data object HelpRequestsRoute

/** One « coup de main », opened from the Entraide tab. */
@Serializable
data class HelpRequestDetailDestination(val requestId: String)

/** The request form: a new request when [requestId] is null, prefilled from [duplicateOf] for « Dupliquer ». */
@Serializable
data class HelpRequestEditDestination(val requestId: String? = null, val duplicateOf: String? = null)

/** One bon plan, opened from the « Bons plans » of Entraide (récit 45). */
@Serializable
data class GoodDealDetailDestination(val dealId: String)

/** The form of a post: a new one of [kind] (`deal` or `address`) when [dealId] is null. */
@Serializable
data class GoodDealEditDestination(val dealId: String? = null, val kind: String = "deal")

/** All the « Bonnes nouvelles », opened from the home card (récit 32). */
@Serializable
data object GoodNewsFeedDestination

/** One good news, opened from the feed, the home card or a notification. */
@Serializable
data class GoodNewsDetailDestination(val postId: String)

/** Publish a good news, or change one's own when [postId] is set. */
@Serializable
data class GoodNewsEditDestination(val postId: String? = null, val forChallenge: Boolean = false)

/** The question of the week (récit 37): this week's, or an older one when [questionId] is set. */
@Serializable
data class WeeklyQuestionDestination(val questionId: String? = null)

/** The guide of the challenge of a foyer for a month (récit 35 bis), from the card of the tree. */
@Serializable
data class HostChallengeDestination(val year: Int, val month: Int)

/** « Défis » (R1 bis, maquette R7): the challenge of the month, the question of Monday, what was won together. */
@Serializable
data object ChallengesDestination

/** « La semaine de la famille », opened by the notification of Sunday (récit 36). */
@Serializable
data object RecapDestination

/** A member's page, read-only, opened from Famille while the Souvenirs module is on (récit 34). */
@Serializable
data class MemberProfileDestination(val memberId: String)

/** « Nos réponses »: the questions of the week already asked (récit 37). */
@Serializable
data object WeeklyArchiveDestination

/** Read-only view of an event with "qui vient", opened from the Planning tab. */
@Serializable
data class EventDetailDestination(val eventId: String)

/** « Sondages », opened from the Planning bar (récits 2 bis et 2 ter). */
@Serializable
data object PollsDestination

/** One poll, opened from the list or a notification. */
@Serializable
data class PollDetailDestination(val pollId: String)

/** « Nouveau sondage ». */
@Serializable
data object PollEditDestination

/** « Lancer un sondage » from the page of an event (récit 2 quater): its guests are asked. */
@Serializable
data class EventPollEditDestination(val eventId: String)

/** « Rejoignez-moi », opened from its card on the Accueil (récit 33). */
@Serializable
data object JoinMeDestination

/** Event form opened from the Planning tab: a new event when [eventId] is null. */
@Serializable
data class EventEditDestination(val eventId: String? = null)

/** Admin form opened from the Famille tab: a new member when [memberId] is null. */
@Serializable
data class MemberEditDestination(val memberId: String? = null)

/** Ma photo › Apparence: the theme of this phone (DESIGN-7). */
@Serializable
data object AppearanceDestination

/** Notification settings, opened from the Famille tab (lot 8). */
@Serializable
data object NotificationSettingsDestination

/** Admin backup of the family's data, opened from the Famille tab (lot 9). */
@Serializable
data object ExportDestination

/**
 * « Utilisation » (PRD, récit 1 nonies): the whole family from Administration for the admin
 * principal, the children's time from the Famille tab for a responsible adult (carte EXPERT-5).
 */
@Serializable
data object UsageDestination

/** The Administration menu of the admin principal (PRD, récit 1 ter). */
@Serializable
data object AdministrationDestination

/** Help page, opened from the Famille tab or from a « ? » (lot 11b). */
@Serializable
data object HelpDestination

/** One gift list, opened from the Cadeaux tab. */
@Serializable
data class GiftListDestination(val listId: String)

/** Gift list form: a new list when [listId] is null, for [forMemberId] when it is set (a birthday). */
@Serializable
data class GiftListEditDestination(val listId: String? = null, val forMemberId: String? = null)

/** Item form of a gift list: a new item when [itemId] is null, filled with a shared link if any. */
@Serializable
data class GiftItemEditDestination(
    val listId: String,
    val itemId: String? = null,
    val sharedUrl: String? = null,
    val sharedTitle: String? = null,
    /** Récit 50: opened from « Ajouter à mes envies », with « Une idée pour… » and no reading of the page. */
    val fromShare: Boolean = false,
)

/** « Mes cadeaux à offrir », the member's private gift notebook (PRD, récit 9 bis). */
@Serializable
data object NotebookDestination

/** A private idea of the notebook: a new one when [entryId] is null, at the occasion chosen on the notebook. */
@Serializable
data class NotebookIdeaDestination(val entryId: String? = null, val occasion: String? = null, val year: Int? = null)

/** « Proposer à la famille »: a private idea of the notebook becomes a surprise item. */
@Serializable
data class ProposeIdeaDestination(val entryId: String)

/** « On m'a offert » (récit 10 bis): the wishes the family gave to the signed-in member. */
@Serializable
data object ReceivedGiftsDestination

/** "Vérifier les mises à jour", opened from the Famille tab (lot 10). */
@Serializable
data object UpdateCheckDestination

/**
 * The tabs of the bottom navigation bar, in display order (design principles, "Navigation et structure").
 * Settings and app updates live inside the Famille tab, which stays last.
 */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** The module that hides the tab while it is switched off (null: always shown). */
    val feature: Feature? = null,
) {
    Home(HomeRoute, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, Feature.Home),
    Planning(PlanningRoute, R.string.nav_planning, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    Gifts(GiftsRoute, R.string.nav_gifts, Icons.Filled.CardGiftcard, Icons.Outlined.CardGiftcard, Feature.Gifts),
    HelpRequests(HelpRequestsRoute, R.string.nav_help_requests, Icons.Filled.VolunteerActivism, Icons.Outlined.VolunteerActivism, Feature.HelpRequests),
    Members(MembersRoute, R.string.nav_members, Icons.Filled.Groups, Icons.Outlined.Groups),
    ;

    /** A child reads « Mes envies » on the Cadeaux tab (carte DESIGN-4). */
    @StringRes
    fun labelFor(member: Member): Int =
        if (this == Gifts && member.profile != MemberProfile.Adult) R.string.nav_gifts_child else labelRes

    companion object {
        /** The app opens on the Accueil once its module shows, on the planning before. */
        fun startFor(homeOn: Boolean): TopLevelDestination = if (homeOn) Home else Planning

        /** The tabs of the bar: those of the modules switched off stay hidden. */
        fun visible(flags: FeatureFlags): List<TopLevelDestination> = entries.filter { it.feature?.let(flags::isOn) ?: true }
    }
}

/** « Tout Familia », the drawer of every module (R1 bis, §3). */
@Serializable
data object AllFamiliaDestination
