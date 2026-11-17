package fr.familia.app

import android.content.Intent
import fr.familia.app.core.notifications.FamiliaMessagingService
import fr.familia.app.core.share.ShareKind
import fr.familia.app.core.share.parseSharedLink

/** What a tapped notification or a shared link opens (audit technique): one door, the same for both. */
sealed interface DeepLink {
    data class Event(val id: String) : DeepLink
    data class GiftList(val id: String) : DeepLink
    data class HelpRequest(val id: String) : DeepLink
    data class GoodNews(val id: String) : DeepLink
    data class Trip(val id: String) : DeepLink
    data class WeeklyQuestion(val id: String) : DeepLink
    data object Souvenir : DeepLink
    data class Poll(val id: String) : DeepLink
    data object JoinMe : DeepLink
    data class Capsule(val id: String) : DeepLink
    /** The Sunday of the recap (AAAA-MM-JJ). */
    data class Recap(val week: String) : DeepLink
    /** A member's page: a birthday, a welcome or « Pensez à moi ». */
    data class Member(val id: String) : DeepLink
    /** « list » (Retours), « mine » (Mes demandes) or « crashes » (Plantages). */
    data class Feedback(val key: String) : DeepLink
}

/**
 * Every door of an intent, in a fixed order: first the extras of a notification (same keys as
 * FamiliaMessagingService and functions/src/index.ts), then a shared link (ACTION_VIEW, récit 31).
 * Pure, so each kind is tested without Android. The security rules still decide what shows.
 */
fun deepLinks(extra: (String) -> String?, action: String?, scheme: String?, host: String?, path: String?): List<DeepLink> = buildList {
    extra(FamiliaMessagingService.EXTRA_EVENT_ID)?.let { add(DeepLink.Event(it)) }
    extra(FamiliaMessagingService.EXTRA_LIST_ID)?.let { add(DeepLink.GiftList(it)) }
    extra(FamiliaMessagingService.EXTRA_HELP_REQUEST_ID)?.let { add(DeepLink.HelpRequest(it)) }
    extra(FamiliaMessagingService.EXTRA_GOOD_NEWS_ID)?.let { add(DeepLink.GoodNews(it)) }
    extra(FamiliaMessagingService.EXTRA_TRIP_ID)?.let { add(DeepLink.Trip(it)) }
    extra(FamiliaMessagingService.EXTRA_WEEKLY_QUESTION_ID)?.let { add(DeepLink.WeeklyQuestion(it)) }
    if (extra(FamiliaMessagingService.EXTRA_SOUVENIR_KEY) != null) add(DeepLink.Souvenir)
    extra(FamiliaMessagingService.EXTRA_POLL_ID)?.let { add(DeepLink.Poll(it)) }
    if (extra(FamiliaMessagingService.EXTRA_JOIN_ME_ID) != null) add(DeepLink.JoinMe)
    extra(FamiliaMessagingService.EXTRA_CAPSULE_ID)?.let { add(DeepLink.Capsule(it)) }
    extra(FamiliaMessagingService.EXTRA_RECAP_WEEK)?.let { add(DeepLink.Recap(it)) }
    (extra(FamiliaMessagingService.EXTRA_BIRTHDAY_OF) ?: extra(FamiliaMessagingService.EXTRA_WELCOME_OF) ?: extra(FamiliaMessagingService.EXTRA_THINK_OF_ME_OF))?.let { add(DeepLink.Member(it)) }
    extra(FamiliaMessagingService.EXTRA_FEEDBACK)?.let { add(DeepLink.Feedback(it)) }
    if (action == Intent.ACTION_VIEW) {
        parseSharedLink(scheme, host, path)?.let { link ->
            add(
                when (link.kind) {
                    ShareKind.Event -> DeepLink.Event(link.id)
                    ShareKind.Poll -> DeepLink.Poll(link.id)
                    ShareKind.Birthday -> DeepLink.Member(link.id)
                    ShareKind.List -> DeepLink.GiftList(link.id)
                    ShareKind.GoodNews -> DeepLink.GoodNews(link.id)
                    ShareKind.Trip -> DeepLink.Trip(link.id)
                    ShareKind.HelpRequest -> DeepLink.HelpRequest(link.id)
                },
            )
        }
    }
}

fun Intent.deepLinks(): List<DeepLink> = deepLinks({ getStringExtra(it) }, action, data?.scheme, data?.host, data?.path)
