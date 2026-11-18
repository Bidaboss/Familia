package fr.familia.app.core.model

/**
 * How Administration > Modules sorts the switches (BUG-2, Kévin le 18/11) : the modules au choix
 * first, then the new versions to adopt, and the base, always on, folded at the bottom. Only the
 * screen changes: every switch still works and the server reads them as before.
 */
enum class FeatureTier { Choice, NewVersion, Base }

private val BASE = setOf(
    Feature.Home, Feature.Gifts, Feature.HelpRequests, Feature.GoodNews, Feature.Polls, Feature.Bring,
    Feature.Comments, Feature.BirthdayWishes, Feature.Calendar, Feature.Share, Feature.ShareToWishes,
    Feature.WishFromShare, Feature.MemberCard, Feature.Themes, Feature.RecurringEvents, Feature.MyReminders,
    Feature.Welcome, Feature.OpeningAnimation,
)

/** Kept on once tried; after the 21st the old version and its switch go away. */
private val NEW_VERSIONS = setOf(
    Feature.SingleList, Feature.FamilyTabV2, Feature.PresentationR1bis,
    Feature.ChallengeDetails, Feature.MorePhotos, Feature.PlanningV2,
)

val Feature.tier: FeatureTier
    get() = when (this) {
        in BASE -> FeatureTier.Base
        in NEW_VERSIONS -> FeatureTier.NewVersion
        else -> FeatureTier.Choice
    }
