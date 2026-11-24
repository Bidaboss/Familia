package fr.familia.app.feature.trips.documents

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalActivity
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.TripDocument
import fr.familia.app.core.data.TripDocumentKind
import fr.familia.app.core.data.TripDocuments
import fr.familia.app.core.model.Member
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.feature.trips.TripPage
import fr.familia.app.feature.trips.TripPageAction
import fr.familia.app.feature.trips.detail.TripDetailUiState
import fr.familia.app.feature.trips.detail.TripDetailViewModel
import fr.familia.app.feature.trips.detail.TripPathColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** « Documents » of [tripId] (récit 105, règle 41), for those who go only. */
@Composable
fun TripDocumentsRoute(container: AppContainer?, currentMember: Member, tripId: String, onBack: () -> Unit) {
    val detail: TripDetailViewModel = viewModel(
        key = "trip-documents-$tripId",
        factory = viewModelFactory {
            initializer { TripDetailViewModel(container?.tripRepository, container?.memberRepository, currentMember.id, tripId) }
        },
    )
    val documents: TripDocumentsViewModel = viewModel(
        key = "trip-documents-list-$tripId",
        factory = viewModelFactory { initializer { TripDocumentsViewModel(container?.tripDocumentRepository, tripId, currentMember) } },
    )
    val trip by detail.uiState.collectAsStateWithLifecycle()
    val state by documents.state.collectAsStateWithLifecycle()
    val sending by documents.sending.collectAsStateWithLifecycle()
    val problem by documents.problem.collectAsStateWithLifecycle()
    val shown = trip as? TripDetailUiState.Success
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<PickedFile.Ok?>(null) }

    // A file opened last time stays in the cache only until the screen comes back.
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { openedFolder(context).listFiles()?.forEach { it.delete() } } }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val file = withContext(Dispatchers.IO) { readPicked(context, uri) }) {
                is PickedFile.Ok -> picked = file
                is PickedFile.Refused -> documents.refuse(file.problem)
            }
        }
    }

    TripDocumentsScreen(
        // Only those who go: a follower who opens the link sees nothing of it.
        loading = shown == null || state == DocumentsState.Loading,
        allowed = shown?.isParticipant == true,
        state = state,
        sending = sending,
        problem = problem,
        canDelete = { document -> canDeleteDocument(document, currentMember, shown?.canManage == true) },
        onAdd = { pick.launch(TripDocumentTypes) },
        onOpen = { document ->
            scope.launch {
                val bytes = documents.bytes(document) ?: return@launch
                val file = withContext(Dispatchers.IO) {
                    File(openedFolder(context), "${document.id}.${document.type.extension}").apply { writeBytes(bytes) }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, document.type.contentType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    context.startActivity(view)
                } catch (_: ActivityNotFoundException) {
                    documents.refuse(DocumentProblem.NoViewer)
                }
            }
        },
        onDelete = documents::delete,
        onDismissProblem = documents::dismissProblem,
        onBack = onBack,
    )
    picked?.let { file ->
        AddDocumentDialog(
            suggested = file.title,
            onConfirm = { title, kind ->
                picked = null
                documents.add(file, title, kind)
            },
            onDismiss = { picked = null },
        )
    }
}

/** Stateless. [allowed] is false for someone who does not go: nothing of the documents shows. */
@Composable
fun TripDocumentsScreen(
    loading: Boolean,
    allowed: Boolean,
    state: DocumentsState,
    sending: Boolean,
    problem: DocumentProblem?,
    canDelete: (TripDocument) -> Boolean,
    onAdd: () -> Unit,
    onOpen: (TripDocument) -> Unit,
    onDelete: (TripDocument) -> Unit,
    onDismissProblem: () -> Unit,
    onBack: () -> Unit,
) {
    var deleting by remember { mutableStateOf<TripDocument?>(null) }
    // Fiche F3: the light blue page, the rows in white, « Ajouter un document » the full button at the bottom.
    val action = if (allowed && !loading) {
        TripPageAction(
            text = stringResource(if (sending) R.string.documents_sending else R.string.documents_add),
            onClick = onAdd,
            enabled = !sending && state is DocumentsState.Ready,
            busy = sending,
        )
    } else {
        null
    }
    TripPage(title = stringResource(R.string.documents_title), onBack = onBack, action = action) {
        if (loading && allowed) {
            LoadingState()
            return@TripPage
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!allowed) {
                Text(stringResource(R.string.documents_not_allowed), fontSize = 16.sp)
                return@Column
            }
            Text(stringResource(R.string.documents_intro), fontSize = 15.sp, color = TripPathColors.Ink.copy(alpha = 0.8f))
            Text(stringResource(R.string.documents_limits), fontSize = 13.sp, color = TripPathColors.Ink.copy(alpha = 0.7f))
            problem?.let { shownProblem ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(
                        stringResource(problemText(shownProblem)),
                        color = DocumentRed,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onDismissProblem,
                        modifier = Modifier.heightIn(min = 56.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = TripPathColors.Ink),
                    ) { Text(stringResource(R.string.documents_ok), fontWeight = FontWeight.ExtraBold) }
                }
            }
            when (state) {
                DocumentsState.Loading -> Unit
                DocumentsState.Failed -> Text(stringResource(R.string.documents_failed), fontSize = 16.sp)
                is DocumentsState.Ready -> {
                    if (state.documents.isEmpty()) {
                        Text(stringResource(R.string.documents_empty), fontSize = 16.sp)
                    }
                    TripDocumentKind.entries.forEach { kind ->
                        val ofKind = state.documents.filter { it.kind == kind }
                        if (ofKind.isNotEmpty()) {
                            Text(
                                stringResource(kindHeading(kind)),
                                fontFamily = Fredoka,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                            )
                            ofKind.forEach { document ->
                                DocumentRow(document, onOpen = { onOpen(document) }, onDelete = if (canDelete(document)) ({ deleting = document }) else null)
                            }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { document ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.documents_delete_title)) },
            text = { Text(stringResource(R.string.documents_delete_body, document.title)) },
            confirmButton = {
                TextButton(onClick = { deleting = null; onDelete(document) }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.documents_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.documents_cancel)) }
            },
        )
    }
}

