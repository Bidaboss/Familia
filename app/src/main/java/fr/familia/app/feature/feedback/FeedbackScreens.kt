package fr.familia.app.feature.feedback

import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import fr.familia.app.core.ui.FamiliaTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.photo.keepPhotoAccess
import fr.familia.app.core.model.Feedback
import fr.familia.app.core.model.FeedbackContext
import fr.familia.app.core.model.FeedbackOrigin
import fr.familia.app.core.photo.StorageImage
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import fr.familia.app.core.ui.LoadingState

/** The form, stateless: every change goes through the callbacks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackFormScreen(
    state: FeedbackFormState,
    onChoose: (Feedback.Kind) -> Unit,
    onType: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    onAgain: () -> Unit = {},
    /** « Mes demandes »: what one sent and the answers. */
    onOpenMine: (() -> Unit)? = null,
    /** A screenshot chosen (its content URI), or null to take it off. */
    onScreenshot: (String?) -> Unit = {},
) {
    // The Android photo picker: no permission to ask.
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) keepPhotoAccess(context, listOf(uri))
        if (uri != null) onScreenshot(uri.toString())
    }
    Scaffold(
        topBar = {
            FamiliaTopBar(title = { Text(stringResource(R.string.feedback_title), maxLines = 2, overflow = TextOverflow.Ellipsis) }, navigationIcon = { BackButton(onClick = onBack) })
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.sent) {
                Text(
                    stringResource(R.string.feedback_sent),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.feedback_back))
                }
                OutlinedButton(onClick = onAgain, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.feedback_again))
                }
                if (onOpenMine != null) {
                    TextButton(onClick = onOpenMine, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.feedback_mine_title))
                    }
                }
                return@Column
            }
            Text(stringResource(R.string.feedback_intro), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Feedback.Kind.entries.forEach { kind ->
                    val selected = state.kind == kind
                    FilterChip(
                        selected = selected,
                        onClick = { onChoose(kind) },
                        label = { Text(stringResource(kindLabel(kind)), style = MaterialTheme.typography.labelLarge) },
                        leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                        modifier = Modifier.heightIn(min = 56.dp),
                    )
                }
            }
            OutlinedTextField(
                value = state.text,
                onValueChange = onType,
                label = { Text(stringResource(if (state.kind == Feedback.Kind.Problem) R.string.feedback_field_problem else R.string.feedback_field_idea)) },
                supportingText = {
                    Column {
                        Text(stringResource(R.string.feedback_private))
                        Text(stringResource(R.string.recipe_comment_count, state.text.length, Feedback.MAX_TEXT))
                    }
                },
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.canAddScreenshot) {
                val shot = state.screenshot
                if (shot == null) {
                    OutlinedButton(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.feedback_screenshot_add))
                    }
                    Text(stringResource(R.string.feedback_screenshot_hint), style = MaterialTheme.typography.bodySmall)
                } else {
                    AsyncImage(
                        model = shot,
                        contentDescription = stringResource(R.string.feedback_screenshot),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                    )
                    TextButton(onClick = { onScreenshot(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.feedback_screenshot_remove))
                    }
                }
            }
            if (state.failed) {
                Text(stringResource(R.string.feedback_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onSend, enabled = state.canSend, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.feedback_send))
            }
            Text(stringResource(R.string.feedback_context), style = MaterialTheme.typography.bodySmall)
            if (onOpenMine != null) {
                TextButton(onClick = onOpenMine, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.feedback_mine_title))
                }
            }
        }
    }
}

