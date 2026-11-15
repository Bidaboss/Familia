package fr.familia.app.feature.help

import androidx.annotation.StringRes
import fr.familia.app.R

/** The « ? » of a screen (spec « tuto et aide », récit 26): a few sentences on what it does. */
enum class ScreenHelp(@StringRes val titleRes: Int, @StringRes val bodyRes: Int) {
    Planning(R.string.help_screen_planning_title, R.string.help_screen_planning_body),
    EventDetail(R.string.help_screen_event_detail_title, R.string.help_screen_event_detail_body),
    EventEdit(R.string.help_screen_event_edit_title, R.string.help_screen_event_edit_body),
    Family(R.string.help_screen_family_title, R.string.help_screen_family_body),
    HelpRequests(R.string.help_screen_help_requests_title, R.string.help_screen_help_requests_body),
}

/** Who sees a topic of the help page: the functions they can use. */
enum class HelpAudience { Everyone, Chefs, Owner }

/** One function of the help page (récit 27). */
enum class HelpTopic(@StringRes val titleRes: Int, @StringRes val bodyRes: Int, val audience: HelpAudience = HelpAudience.Everyone) {
    Planning(R.string.help_topic_planning_title, R.string.help_topic_planning_body),
    AddEvent(R.string.help_topic_add_title, R.string.help_topic_add_body),
    EditEvent(R.string.help_topic_edit_title, R.string.help_topic_edit_body),
    Answers(R.string.help_topic_answers_title, R.string.help_topic_answers_body),
    Notifications(R.string.help_topic_notifications_title, R.string.help_topic_notifications_body),
    HelpRequests(R.string.help_topic_help_requests_title, R.string.help_topic_help_requests_body),
    Family(R.string.help_topic_family_title, R.string.help_topic_family_body),
    Updates(R.string.help_topic_updates_title, R.string.help_topic_updates_body),
    Invite(R.string.help_topic_invite_title, R.string.help_topic_invite_body, HelpAudience.Chefs),
    Export(R.string.help_topic_export_title, R.string.help_topic_export_body, HelpAudience.Owner),
}

/** The topics a member sees: only the functions they can use. The admin principal sees them all. */
fun helpTopics(isChef: Boolean, isOwner: Boolean = false): List<HelpTopic> = HelpTopic.entries.filter {
    when (it.audience) {
        HelpAudience.Everyone -> true
        HelpAudience.Chefs -> isChef || isOwner
        HelpAudience.Owner -> isOwner
    }
}

/** "Kévin", "Kévin ou Inès", "Kévin, Inès ou Sam": who to contact when something goes wrong. */
fun adminNames(names: List<String>): String? = when (names.size) {
    0 -> null
    1 -> names[0]
    else -> names.dropLast(1).joinToString(", ") + " ou " + names.last()
}
