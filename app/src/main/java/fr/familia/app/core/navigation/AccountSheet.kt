package fr.familia.app.core.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.share.canShareApp
import fr.familia.app.core.ui.MemberAvatar
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf

/**
 * What the sheet of the avatar opens (DESIGN-12). A null entry does not show: its module is off,
 * or it is not for this member (Administration for the admin principal only).
 */
class AccountActions(
    val onOpenProfile: (() -> Unit)?,
    val onOpenAppearance: (() -> Unit)?,
    val onOpenNotifications: () -> Unit,
    val onOpenHelp: () -> Unit,
    val onOpenWhatsNew: (() -> Unit)? = null,
    val onOpenUpdates: () -> Unit,
    val onOpenFeedback: (() -> Unit)?,
    /** « Partager Familia », given the first name of the admin principal; null in « Voir comme ». */
    val onShareApp: ((ownerFirstName: String?) -> Unit)?,
    val onOpenChildrenUsage: (() -> Unit)?,
    val onOpenAdministration: (() -> Unit)?,
    /** « Voir dans mon agenda » (récit 49): null while its module is off, or in « Voir comme ». */
    val onOpenCalendarFeed: (() -> Unit)? = null,
)

/** My photo at the right of the bar of the 5 tabs, 40 dp in a 56 dp target (DESIGN-12). */
@Composable
fun AccountButton(member: Member, onClick: () -> Unit) {
    val label = stringResource(R.string.account_open)
    IconButton(onClick = onClick, modifier = Modifier.size(56.dp).semantics { contentDescription = label }) {
        Box(Modifier.clearAndSetSemantics { }) { MemberAvatar(member, size = 40.dp) }
    }
}

/**
 * The sheet of the avatar, in the order of DESIGN-12: Mon profil, Apparence, Notifications, Aide,
 * then the other settings that were under Famille, and Administration last, after a line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(container: AppContainer?, member: Member, actions: AccountActions, onDismiss: () -> Unit) {
    val familyFlow = remember(container) { container?.memberRepository?.observeFamily()?.catch { } ?: flowOf(null) }
    val family by familyFlow.collectAsStateWithLifecycle(initialValue = null)
    val isOwner = family?.ownerId != null && family?.ownerId == member.id
    val hasChildWithApp = family?.members.orEmpty().any { it.profile == MemberProfile.ChildWithApp && member.id in it.parentIds }
    val ownerName = family?.members?.firstOrNull { it.id == family?.ownerId }?.firstName
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            fun go(action: () -> Unit): () -> Unit = {
                onDismiss()
                action()
            }
            actions.onOpenProfile?.let { AccountRow(Icons.Outlined.Person, R.string.account_profile, go(it)) }
            actions.onOpenAppearance?.let { AccountRow(Icons.Outlined.Palette, R.string.appearance_title, go(it)) }
            AccountRow(Icons.Outlined.Notifications, R.string.notifications_title, go(actions.onOpenNotifications))
            // A child has no link (récit 49): the rules refuse it too.
            val calendarFeed = actions.onOpenCalendarFeed
            if (calendarFeed != null && member.profile == MemberProfile.Adult) {
                AccountRow(Icons.Outlined.CalendarMonth, R.string.account_calendar_feed, go(calendarFeed))
            }
            AccountRow(Icons.AutoMirrored.Outlined.HelpOutline, R.string.help_title, go(actions.onOpenHelp))
            actions.onOpenWhatsNew?.let { AccountRow(Icons.Outlined.AutoAwesome, R.string.account_whats_new, go(it)) }
            AccountRow(Icons.Outlined.SystemUpdate, R.string.update_check_title, go(actions.onOpenUpdates))
            actions.onOpenFeedback?.let { AccountRow(Icons.Outlined.Lightbulb, R.string.feedback_title, go(it)) }
            val share = actions.onShareApp
            // Only the admin principal sends the install link (rév. 229).
            if (share != null && canShareApp(member, family?.ownerId)) {
                AccountRow(Icons.Outlined.Share, R.string.share_app_title, go { share(ownerName) })
            }
            // The admin principal finds everyone's time in Administration instead.
            val childrenUsage = actions.onOpenChildrenUsage
            if (childrenUsage != null && !isOwner && hasChildWithApp) {
                AccountRow(Icons.Outlined.QueryStats, R.string.usage_children_title, go(childrenUsage))
            }
            val administration = actions.onOpenAdministration
            if (administration != null && isOwner) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                AccountRow(Icons.Outlined.AdminPanelSettings, R.string.administration_title, go(administration))
            }
        }
    }
}

@Composable
private fun AccountRow(icon: ImageVector, @StringRes labelRes: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(labelRes), style = MaterialTheme.typography.titleMedium)
    }
}
