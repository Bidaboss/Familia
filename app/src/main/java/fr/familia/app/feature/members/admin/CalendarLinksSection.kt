package fr.familia.app.feature.members.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.CalendarFeedRepository
import fr.familia.app.core.model.Family
import fr.familia.app.core.model.Member
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClaySpacing
import fr.familia.app.core.ui.pickGroups
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A write that waits for the network still leaves by itself: past this, the screen goes on. */
private const val CUT_TIMEOUT_MILLIS = 4_000L

/** What the last « Couper » gave, for the line under the buttons. */
private sealed interface CutResult {
    data class Done(val firstName: String) : CutResult
    data object Failed : CutResult
}

/**
 * « Liens d'agenda » (audit du 17/11, PRD rév. 267): the admin principal cuts the calendar link of
 * an adult (a lost phone, a link given away). The rules never let them read anyone's token, so the
 * section lists every adult, by household, rather than those who have one: cutting a link that does
 * not exist changes nothing. The adult can make a new one from their own screen.
 */
@Composable
internal fun CalendarLinksSection(family: Family, repository: CalendarFeedRepository, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<CutResult?>(null) }
    val adults = family.members.filter { it.profile == MemberProfile.Adult }
    ClayCard(modifier.fillMaxWidth().padding(horizontal = ClaySpacing.screen, vertical = 8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ClayCardTitle(stringResource(R.string.calendar_links_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.calendar_links_help), style = MaterialTheme.typography.bodyMedium)
            pickGroups(adults, family).forEach { group ->
                Text(group.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                group.members.forEach { adult ->
                    OutlinedButton(
                        onClick = { confirming = adult.id },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.calendar_links_cut, adult.firstName)) }
                }
            }
            result?.let { shown ->
                Text(
                    when (shown) {
                        is CutResult.Done -> stringResource(R.string.calendar_links_cut_done, shown.firstName)
                        CutResult.Failed -> stringResource(R.string.calendar_links_cut_failed)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (shown is CutResult.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
    val asked: Member? = adults.firstOrNull { it.id == confirming }
    if (asked != null) {
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.calendar_links_confirm_title, asked.firstName)) },
            text = { Text(stringResource(R.string.calendar_links_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = null
                        busy = true
                        scope.launch {
                            result = try {
                                withTimeoutOrNull(CUT_TIMEOUT_MILLIS) { repository.remove(asked.id) }
                                CutResult.Done(asked.firstName)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                CutResult.Failed
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.calendar_links_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
