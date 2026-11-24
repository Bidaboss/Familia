package fr.familia.app.feature.trips.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.album.AlbumPhoto
import fr.familia.app.core.photo.PhotoStorage
import fr.familia.app.core.share.ShareConsentReminder
import fr.familia.app.core.share.openUrl
import fr.familia.app.core.share.shareImagesWithText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * « Faire un vrai film avec une IA » (récit 108, règle 33, prototype v75): the criteria, the prompt
 * that follows them, « Copier le prompt », and « Exporter vers une IA » which sends the photos, the
 * words and the prompt through the share sheet, after the reminder of EXPERT-6.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilmAiSheet(subject: FilmAiSubject, storage: PhotoStorage?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var ambiance by rememberSaveable { mutableStateOf(FilmAmbiance.Souvenir) }
    var length by rememberSaveable { mutableStateOf(FilmAiLength.M1) }
    var music by rememberSaveable { mutableStateOf(true) }
    var voiceOver by rememberSaveable { mutableStateOf(false) }
    var copied by rememberSaveable { mutableStateOf(false) }
    var remind by rememberSaveable { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportFailed by rememberSaveable { mutableStateOf(false) }
    val prompt = filmAiPrompt(subject, FilmAiOptions(ambiance, length, music, voiceOver))
    val chooser = stringResource(R.string.film_ai_export)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.film_ai_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.film_ai_intro), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.film_ai_ambiance), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilmAmbiance.entries.forEach { a ->
                    FilterChip(selected = a == ambiance, onClick = { ambiance = a; copied = false }, label = { Text(a.label) }, modifier = Modifier.heightIn(min = 56.dp))
                }
            }
            Text(ambiance.look, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.film_ai_length), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilmAiLength.entries.forEach { l ->
                    FilterChip(selected = l == length, onClick = { length = l; copied = false }, label = { Text(l.label) }, modifier = Modifier.heightIn(min = 56.dp))
                }
            }
            SwitchRow(stringResource(R.string.film_ai_music), music) { music = it; copied = false }
            SwitchRow(stringResource(R.string.film_ai_voice), voiceOver) { voiceOver = it; copied = false }
            OutlinedTextField(
                value = prompt,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.film_ai_prompt)) },
                minLines = 4,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { clipboard.setText(AnnotatedString(prompt)); copied = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.film_ai_copy))
            }
            if (copied) Text(stringResource(R.string.film_ai_copied), style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = { remind = true },
                enabled = storage != null && subject.photos.isNotEmpty() && !exporting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.film_ai_export_long))
            }
            if (exporting) {
                Text(stringResource(R.string.film_ai_exporting), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (exportFailed) {
                Text(stringResource(R.string.film_ai_export_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (remind) {
        ShareConsentReminder(
            onContinue = {
                remind = false
                val files = storage ?: return@ShareConsentReminder
                exporting = true
                exportFailed = false
                scope.launch {
                    val photos = try {
                        exportAiPhotos(context.cacheDir, files, filmAiExportPhotos(subject))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    exporting = false
                    if (photos.isNullOrEmpty()) exportFailed = true else shareImagesWithText(context, photos, prompt, chooser)
                }
            },
            onDismiss = { remind = false },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The photos fetched into the cache folder shared with the other apps (xml/file_paths: shares/), emptied each time. */
private suspend fun exportAiPhotos(cacheDir: File, storage: PhotoStorage, photos: List<AlbumPhoto>): List<File> =
    withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "shares/film-ia").apply { deleteRecursively(); mkdirs() }
        photos.mapIndexed { i, photo ->
            val file = File(dir, "photo-${(i + 1).toString().padStart(2, '0')}.jpg")
            val url = storage.downloadUrl(photo.photoPath)
            openUrl(url).use { input -> file.outputStream().use { input.copyTo(it) } }
            file
        }
    }