/** The pale blue of the square behind the icon, and the red of the ✕ (fiche F3). */
private val DocumentIconBlue = Color(0xFFEAF6FB)
private val DocumentRed = Color(0xFFC62828)

/** A white row of 72: the icon in its pale blue square, the title and its type, the red ✕ (fiche F3). */
@Composable
private fun DocumentRow(document: TripDocument, onOpen: () -> Unit, onDelete: (() -> Unit)?) {
    val open = stringResource(R.string.documents_open, document.title)
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, contentColor = TripPathColors.Ink, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 72.dp)
                    .clickable(role = Role.Button, onClickLabel = open, onClick = onOpen)
                    .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = if (onDelete == null) 14.dp else 4.dp),
            ) {
                Box(
                    Modifier.size(36.dp).background(DocumentIconBlue, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(kindIcon(document.kind), contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(document.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.documents_by, document.ownerName, document.type.extension.uppercase()),
                        fontSize = 12.sp,
                        color = TripPathColors.Ink.copy(alpha = 0.7f),
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.documents_delete_description, document.title), tint = DocumentRed)
                }
            }
        }
    }
}

/** The title (from the name of the file) and the kind, before anything is sent. */
@Composable
private fun AddDocumentDialog(suggested: String, onConfirm: (String, TripDocumentKind) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(suggested) }
    var kind by rememberSaveable { mutableStateOf(TripDocumentKind.Ticket) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.documents_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(TripDocuments.TITLE_MAX) },
                    label = { Text(stringResource(R.string.documents_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.selectableGroup()) {
                    TripDocumentKind.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .selectable(selected = option == kind, role = Role.RadioButton, onClick = { kind = option }),
                        ) {
                            RadioButton(selected = option == kind, onClick = null)
                            Text(stringResource(kindLabel(option)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title.trim(), kind) }, enabled = title.isNotBlank(), modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.documents_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.documents_cancel)) }
        },
    )
}

private val TripDocumentTypes = arrayOf("application/pdf", "image/jpeg", "image/png")

/** The cache folder shared through the FileProvider (res/xml/photo_paths.xml), emptied at each visit. */
private fun openedFolder(context: Context): File = File(context.cacheDir, "documents").apply { mkdirs() }

/** Reads a picked file, no more than one byte over the limit. */
private fun readPicked(context: Context, uri: Uri): PickedFile {
    val resolver = context.contentResolver
    val name = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    val bytes = runCatching {
        resolver.openInputStream(uri)?.use { input -> input.readNBytesCompat((TripDocuments.MAX_BYTES + 1).toInt()) }
    }.getOrNull()
    return checkPicked(resolver.getType(uri), name, bytes)
}

private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (out.size() < max) {
        val read = read(buffer, 0, minOf(buffer.size, max - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private fun problemText(problem: DocumentProblem): Int = when (problem) {
    DocumentProblem.TooBig -> R.string.documents_too_big
    DocumentProblem.WrongType -> R.string.documents_wrong_type
    DocumentProblem.AddFailed -> R.string.documents_add_failed
    DocumentProblem.OpenFailed -> R.string.documents_open_failed
    DocumentProblem.NoViewer -> R.string.documents_no_viewer
    DocumentProblem.DeleteFailed -> R.string.documents_delete_failed
}

private fun kindHeading(kind: TripDocumentKind): Int = when (kind) {
    TripDocumentKind.Ticket -> R.string.documents_heading_tickets
    TripDocumentKind.Booking -> R.string.documents_heading_bookings
    TripDocumentKind.Paper -> R.string.documents_heading_papers
}

private fun kindLabel(kind: TripDocumentKind): Int = when (kind) {
    TripDocumentKind.Ticket -> R.string.documents_kind_ticket
    TripDocumentKind.Booking -> R.string.documents_kind_booking
    TripDocumentKind.Paper -> R.string.documents_kind_paper
}

private fun kindIcon(kind: TripDocumentKind): ImageVector = when (kind) {
    TripDocumentKind.Ticket -> Icons.Outlined.LocalActivity
    TripDocumentKind.Booking -> Icons.Outlined.Hotel
    TripDocumentKind.Paper -> Icons.Outlined.Lock
}
