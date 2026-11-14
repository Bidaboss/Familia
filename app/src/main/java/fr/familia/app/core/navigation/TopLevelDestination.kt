package fr.familia.app.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.ui.graphics.vector.ImageVector
import fr.familia.app.R
import kotlinx.serialization.Serializable

/** Type-safe routes of the three tabs. */
@Serializable
data object PlanningRoute

@Serializable
data object BirthdaysRoute

@Serializable
data object MembersRoute

/** Read-only view of an event with "qui vient", opened from the Planning tab. */
@Serializable
data class EventDetailDestination(val eventId: String)

/** Event form opened from the Planning tab: a new event when [eventId] is null. */
@Serializable
data class EventEditDestination(val eventId: String? = null)

/** Admin form opened from the Famille tab: a new member when [memberId] is null. */
@Serializable
data class MemberEditDestination(val memberId: String? = null)

/** Notification settings, opened from the Famille tab (lot 8). */
@Serializable
data object NotificationSettingsDestination

/** Admin backup of the family's data, opened from the Famille tab (lot 9). */
@Serializable
data object ExportDestination

/** Help page, opened from the Famille tab or from a « ? » (lot 11b). */
@Serializable
data object HelpDestination

/** "Vérifier les mises à jour", opened from the Famille tab (lot 10). */
@Serializable
data object UpdateCheckDestination

/**
 * The tabs of the bottom navigation bar, in display order (design principles, "Navigation et structure").
 * Settings and app updates live inside the Famille tab, so V1 keeps three tabs.
 */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Planning(PlanningRoute, R.string.nav_planning, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    Birthdays(BirthdaysRoute, R.string.nav_birthdays, Icons.Filled.Cake, Icons.Outlined.Cake),
    Members(MembersRoute, R.string.nav_members, Icons.Filled.Groups, Icons.Outlined.Groups),
    ;

    companion object {
        /** The app always opens on the planning. */
        val start: TopLevelDestination = Planning
    }
}
