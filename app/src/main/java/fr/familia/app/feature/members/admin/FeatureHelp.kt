package fr.familia.app.feature.members.admin

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import fr.familia.app.R
import fr.familia.app.core.model.Feature

/**
 * One sentence under each switch of Administration, Modules (PRODUIT, 17/11): what the module is for
 * and where it shows, in at most 140 characters. A module added without one shows no sentence.
 */
private val HELP: Map<Feature, Int> = mapOf(
    Feature.Home to R.string.feature_help_home,
    Feature.Gifts to R.string.feature_help_gifts,
    Feature.GiftNotebook to R.string.feature_help_gift_notebook,
    Feature.ShareToWishes to R.string.feature_help_share_to_wishes,
    Feature.WishFromShare to R.string.feature_help_wish_from_share,
    Feature.WishesReveal to R.string.feature_help_wishes_reveal,
    Feature.GiftBudget to R.string.feature_help_gift_budget,
    Feature.HelpRequests to R.string.feature_help_help_requests,
    Feature.Trips to R.string.feature_help_trips,
    Feature.GoodNews to R.string.feature_help_good_news,
    Feature.Themes to R.string.feature_help_themes,
    Feature.Usage to R.string.feature_help_usage,
    Feature.WeeklyQuestion to R.string.feature_help_weekly_question,
    Feature.EventAlbums to R.string.feature_help_event_albums,
    Feature.Calendar to R.string.feature_help_calendar,
    Feature.Souvenirs to R.string.feature_help_souvenirs,
    Feature.Tree to R.string.feature_help_tree,
    Feature.Digest to R.string.feature_help_digest,
    Feature.MonthSlideshow to R.string.feature_help_month_slideshow,
    Feature.Polls to R.string.feature_help_polls,
    Feature.Recipes to R.string.feature_help_recipes,
    Feature.Capsules to R.string.feature_help_capsules,
    Feature.Genealogy to R.string.feature_help_genealogy,
    Feature.Bring to R.string.feature_help_bring,
    Feature.Places to R.string.feature_help_places,
    Feature.Share to R.string.feature_help_share,
    Feature.Comments to R.string.feature_help_comments,
    Feature.Expenses to R.string.feature_help_expenses,
    Feature.Recap to R.string.feature_help_recap,
    Feature.YearAgo to R.string.feature_help_year_ago,
    Feature.BirthdayWishes to R.string.feature_help_birthday_wishes,
    Feature.Welcome to R.string.feature_help_welcome,
    Feature.MemberCard to R.string.feature_help_member_card,
    Feature.LittleOnes to R.string.feature_help_little_ones,
    Feature.Discover to R.string.feature_help_discover,
    Feature.DailyNudge to R.string.feature_help_daily_nudge,
    Feature.ThinkOfMe to R.string.feature_help_think_of_me,
    Feature.WhoIsIt to R.string.feature_help_who_is_it,
    Feature.SingleList to R.string.feature_help_single_list,
    Feature.OldListsCleanup to R.string.feature_help_old_lists_cleanup,
    Feature.SurpriseButton to R.string.feature_help_surprise_button,
    Feature.MemberProfileV2 to R.string.feature_help_member_profile_v2,
    Feature.MorePhotos to R.string.feature_help_more_photos,
    Feature.RecipeBook to R.string.feature_help_recipe_book,
    Feature.PresentationR1bis to R.string.feature_help_presentation_r1bis,
    Feature.GoodDeals to R.string.feature_help_good_deals,
    Feature.CalendarFeed to R.string.feature_help_calendar_feed,
    Feature.Lending to R.string.feature_help_lending,
    Feature.Carpool to R.string.feature_help_carpool,
    Feature.RecurringEvents to R.string.feature_help_recurring_events,
    Feature.TreePoster to R.string.feature_help_tree_poster,
    Feature.ArbreDeVie to R.string.feature_help_arbre_de_vie,
    Feature.PhotoBook to R.string.feature_help_photo_book,
    Feature.YearInReview to R.string.feature_help_year_in_review,
    Feature.MyReminders to R.string.feature_help_my_reminders,
    Feature.Turns to R.string.feature_help_turns,
    Feature.PackingList to R.string.feature_help_packing_list,
    Feature.FirstSteps to R.string.feature_help_first_steps,
    Feature.OpeningAnimation to R.string.feature_help_opening_animation,
    Feature.FamilyTabV2 to R.string.feature_help_family_tab_v2,
    Feature.ChallengeDetails to R.string.feature_help_challenge_details,
    Feature.AlreadyBought to R.string.feature_help_already_bought,
    Feature.AdminAnnouncements to R.string.feature_help_admin_announcements,
)

@StringRes
fun featureHelpRes(feature: Feature): Int? = HELP[feature]

/**
 * The modules with rules or server functions of their own: they need « Publier Firebase » once since
 * they arrived before being switched on.
 */
val NEEDS_FIREBASE_PUBLISH: Set<Feature> = setOf(
    // The phone of a member and the address of a household (récit 66).
    Feature.MemberProfileV2,
    Feature.Gifts,
    Feature.GiftNotebook,
    Feature.WishesReveal,
    Feature.GiftBudget,
    Feature.HelpRequests,
    Feature.Trips,
    Feature.GoodNews,
    Feature.Usage,
    Feature.WeeklyQuestion,
    Feature.EventAlbums,
    Feature.Souvenirs,
    Feature.Tree,
    Feature.ChallengeDetails,
    Feature.Digest,
    Feature.Polls,
    Feature.Recipes,
    Feature.Capsules,
    Feature.Genealogy,
    Feature.Bring,
    Feature.Places,
    Feature.Comments,
    Feature.Expenses,
    Feature.Recap,
    Feature.BirthdayWishes,
    Feature.Welcome,
    Feature.MemberCard,
    Feature.LittleOnes,
    Feature.ThinkOfMe,
    Feature.WhoIsIt,
    Feature.SingleList,
    Feature.OldListsCleanup,
    Feature.SurpriseButton,
    Feature.MemberProfileV2,
    Feature.MorePhotos,
    Feature.RecipeBook,
    Feature.GoodDeals,
    Feature.CalendarFeed,
    Feature.Lending,
    Feature.Carpool,
    Feature.RecurringEvents,
    Feature.MyReminders,
    Feature.Turns,
    Feature.PackingList,
    // The parts of a wish already bought (récit 68).
    Feature.AlreadyBought,
    Feature.AdminAnnouncements,
)

/** « Voir où » under a switch: opens the screen where the module shows, or null when there is none. */
val LocalFeatureSeeWhere = staticCompositionLocalOf<(Feature) -> (() -> Unit)?> { { null } }
