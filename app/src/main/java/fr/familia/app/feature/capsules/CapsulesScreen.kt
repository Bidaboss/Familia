package fr.familia.app.feature.capsules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.Capsule
import fr.familia.app.core.theme.ClayTint
import fr.familia.app.core.theme.FamiliaClay
import fr.familia.app.core.theme.FamiliaTheme
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.FamiliaPreviews
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** « Capsules temporelles »: those opened for me, then mine still sealed. Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapsulesScreen(
    state: CapsulesUiState,
    onBack: () -> Unit,
    /** Opens a capsule, once its page exists. */
    onOpen: ((capsuleId: String) -> Unit)? = null,
    /** « Nouvelle capsule », once the form exists. */
    onAdd: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capsules_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { HelpButton(ScreenHelp.Capsules) },
            )
        },
        floatingActionButton = {
            if (state is CapsulesUiState.Ready && onAdd != null) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.capsules_add)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                CapsulesUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                CapsulesUiState.Failed -> EmptyState(
                    icon = Icons.Outlined.HourglassEmpty,
                    title = stringResource(R.string.capsules_error_title),
                    body = stringResource(R.string.capsules_error_body),
                )
                is CapsulesUiState.Ready -> if (state.isEmpty) {
                    EmptyState(
                        icon = Icons.Outlined.HourglassEmpty,
                        title = stringResource(R.string.capsules_empty_title),
                        body = stringResource(R.string.capsules_empty_body),
                    )
                } else {
                    CapsuleList(state, onOpen)
                }
            }
        }
    }
}

@Composable
private fun CapsuleList(state: CapsulesUiState.Ready, onOpen: ((String) -> Unit)?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Room at the bottom so the add button never hides the last capsule.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.opened.isNotEmpty()) {
            item(key = "opened-title") { SectionTitle(stringResource(R.string.capsules_opened)) }
            items(state.opened, key = { "opened-${it.capsule.id}" }) { row -> CapsuleCard(row, onOpen?.let { open -> { open(row.capsule.id) } }) }
        }
        if (state.sealed.isNotEmpty()) {
            item(key = "sealed-title") { SectionTitle(stringResource(R.string.capsules_sealed)) }
            items(state.sealed, key = { "sealed-${it.capsule.id}" }) { row -> CapsuleCard(row, onOpen?.let { open -> { open(row.capsule.id) } }) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

@Composable
private fun CapsuleCard(row: CapsuleRow, onClick: (() -> Unit)?) {
    val muted = FamiliaClay.colors.onTintVariant(ClayTint.Plain)
    val capsule = row.capsule
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        tint = if (capsule.isOpened) ClayTint.Family else ClayTint.Plain,
        onClick = onClick,
        onClickLabel = onClick?.let { stringResource(R.string.capsules_open) },
    ) {
        Text(capsuleHeadline(row), style = MaterialTheme.typography.titleMedium)
        Text(capsuleDateLine(capsule), style = MaterialTheme.typography.bodyMedium, color = FamiliaClay.colors.onTintVariant(if (capsule.isOpened) ClayTint.Family else ClayTint.Plain))
        if (capsule.isOpened) {
            Text(capsule.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else {
            Text(stringResource(R.string.capsules_sealed_hint), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
    }
}

/** « De Camille » for a capsule received; « Pour Noé et Lou » for one's own. */
@Composable
internal fun capsuleHeadline(row: CapsuleRow): String =
    if (row.isMine) {
        stringResource(R.string.capsule_for, row.recipients.ifEmpty { stringResource(R.string.capsule_for_nobody) })
    } else {
        stringResource(R.string.capsule_from, row.capsule.authorName)
    }

/** « Ouverte le 12 décembre 2026 » or « S'ouvre le 12 décembre 2027 ». */
@Composable
internal fun capsuleDateLine(capsule: Capsule): String =
    stringResource(if (capsule.isOpened) R.string.capsule_opened_on else R.string.capsule_opens_on, capsuleDay(capsule.openAt))

internal fun capsuleDay(day: LocalDate): String = day.format(capsuleDayFormatter)

private val capsuleDayFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)

/** Fictional capsules for previews and screenshots: Inès wrote to Camille, Camille to the children. */
internal fun previewCapsules(): List<Capsule> = listOf(
    Capsule("k1", "Joyeux anniversaire ! Te souviens-tu de ce pique-nique au bord du lac ? Je voulais te le rappeler un an après.", 1, listOf("a"), LocalDate.of(2026, 11, 2), listOf("a"), null, "b", "Inès", null),
    Capsule("k2", "Pour vos 18 ans : voici ce que vous aimiez à 10 ans.", 0, listOf("c", "d"), LocalDate.of(2031, 7, 19), emptyList(), null, "a", "Camille", null),
    Capsule("k3", "Un petit mot pour Noël prochain.", 2, listOf("b"), LocalDate.of(2027, 12, 24), emptyList(), null, "a", "Camille", null),
)

internal fun previewCapsulesState(): CapsulesUiState.Ready = previewCapsules().let { list ->
    CapsulesUiState.Ready(
        opened = listOf(CapsuleRow(list[0], "Camille", isMine = false)),
        sealed = listOf(CapsuleRow(list[2], "Inès", isMine = true), CapsuleRow(list[1], "Noé et Lou", isMine = true)),
    )
}

@FamiliaPreviews
@Composable
private fun CapsulesScreenPreview() {
    FamiliaTheme { Surface { CapsulesScreen(previewCapsulesState(), {}, onOpen = {}, onAdd = {}) } }
}