/**
 * « Retours » for the admin principal ([onAnswer] set), or « Mes demandes » for their author
 * ([onAnswer] null), stateless.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedbackListScreen(
    state: FeedbackListState,
    onBack: () -> Unit,
    onAnswer: ((id: String, status: Feedback.Status, note: String) -> Unit)? = null,
    onRemove: (String) -> Unit = {},
    /** « Partager pour Claude »: the text and the screenshot together; null outside Retours. */
    onShareForClaude: ((Feedback) -> Unit)? = null,
    /** « Enregistrer la capture »: the screenshot into the gallery; null outside Retours. */
    onSaveScreenshot: ((Feedback) -> Unit)? = null,
    actionFailed: Boolean = false,
    onFailureShown: () -> Unit = {},
) {
    val forAdmin = onAnswer != null
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var answering by rememberSaveable { mutableStateOf<String?>(null) }
    var copied by rememberSaveable { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    Scaffold(
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(if (forAdmin) R.string.feedback_list_title else R.string.feedback_mine_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                FeedbackListState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                FeedbackListState.Error -> EmptyState(
                    icon = Icons.Outlined.Lightbulb,
                    title = stringResource(R.string.feedback_list_error_title),
                    body = stringResource(R.string.feedback_list_error_body),
                )
                is FeedbackListState.Ready -> if (state.items.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Lightbulb,
                        title = stringResource(if (forAdmin) R.string.feedback_list_empty_title else R.string.feedback_mine_empty_title),
                        body = stringResource(if (forAdmin) R.string.feedback_list_empty_body else R.string.feedback_mine_empty_body),
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.items, key = { it.id }) { item ->
                            val fresh = item.status == Feedback.Status.New
                            val tint = if (forAdmin && fresh) ClayTint.Family else ClayTint.Plain
                            ClayCard(modifier = Modifier.fillMaxWidth(), tint = tint) {
                                Text(
                                    stringResource(R.string.feedback_card_head, stringResource(kindLabel(item.kind)), stringResource(statusLabel(item.status))),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(item.text, style = MaterialTheme.typography.bodyLarge)
                                item.screenshot?.let { path ->
                                    StorageImage(
                                        path = path,
                                        contentDescription = stringResource(R.string.feedback_screenshot),
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(240.dp)
                                            // Touching the picture shares it with the text, for Claude.
                                            .then(
                                                if (onShareForClaude != null) {
                                                    Modifier.clickable(onClickLabel = stringResource(R.string.feedback_share_for_claude)) { onShareForClaude(item) }
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                    )
                                }
                                item.note?.let { Text(stringResource(R.string.feedback_note_shown, it), style = MaterialTheme.typography.bodyMedium) }
                                Text(
                                    if (forAdmin) feedbackLine(item) else feedbackDay(item),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FamiliaClay.colors.onTintVariant(tint),
                                )
                                if (forAdmin) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { answering = item.id }, modifier = Modifier.heightIn(min = 56.dp)) {
                                            Text(stringResource(R.string.feedback_answer))
                                        }
                                        TextButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(feedbackForClaude(item)))
                                                copied = item.id
                                            },
                                            modifier = Modifier.heightIn(min = 56.dp),
                                        ) {
                                            Text(stringResource(if (copied == item.id) R.string.feedback_copied else R.string.feedback_copy_for_claude))
                                        }
                                        if (onShareForClaude != null) {
                                            TextButton(onClick = { onShareForClaude(item) }, modifier = Modifier.heightIn(min = 56.dp)) {
                                                Text(stringResource(R.string.feedback_share_for_claude))
                                            }
                                        }
                                        if (onSaveScreenshot != null && item.screenshot != null) {
                                            TextButton(onClick = { onSaveScreenshot(item) }, modifier = Modifier.heightIn(min = 56.dp)) {
                                                Text(stringResource(R.string.feedback_save_screenshot))
                                            }
                                        }
                                        TextButton(onClick = { removing = item.id }, modifier = Modifier.heightIn(min = 56.dp)) {
                                            Text(stringResource(R.string.feedback_remove))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    val items = (state as? FeedbackListState.Ready)?.items.orEmpty()
    items.firstOrNull { it.id == removing }?.let { target ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.feedback_remove_title)) },
            text = { Text(target.text) },
            confirmButton = {
                TextButton(onClick = { removing = null; onRemove(target.id) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.feedback_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_keep)) }
            },
        )
    }
    if (onAnswer != null) {
        items.firstOrNull { it.id == answering }?.let { target ->
            AnswerDialog(target, onDismiss = { answering = null }, onSave = { status, note -> answering = null; onAnswer(target.id, status, note) })
        }
    }
    if (actionFailed) {
        AlertDialog(
            onDismissRequest = onFailureShown,
            text = { Text(stringResource(R.string.feedback_action_failed)) },
            confirmButton = {
                TextButton(onClick = onFailureShown, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

/** The state and the word of the admin principal; its author is told on save. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerDialog(target: Feedback, onDismiss: () -> Unit, onSave: (Feedback.Status, String) -> Unit) {
    var status by rememberSaveable(target.id) { mutableStateOf(target.status) }
    var note by rememberSaveable(target.id) { mutableStateOf(target.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feedback_answer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Feedback.Status.entries.forEach { option ->
                        val selected = status == option
                        FilterChip(
                            selected = selected,
                            onClick = { status = option },
                            label = { Text(stringResource(statusLabel(option))) },
                            leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                            modifier = Modifier.heightIn(min = 56.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(Feedback.MAX_NOTE) },
                    label = { Text(stringResource(R.string.feedback_note_field)) },
                    supportingText = { Text(stringResource(R.string.recipe_comment_count, note.length, Feedback.MAX_NOTE)) },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.feedback_answer_told), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(status, note) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.feedback_answer_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun statusLabel(status: Feedback.Status): Int = when (status) {
    Feedback.Status.New -> R.string.feedback_status_new
    Feedback.Status.Planned -> R.string.feedback_status_planned
    Feedback.Status.Done -> R.string.feedback_status_done
    Feedback.Status.Declined -> R.string.feedback_status_declined
}

private fun kindLabel(kind: Feedback.Kind): Int = when (kind) {
    Feedback.Kind.Idea -> R.string.feedback_kind_idea
    Feedback.Kind.Problem -> R.string.feedback_kind_problem
}

private val feedbackTimeFormat = DateTimeFormatter.ofPattern("d MMMM 'à' HH'h'mm", Locale.FRANCE)

/** « Camille · 12 novembre à 14h05 · Familia 1.4.0 · Android 14 · Google Pixel 8 · depuis l'Aide ». */
@Composable
private fun feedbackLine(item: Feedback): String {
    val c = item.context
    val parts = listOfNotNull(
        item.authorName,
        item.createdAt?.let { feedbackTimeFormat.format(it.atZone(ZoneId.systemDefault())) },
        c.appVersion.takeIf { it.isNotBlank() }?.let { stringResource(R.string.feedback_version, it) },
        c.android.takeIf { it.isNotBlank() }?.let { stringResource(R.string.feedback_android, it) },
        c.device.takeIf { it.isNotBlank() },
        stringResource(if (c.screen == FeedbackOrigin.Help) R.string.feedback_from_help else R.string.feedback_from_family),
    )
    return parts.joinToString(" · ")
}

@Composable
private fun feedbackDay(item: Feedback): String =
    item.createdAt?.let { feedbackTimeFormat.format(it.atZone(ZoneId.systemDefault())) } ?: stringResource(R.string.feedback_sending)

private val previewContext = FeedbackContext("1.4.0", "14", "Google Pixel 8", FeedbackOrigin.Family)

@FamiliaPreviews
@Composable
private fun FeedbackFormPreview() {
    FamiliaTheme {
        FeedbackFormScreen(FeedbackFormState(kind = Feedback.Kind.Problem, text = "Le planning met du temps à s'ouvrir."), {}, {}, {}, {})
    }
}

@FamiliaPreviews
@Composable
private fun FeedbackListPreview() {
    FamiliaTheme {
        FeedbackListScreen(
            FeedbackListState.Ready(
                listOf(
                    Feedback("f1", Feedback.Kind.Idea, "Un rappel pour les anniversaires la veille.", "a", "Inès", previewContext, Instant.parse("2026-11-12T13:05:00Z")),
                    Feedback(
                        "f2", Feedback.Kind.Problem, "La photo ne s'affiche pas.", "b", "Sam", previewContext.copy(screen = FeedbackOrigin.Help),
                        Instant.parse("2026-11-11T09:20:00Z"), Feedback.Status.Planned, "Pour la prochaine version",
                    ),
                ),
            ),
            onBack = {},
            onAnswer = { _, _, _ -> },
        )
    }
}
