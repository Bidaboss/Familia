package fr.familia.app.feature.genealogy

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.model.GenealogyTree
import fr.familia.app.core.model.KinLinkKind
import fr.familia.app.core.model.TreePerson
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.feature.help.HelpButton
import fr.familia.app.feature.help.ScreenHelp

private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 2.5f

/**
 * L'arbre généalogique (PRD, récit 40): one row per generation, eldest at the top, with lines from
 * parents to children and between partners. Pinch, or the two buttons, to zoom. A tap opens the
 * person's card. Stateless but for the zoom and the open card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenealogyScreen(
    state: GenealogyUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** A member's page (récit 34), when the Souvenirs module is on. */
    onOpenProfile: ((String) -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.genealogy_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { HelpButton(ScreenHelp.Genealogy) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                GenealogyUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                GenealogyUiState.Failed -> EmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = stringResource(R.string.genealogy_failed_title),
                    body = stringResource(R.string.genealogy_failed_body),
                )
                is GenealogyUiState.Ready ->
                    if (state.isEmpty) {
                        EmptyState(
                            icon = Icons.Outlined.AccountTree,
                            title = stringResource(R.string.genealogy_empty_title),
                            body = stringResource(R.string.genealogy_empty_body),
                        )
                    } else {
                        ReadyTree(state, onOpenProfile)
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyTree(state: GenealogyUiState.Ready, onOpenProfile: ((String) -> Unit)?) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        pan += panChange
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { zoom = (zoom / 1.25f).coerceAtLeast(MIN_ZOOM) },
                enabled = zoom > MIN_ZOOM,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.genealogy_zoom_out)) }
            OutlinedButton(
                onClick = { zoom = (zoom * 1.25f).coerceAtMost(MAX_ZOOM) },
                enabled = zoom < MAX_ZOOM,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.genealogy_zoom_in)) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .transformable(transform),
        ) {
            TreeDrawing(
                tree = state.tree,
                words = state.words,
                onOpen = { open = it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    },
            )
        }
    }
    val person = open?.let { state.tree.people[it] }
    if (person != null) {
        ModalBottomSheet(onDismissRequest = { open = null }) {
            PersonCard(
                person = person,
                tree = state.tree,
                words = state.words,
                onOpenProfile = onOpenProfile?.takeIf { person is TreePerson.OfMember }?.let { go ->
                    {
                        open = null
                        go(person.id)
                    }
                },
                onClose = { open = null },
            )
        }
    }
}

/** The rows of people, and behind them the lines measured from where each one landed. */
@Composable
internal fun TreeDrawing(tree: GenealogyTree, words: Map<String, String>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val boxes = remember { mutableStateMapOf<String, Rect>() }
    var origin by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val line = MaterialTheme.colorScheme.outline
    Column(
        modifier = modifier
            .wrapContentSize(unbounded = true)
            .onGloballyPositioned { origin = it }
            .drawBehind {
                val stroke = 2.dp.toPx()
                for (link in tree.links) {
                    val a = boxes[link.aId] ?: continue
                    val b = boxes[link.bId] ?: continue
                    when (link.kind) {
                        KinLinkKind.Parent -> {
                            val from = Offset(a.center.x, a.bottom)
                            val to = Offset(b.center.x, b.top)
                            val middle = (from.y + to.y) / 2
                            drawLine(line, from, Offset(from.x, middle), stroke)
                            drawLine(line, Offset(from.x, middle), Offset(to.x, middle), stroke)
                            drawLine(line, Offset(to.x, middle), to, stroke)
                        }
                        KinLinkKind.Partner -> {
                            val (left, right) = if (a.center.x < b.center.x) a to b else b to a
                            drawLine(line, Offset(left.right, left.center.y), Offset(right.left, right.center.y), stroke * 2)
                        }
                    }
                }
            }
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        tree.rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { id ->
                    val person = tree.people.getValue(id)
                    PersonNode(
                        person = person,
                        word = words[id],
                        onClick = { onOpen(id) },
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val root = origin ?: return@onGloballyPositioned
                            if (!root.isAttached || !coords.isAttached) return@onGloballyPositioned
                            val topLeft = root.localPositionOf(coords, Offset.Zero)
                            boxes[id] = Rect(topLeft, Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonNode(person: TreePerson, word: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ClayCard(
        modifier = modifier.width(128.dp),
        onClick = onClick,
        onClickLabel = stringResource(R.string.genealogy_open_card),
        contentPadding = PaddingValues(12.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Avatar(person)
            Text(
                person.firstName,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            word?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, maxLines = 2)
            }
            lifeLine(person)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Avatar(person: TreePerson) {
    when (person) {
        is TreePerson.OfMember -> MemberAvatar(person.member, size = 48.dp)
        is TreePerson.OfKin -> Box(
            Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                person.firstName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** « 1921 – 1999 » for someone remembered, a year of birth for a living relative, or nothing. */
@Composable
private fun lifeLine(person: TreePerson): String? {
    val kin = (person as? TreePerson.OfKin)?.kin ?: return null
    val born = kin.born?.take(4)
    val died = kin.died?.take(4)
    return when {
        !kin.living && born != null && died != null -> stringResource(R.string.genealogy_years, born, died)
        !kin.living && died != null -> stringResource(R.string.genealogy_died, died)
        !kin.living -> stringResource(R.string.genealogy_remembered)
        born != null -> stringResource(R.string.genealogy_born, born)
        else -> null
    }
}

/** The card of one person: who they are, their word, and who they are joined to. */
@Composable
internal fun PersonCard(
    person: TreePerson,
    tree: GenealogyTree,
    words: Map<String, String>,
    onOpenProfile: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val names = { ids: List<String> ->
        ids.mapNotNull { tree.people[it] }.sortedBy { it.firstName.lowercase() }.joinToString(", ") { p ->
            words[p.id]?.let { "${p.firstName} ($it)" } ?: p.firstName
        }
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(person)
            Column {
                Text(fullName(person), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                words[person.id]?.let { Text(stringResource(R.string.genealogy_my_word, it), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        (person as? TreePerson.OfKin)?.kin?.let { kin ->
            Text(
                stringResource(if (kin.living) R.string.genealogy_kin_living else R.string.genealogy_kin_remembered),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            lifeLine(person)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            kin.note?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
        Relation(R.string.genealogy_parents, names(tree.parentsOf(person.id)))
        Relation(R.string.genealogy_partners, names(tree.partnersOf(person.id)))
        Relation(R.string.genealogy_children, names(tree.childrenOf(person.id)))
        if (onOpenProfile != null) {
            OutlinedButton(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.genealogy_open_profile))
            }
        }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.genealogy_close))
        }
    }
}

@Composable
private fun Relation(@StringRes label: Int, names: String) {
    if (names.isEmpty()) return
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(names, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun fullName(person: TreePerson): String = when (person) {
    is TreePerson.OfMember -> person.firstName
    is TreePerson.OfKin -> listOfNotNull(person.kin.firstName, person.kin.lastName).joinToString(" ")
}
