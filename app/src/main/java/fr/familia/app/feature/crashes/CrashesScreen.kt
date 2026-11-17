package fr.familia.app.feature.crashes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.CrashGroup
import fr.familia.app.core.model.CrashReport
import fr.familia.app.core.model.crashGroups
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import fr.familia.app.core.ui.LoadingState

/** What the screen shows: the kinds of crash, the one that crashed last first. */
sealed interface CrashesState {
    data object Loading : CrashesState
    data object Error : CrashesState
    data class Ready(val groups: List<CrashGroup>) : CrashesState
}

private val crashTimeFormat = DateTimeFormatter.ofPattern("d MMM 'à' HH'h'mm", Locale.FRANCE)

/**
 * « Plantages » (PRD, récit 6 sexies, rév. 220), for the admin principal: each kind of crash with
 * how many times and how many people, « Copier pour Claude » and « Marquer comme réglé ».
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CrashesScreen(
    state: CrashesState,
    onBack: () -> Unit,
    onSettle: (String) -> Unit,
    /** « Faire un essai »: closes the app on purpose, to see a report arrive at the next launch. */
    onTestCrash: (() -> Unit)? = null,
    actionFailed: Boolean = false,
    onFailureShown: () -> Unit = {},
) {
    var settling by rememberSaveable { mutableStateOf<String?>(null) }
    var testing by rememberSaveable { mutableStateOf(false) }
    var copied by rememberSaveable { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crashes_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                CrashesState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                CrashesState.Error -> EmptyState(
                    icon = Icons.Outlined.BugReport,
                    title = stringResource(R.string.crashes_error_title),
                    body = stringResource(R.string.crashes_error_body),
                )
                is CrashesState.Ready -> if (state.groups.isEmpty()) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) {
                            EmptyState(
                                icon = Icons.Outlined.BugReport,
                                title = stringResource(R.string.crashes_empty_title),
                                body = stringResource(R.string.crashes_empty_body),
                            )
                        }
                        if (onTestCrash != null) TestCrashButton(onClick = { testing = true })
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.groups, key = { it.signature }) { group ->
                            ClayCard(modifier = Modifier.fillMaxWidth(), tint = ClayTint.Family) {
                                Text(group.errorName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                                group.place?.let { Text(stringResource(R.string.crashes_place, it), style = MaterialTheme.typography.bodyLarge) }
                                Text(
                                    stringResource(
                                        R.string.crashes_counts,
                                        pluralStringResource(R.plurals.crashes_times, group.count, group.count),
                                        pluralStringResource(R.plurals.crashes_people, group.people, group.people),
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    crashLine(group),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FamiliaClay.colors.onTintVariant(ClayTint.Family),
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(crashForClaude(group)))
                                            copied = group.signature
                                        },
                                        modifier = Modifier.heightIn(min = 56.dp),
                                    ) {
                                        Text(stringResource(if (copied == group.signature) R.string.feedback_copied else R.string.feedback_copy_for_claude))
                                    }
                                    TextButton(onClick = { settling = group.signature }, modifier = Modifier.heightIn(min = 56.dp)) {
                                        Text(stringResource(R.string.crashes_settle))
                                    }
                                }
                            }
                        }
                        if (onTestCrash != null) item(key = "test") { TestCrashButton(onClick = { testing = true }) }
                    }
                }
            }
        }
    }
    if (testing && onTestCrash != null) {
        AlertDialog(
            onDismissRequest = { testing = false },
            title = { Text(stringResource(R.string.crashes_test_title)) },
            text = { Text(stringResource(R.string.crashes_test_body)) },
            confirmButton = {
                TextButton(onClick = onTestCrash, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.crashes_test_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { testing = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    val groups = (state as? CrashesState.Ready)?.groups.orEmpty()
    groups.firstOrNull { it.signature == settling }?.let { target ->
        AlertDialog(
            onDismissRequest = { settling = null },
            title = { Text(stringResource(R.string.crashes_settle_title)) },
            text = { Text(stringResource(R.string.crashes_settle_body, target.errorName)) },
            confirmButton = {
                TextButton(onClick = { settling = null; onSettle(target.signature) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.crashes_settle))
                }
            },
            dismissButton = {
                TextButton(onClick = { settling = null }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_keep)) }
            },
        )
    }
    if (actionFailed) {
        AlertDialog(
            onDismissRequest = onFailureShown,
            text = { Text(stringResource(R.string.crashes_action_failed)) },
            confirmButton = {
                TextButton(onClick = onFailureShown, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

@Composable
private fun TestCrashButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(R.string.crashes_test))
    }
}

/** « Dernière fois le 21 nov. à 14h05 · Familia 1.4.0 · écran Home ». */
@Composable
private fun crashLine(group: CrashGroup): String {
    val latest = group.latest
    return listOfNotNull(
        stringResource(R.string.crashes_last, crashTimeFormat.format(latest.crashedAt.atZone(ZoneId.systemDefault()))),
        // map is inline, so stringResource may be called in it (joinToString takes a plain lambda).
        group.versions.map { stringResource(R.string.feedback_version, it) }.joinToString(", "),
        latest.screen.takeIf { it.isNotBlank() }?.let { stringResource(R.string.crashes_screen, it) },
    ).joinToString(" · ")
}

@Preview(showBackground = true)
@Composable
private fun CrashesScreenPreview() {
    val trace = "java.lang.IllegalStateException\n\tat fr.familia.app.feature.home.HomeViewModel.load(HomeViewModel.kt:42)"
    fun report(id: String, member: String, minutes: Long) = CrashReport(
        id, member, "1.4.0", 140, "14", "Google Pixel 8", "Home", trace, "0123456789abcdef", Instant.parse("2026-11-21T12:00:00Z").minusSeconds(minutes * 60),
    )
    FamiliaTheme {
        CrashesScreen(CrashesState.Ready(crashGroups(listOf(report("a", "m1", 5), report("b", "m2", 60)))), onBack = {}, onSettle = {})
    }
}
