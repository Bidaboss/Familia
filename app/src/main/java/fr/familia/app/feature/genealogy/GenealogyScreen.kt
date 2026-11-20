package fr.familia.app.feature.genealogy

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import fr.familia.app.core.ui.FamiliaTopBar
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.familia.app.R
import fr.familia.app.core.data.KinPhotoChange
import fr.familia.app.core.model.GenealogyTree
import fr.familia.app.core.model.Kin
import fr.familia.app.core.model.KinLink
import fr.familia.app.core.model.KinLinkKind
import fr.familia.app.core.model.TreeBox
import fr.familia.app.core.model.TreePerson
import fr.familia.app.core.model.treeParentLines
import fr.familia.app.core.ui.BackButton
import fr.familia.app.core.ui.ClayCard
import fr.familia.app.core.ui.ClayLink
import fr.familia.app.core.ui.EmptyState
import fr.familia.app.core.ui.KinAvatar
import fr.familia.app.core.ui.LoadingState
import fr.familia.app.core.ui.LocalMemberPhotos
import fr.familia.app.core.ui.MemberAvatar
import fr.familia.app.core.ui.SideBySide
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
    /** Changing the tree and one's words (récit 40c); read only when null. */
    actions: GenealogyActions? = null,
    actionFailed: Boolean = false,
    onFailureShown: () -> Unit = {},
    /** « Faire l'affiche de l'arbre », with the switch treePoster (récit 53); null hides it. */
    onMakePoster: (() -> Unit)? = null,
    /** L'arbre de vie (switch arbreDeVie): the « Arbre » and « Liste » tabs; off, the rows only. */
    treeOfLife: Boolean = false,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            FamiliaTopBar(
                title = { Text(stringResource(R.string.genealogy_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = { HelpButton(ScreenHelp.Genealogy) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                GenealogyUiState.Loading -> LoadingState(Modifier.align(Alignment.Center))
                GenealogyUiState.Failed -> EmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = stringResource(R.string.genealogy_failed_title),
                    body = stringResource(R.string.genealogy_failed_body),
                )
                is GenealogyUiState.Ready -> ReadyTree(state, onOpenProfile, actions, onMakePoster, onBack, treeOfLife)
            }
        }
    }
    if (actionFailed) {
        AlertDialog(
            onDismissRequest = onFailureShown,
            title = { Text(stringResource(R.string.genealogy_failed_change_title)) },
            text = { Text(stringResource(R.string.genealogy_failed_change_body)) },
            confirmButton = {
                TextButton(onClick = onFailureShown, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
}

/** What one can do on the tree; each is checked again by the ViewModel and the rules. */
data class GenealogyActions(
    val onAddLink: (personId: String, relation: Relation, otherId: String) -> Unit,
    val onRemoveLink: (KinLink) -> Unit,
    val onSaveKin: (kin: Kin, joinTo: Pair<String, Relation>?, photo: KinPhotoChange?) -> Unit,
    val onDeleteKin: (String) -> Unit,
    val onSetWord: (personId: String, word: String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyTree(
    state: GenealogyUiState.Ready,
    onOpenProfile: ((String) -> Unit)?,
    actions: GenealogyActions?,
    onMakePoster: (() -> Unit)?,
    onBack: () -> Unit,
    treeOfLife: Boolean = false,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    // The dialogs: one at a time, each named by the person it is about.
    var wordFor by rememberSaveable { mutableStateOf<String?>(null) }
    var linkFor by rememberSaveable { mutableStateOf<String?>(null) }
    var editKin by rememberSaveable { mutableStateOf<String?>(null) }
    var joinPerson by rememberSaveable { mutableStateOf<String?>(null) }
    var joinRelation by rememberSaveable { mutableStateOf<Relation?>(null) }
    var removeKin by rememberSaveable { mutableStateOf<String?>(null) }
    var pickFirst by rememberSaveable { mutableStateOf(false) }
    val editing = actions?.takeIf { state.canEdit }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        pan += panChange
    }
    val addFirst: (@Composable () -> Unit)? = if (editing == null) {
        null
    } else {
        {
            // An empty tree still shows nobody: « Relier deux membres » picks the first one by name.
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.joinable.size > 1) {
                    Button(onClick = { pickFirst = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.genealogy_join_members))
                    }
                    OutlinedButton(onClick = { editKin = NEW_KIN }, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.genealogy_kin_add))
                    }
                } else {
                    Button(onClick = { editKin = NEW_KIN }, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.genealogy_kin_add))
                    }
                }
            }
        }
    }
    if (state.isEmpty) {
        EmptyState(
            icon = Icons.Outlined.AccountTree,
            title = stringResource(R.string.genealogy_empty_title),
            body = stringResource(R.string.genealogy_empty_body),
            // Who cannot add a link yet still gets a way on, never a dead end.
            action = if (addFirst != null) addFirst else {
                { ClayLink(stringResource(R.string.empty_go_back), onClick = onBack) }
            },
        )
    } else Column(Modifier.fillMaxSize()) {
        // L'arbre de vie (spec DESIGN du 18/11), behind its switch: « Arbre » draws it, « Liste »
        // keeps the rows, the plain reading for TalkBack and Contraste fort, where it opens.
        val prefersList = rememberPrefersList()
        var view by rememberSaveable { mutableStateOf(if (treeOfLife && !prefersList) TreeView.Tree else TreeView.List) }
        if (treeOfLife) {
            TreeViewSwitch(view, onChange = { view = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        }
        if (treeOfLife && view == TreeView.Tree) {
            TreeOfLifeView(state, onOpen = { open = it }, onAddKin = editing?.let { { editKin = NEW_KIN } })
        } else {
            if (onMakePoster != null) {
                ClayLink(stringResource(R.string.tree_poster_open), onClick = onMakePoster, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
            }
            if (editing != null) {
                OutlinedButton(
                    onClick = { editKin = NEW_KIN },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.genealogy_kin_add)) }
            }
            SideBySide(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), spacing = 12.dp) { item ->
                OutlinedButton(
                    onClick = { zoom = (zoom / 1.25f).coerceAtLeast(MIN_ZOOM) },
                    enabled = zoom > MIN_ZOOM,
                    modifier = item.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.genealogy_zoom_out)) }
                OutlinedButton(
                    onClick = { zoom = (zoom * 1.25f).coerceAtMost(MAX_ZOOM) },
                    enabled = zoom < MAX_ZOOM,
                    modifier = item.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.genealogy_zoom_in)) }
                // Moved too far, or lost: back to the start (relecture #122).
                OutlinedButton(
                    onClick = {
                        zoom = 1f
                        pan = Offset.Zero
                    },
                    enabled = zoom != 1f || pan != Offset.Zero,
                    modifier = item.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.genealogy_recenter)) }
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
                onWord = actions?.takeIf { person.id != state.me }?.let { { wordFor = person.id } },
                onAddLink = editing?.takeIf { state.canEditPerson(person.id) }?.let { { linkFor = person.id } },
                onRemoveLink = editing?.let { a -> { link: KinLink -> a.onRemoveLink(link) } },
                canEditLink = state::canEditLink,
                onEditKin = editing?.takeIf { person is TreePerson.OfKin && state.canEditPerson(person.id) }?.let { { editKin = person.id } },
                onRemoveKin = editing?.takeIf { person is TreePerson.OfKin && state.canEditPerson(person.id) }?.let { { removeKin = person.id } },
            )
        }
    }
    val people = state.tree.people
    wordFor?.let { people[it] }?.let { person ->
        WordDialog(person, state.words[person.id], onSave = { word ->
            actions?.onSetWord(person.id, word)
            wordFor = null
        }, onDismiss = { wordFor = null })
    }
    if (pickFirst) {
        PickPersonDialog(state.joinable, onPick = { id ->
            pickFirst = false
            linkFor = id
        }, onDismiss = { pickFirst = false }, everyone = state.tree.people.values, householdNames = state.householdNames)
    }
    linkFor?.let { people[it] }?.let { person ->
        LinkDialog(person, state.tree, onPick = { relation, other ->
            linkFor = null
            if (other == NEW_KIN) {
                joinPerson = person.id
                joinRelation = relation
                editKin = NEW_KIN
            } else {
                editing?.onAddLink(person.id, relation, other)
            }
        }, onDismiss = { linkFor = null }, canJoin = { relation, other -> state.canJoin(person.id, relation, other) }, householdNames = state.householdNames)
    }
    editKin?.let { id ->
        val kin = (people[id] as? TreePerson.OfKin)?.kin
        if (id == NEW_KIN || kin != null) {
            val close = {
                editKin = null
                joinPerson = null
                joinRelation = null
            }
            KinDialog(kin, photo = kin?.let { LocalMemberPhotos.current[it.id] }, onSave = { saved, photo ->
                val join = joinPerson?.let { p -> joinRelation?.let { r -> p to r } }
                editing?.onSaveKin(saved, join, photo)
                close()
            }, onDismiss = close)
        }
    }
    removeKin?.let { (people[it] as? TreePerson.OfKin)?.kin }?.let { kin ->
        RemoveKinDialog(kin, onConfirm = {
            editing?.onDeleteKin(kin.id)
            removeKin = null
            open = null
        }, onDismiss = { removeKin = null })
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
                    if (link.kind != KinLinkKind.Partner) continue
                    val a = boxes[link.aId] ?: continue
                    val b = boxes[link.bId] ?: continue
                    val (left, right) = if (a.center.x < b.center.x) a to b else b to a
                    drawLine(line, Offset(left.right, left.center.y), Offset(right.left, right.center.y), stroke * 2)
                }
                // One drop per couple or parent, one bar per family (retour du 17/11).
                val placed = boxes.mapValues { (_, r) -> TreeBox(r.left, r.top, r.right, r.bottom) }
                for (piece in treeParentLines(tree.links, placed)) {
                    drawLine(line, Offset(piece.x1, piece.y1), Offset(piece.x2, piece.y2), stroke)
                }
            }
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        // Each card on its column, so children stand under the middle of their parents (retour du 18/11).
        val rowWidth = NODE_SLOT * tree.width.toFloat() - NODE_GAP
        tree.rows.forEach { row ->
            Box(Modifier.width(rowWidth)) {
                row.forEach { id ->
                    val person = tree.people.getValue(id)
                    PersonNode(
                        person = person,
                        name = tree.drawnName(id),
                        word = words[id],
                        onClick = { onOpen(id) },
                        modifier = Modifier.offset(x = NODE_SLOT * (tree.columns[id] ?: 0.0).toFloat()).onGloballyPositioned { coords ->
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

/** A card of the tree and the space after it. */
private val NODE_WIDTH = 128.dp
private val NODE_GAP = 24.dp
private val NODE_SLOT = NODE_WIDTH + NODE_GAP

@Composable
private fun PersonNode(person: TreePerson, name: String, word: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ClayCard(
        modifier = modifier.width(NODE_WIDTH),
        onClick = onClick,
        onClickLabel = stringResource(R.string.genealogy_open_card),
        contentPadding = PaddingValues(12.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Avatar(person)
            Text(
                name,
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
        is TreePerson.OfKin -> KinAvatar(person.id, person.firstName, size = 48.dp)
    }
}

/** « 1921 – 1999 » for someone remembered, a year of birth for a living relative, or nothing. */
@Composable
internal fun lifeLine(person: TreePerson): String? {
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
    onWord: (() -> Unit)? = null,
    onAddLink: (() -> Unit)? = null,
    onRemoveLink: ((KinLink) -> Unit)? = null,
    canEditLink: (KinLink) -> Boolean = { false },
    onEditKin: (() -> Unit)? = null,
    onRemoveKin: (() -> Unit)? = null,
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
        if (onRemoveLink == null) {
            Relatives(R.string.genealogy_parents, names(tree.parentsOf(person.id)))
            Relatives(R.string.genealogy_partners, names(tree.partnersOf(person.id)))
            Relatives(R.string.genealogy_children, names(tree.childrenOf(person.id)))
        } else {
            // Each link on its own line, with « Retirer » where I may change it.
            val mine = tree.links.filter { it.aId == person.id || it.bId == person.id }
            // Removing a link changes the tree for the whole family: asked once more.
            var confirm by remember { mutableStateOf<KinLink?>(null) }
            mine.forEach { link ->
                val otherId = if (link.aId == person.id) link.bId else link.aId
                val other = tree.people[otherId] ?: return@forEach
                val role = when {
                    link.kind == KinLinkKind.Partner -> R.string.genealogy_role_partner
                    link.bId == person.id -> R.string.genealogy_role_parent
                    else -> R.string.genealogy_role_child
                }
                val label = stringResource(role, other.firstName)
                val removeLabel = stringResource(R.string.genealogy_link_remove_described, label)
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (canEditLink(link)) {
                        TextButton(
                            onClick = { confirm = link },
                            modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = removeLabel },
                        ) {
                            Text(stringResource(R.string.genealogy_link_remove))
                        }
                    }
                }
            }
            confirm?.let { link ->
                val otherId = if (link.aId == person.id) link.bId else link.aId
                RemoveLinkDialog(
                    firstName = person.firstName,
                    otherName = tree.people[otherId]?.firstName.orEmpty(),
                    onConfirm = {
                        confirm = null
                        onRemoveLink(link)
                    },
                    onDismiss = { confirm = null },
                )
            }
        }
        if (onWord != null) {
            OutlinedButton(onClick = onWord, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.genealogy_word_action, person.firstName))
            }
        }
        if (onAddLink != null) {
            OutlinedButton(onClick = onAddLink, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.genealogy_link_add))
            }
        }
        if (onEditKin != null) {
            OutlinedButton(onClick = onEditKin, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.genealogy_kin_edit))
            }
        }
        if (onRemoveKin != null) {
            TextButton(onClick = onRemoveKin, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.genealogy_kin_remove), color = MaterialTheme.colorScheme.error)
            }
        }
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
private fun Relatives(@StringRes label: Int, names: String) {
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
