package fr.familia.app.feature.calendarfeed

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.CalendarFeedRepository
import fr.familia.app.core.data.calendarFeedUrl
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayCardTitle
import fr.familia.app.core.ui.ClaySpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How long a write may hold the buttons before the screen lets go (it goes on in the background). */
private const val WRITE_TIMEOUT_MILLIS = 4_000L

/** What the screen shows: waiting, my link (null while I have none), or the link cannot be read. */
sealed interface CalendarFeedState {
    data object Loading : CalendarFeedState
    data class Ready(val url: String?) : CalendarFeedState
    data object Error : CalendarFeedState
}

/** « Voir dans mon agenda » (récit 49): reads my token and writes a fresh one on demand. */
class CalendarFeedViewModel(private val repo: CalendarFeedRepository?, private val memberId: String) : ViewModel() {

    val state: StateFlow<CalendarFeedState> = (repo?.observeToken(memberId) ?: emptyFlow())
        .map<String?, CalendarFeedState> { token -> CalendarFeedState.Ready(token?.let(::calendarFeedUrl)) }
        .catch { e -> if (e is CancellationException) throw e else emit(CalendarFeedState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), if (repo == null) CalendarFeedState.Error else CalendarFeedState.Loading)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _failed = MutableStateFlow(false)

    /** True when the server refused the new link: the screen says so once. */
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    /** Creates my link, or replaces it: the old one stops working. */
    fun renew() {
        val r = repo ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                // Offline, the write waits in Firestore's queue: the screen does not stay blocked.
                withTimeoutOrNull(WRITE_TIMEOUT_MILLIS) { r.renew(memberId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _failed.value = true
            } finally {
                _busy.value = false
            }
        }
    }

    private val _removeFailed = MutableStateFlow(false)

    /** True when the link could not be removed: the screen says so once. */
    val removeFailed: StateFlow<Boolean> = _removeFailed.asStateFlow()

    /** « Supprimer mon lien »: the old link stops working, and no new one is made. */
    fun remove() {
        val r = repo ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                withTimeoutOrNull(WRITE_TIMEOUT_MILLIS) { r.remove(memberId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _removeFailed.value = true
            } finally {
                _busy.value = false
            }
        }
    }

    fun failureShown() {
        _removeFailed.value = false
        _failed.value = false
    }
}

@Composable
fun CalendarFeedRoute(container: AppContainer?, memberId: String, onBack: () -> Unit) {
    val viewModel: CalendarFeedViewModel = viewModel(
        factory = viewModelFactory { initializer { CalendarFeedViewModel(container?.calendarFeedRepository, memberId) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val removeFailed by viewModel.removeFailed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val refused = stringResource(R.string.calendar_feed_failed)
    val notRemoved = stringResource(R.string.calendar_feed_remove_failed)
    LaunchedEffect(failed, removeFailed) {
        if (failed || removeFailed) {
            Toast.makeText(context, if (removeFailed) notRemoved else refused, Toast.LENGTH_LONG).show()
            viewModel.failureShown()
        }
    }
    CalendarFeedScreen(state = state, busy = busy, onRenew = viewModel::renew, onBack = onBack, onRemove = viewModel::remove)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarFeedScreen(state: CalendarFeedState, busy: Boolean, onRenew: () -> Unit, onBack: () -> Unit, onRemove: () -> Unit = {}) {
    var confirmRenew by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copied = stringResource(R.string.calendar_feed_copied)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_feed_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                CalendarFeedState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                CalendarFeedState.Error -> Text(
                    stringResource(R.string.calendar_feed_error),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(ClaySpacing.screen),
                )
                is CalendarFeedState.Ready -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                ) {
                    Section(stringResource(R.string.calendar_feed_what_title)) {
                        Text(stringResource(R.string.calendar_feed_what_body), style = MaterialTheme.typography.bodyMedium)
                    }
                    val url = state.url
                    Section(stringResource(R.string.calendar_feed_link_title)) {
                        if (url == null) {
                            Text(stringResource(R.string.calendar_feed_none), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onRenew, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                Text(stringResource(R.string.calendar_feed_create))
                            }
                        } else {
                            SelectionContainer {
                                Text(url, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                            Text(
                                stringResource(R.string.calendar_feed_secret),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = {
                                    clipboard.setText(AnnotatedString(url))
                                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            ) { Text(stringResource(R.string.calendar_feed_copy)) }
                            OutlinedButton(onClick = { shareFeed(context, url) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                Text(stringResource(R.string.calendar_feed_share))
                            }
                        }
                    }
                    if (url != null) {
                        Section(stringResource(R.string.calendar_feed_steps_title)) {
                            listOf(
                                R.string.calendar_feed_step_1,
                                R.string.calendar_feed_step_2,
                                R.string.calendar_feed_step_3,
                                R.string.calendar_feed_step_4,
                                R.string.calendar_feed_step_5,
                            ).forEachIndexed { i, step ->
                                Text(stringResource(R.string.calendar_feed_step, i + 1, stringResource(step)), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Section(stringResource(R.string.calendar_feed_renew_title)) {
                            Text(stringResource(R.string.calendar_feed_renew_body), style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = { confirmRenew = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                Text(stringResource(R.string.calendar_feed_renew))
                            }
                        }
                        Section(stringResource(R.string.calendar_feed_remove_title)) {
                            Text(stringResource(R.string.calendar_feed_remove_body), style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = { confirmRemove = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                Text(stringResource(R.string.calendar_feed_remove))
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.calendar_feed_remove_confirm_title)) },
            text = { Text(stringResource(R.string.calendar_feed_remove_body)) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemove() }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.calendar_feed_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (confirmRenew) {
        AlertDialog(
            onDismissRequest = { confirmRenew = false },
            title = { Text(stringResource(R.string.calendar_feed_renew_confirm_title)) },
            text = { Text(stringResource(R.string.calendar_feed_renew_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmRenew = false; onRenew() }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.calendar_feed_renew))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRenew = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    ClayCard(Modifier.fillMaxWidth().padding(horizontal = ClaySpacing.screen, vertical = 8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ClayCardTitle(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Sends the link to myself (by mail, to open Google Agenda on a computer). */
private fun shareFeed(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.calendar_feed_share_chooser)))
}
