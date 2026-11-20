package fr.familia.app.feature.genealogy

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.familia.app.R
import fr.familia.app.core.model.GenealogyTree
import fr.familia.app.core.model.TreeOfLife
import fr.familia.app.core.model.TreePerson
import fr.familia.app.core.theme.ClayShadow
import fr.familia.app.core.theme.ClayStyle
import fr.familia.app.core.theme.Fredoka
import fr.familia.app.core.theme.LocalClayStyle
import fr.familia.app.core.theme.Nunito
import fr.familia.app.core.theme.OnMemberColor
import fr.familia.app.core.ui.LocalMemberInitials
import fr.familia.app.core.ui.LocalMemberPhotos
import fr.familia.app.core.ui.memberColor
import kotlin.math.max

/** The colors of the spec (DESIGN, 18/11): an illustration, the same in every theme. */
private object Palette {
    val Branch = Color(0xFFB58E70)
    val BranchLight = Color(0xFFE2C6AC)
    val BranchShade = Color(0xFF9C7558).copy(alpha = 0.35f)
    val FoliageIn = Color(0xFFD6EEC9)
    val FoliageOut = Color(0xFF9FCF9C)
    val Leaves = listOf(Color(0xFFA9D8A8), Color(0xFF8CC9A0), Color(0xFFC3E5B5), Color(0xFFB7DDC6))
    val Petals = listOf(Color(0xFFF8C8D4), Color(0xFFFBE0C8), Color(0xFFE6DAF7))
    val FlowerHeart = Color(0xFFF6D57A)
    val Disc = Color(0xFFFFF6E8)
    val DiscEdge = Color(0xFFE8C9A8)
    val DiscDots = Color(0xFFF4D9C8)
    val Ground = Color(0xFFCFE8C4)
    val Shadow = Color(0xFF8A6A50).copy(alpha = 0.14f)
    val Label = Color(0xFF4A3B30)
    val KinInitial = Color(0xFF6A4A30)
    val Outline = Color(0xFF1E1B18)
    val Sky = listOf(Color(0xFFFDF0F2), Color(0xFFFFF8F0), Color(0xFFF4FAEE))
}

/** The same chance for the leaves and flowers at each opening. */
private const val SEED = 2111
private const val MAX_ZOOM = 3f
private val PAD = 16.dp

/**
 * « L'arbre de vie » (spec DESIGN du 18/11, switch arbreDeVie): [TreeOfLife] drawn on a Canvas, the
 * people on top as medallions one can touch. [outlined] (Contraste fort) rings them in dark. The
 * drawing keeps its own size (never under 56 dp to touch), one pans along it and pinches from 1 to 3.
 */
@Composable
internal fun TreeOfLifeDrawing(
    tree: GenealogyTree,
    words: Map<String, String>,
    onOpen: (String) -> Unit,
    outlined: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val pillStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Palette.Label)
    val couple = stringResource(R.string.tree_of_life_couple)
    val life = remember(tree, density.fontScale) {
        TreeOfLife(tree) { text -> with(density) { measurer.measure(text, pillStyle).size.width.toDp().value } }
    }
    val (left, top, right, bottom) = life.bounds
    val width = (right - left).dp + PAD * 2
    val height = (bottom - top).dp + PAD * 2
    // From the top left of the drawing to its point (0, 0), the top of the trunk.
    val ox = -left.dp + PAD
    val oy = -top.dp + PAD
    // Never smaller than its own size: a medallion keeps its 56 dp to touch (grille). One pans to
    // see the rest, and pinches up to 3, never so far that the drawing leaves the screen.
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var view by remember { mutableStateOf(IntSize.Zero) }
    val widthPx = with(density) { width.toPx() }
    val heightPx = with(density) { height.toPx() }
    fun clamped(offset: Offset, scale: Float): Offset {
        val spareX = max(0f, (widthPx * scale - view.width) / 2)
        val spareY = max(0f, heightPx * scale - view.height)
        return Offset(offset.x.coerceIn(-spareX, spareX), offset.y.coerceIn(-spareY, 0f))
    }
    val transform = rememberTransformableState { change, move, _ ->
        zoom = (zoom * change).coerceIn(1f, MAX_ZOOM)
        pan = clamped(pan + move, zoom)
    }
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(Palette.Sky))
            .clipToBounds()
            .onSizeChanged { view = it }
            .transformable(transform),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints.fixed(width.roundToPx(), height.roundToPx()))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    translationX = pan.x
                    translationY = pan.y
                },
        ) {
            Canvas(Modifier.size(width, height).clearAndSetSemantics {}) {
                translate(ox.toPx(), oy.toPx()) { drawScenery(life) }
            }
            life.units.forEach { unit ->
                val radius = life.radiusOf(unit.ids.first())
                unit.ids.forEach { id ->
                    val (x, y) = life.centers.getValue(id)
                    Medallion(tree.people.getValue(id), tree.drawnName(id), radius.dp, words[id], outlined, onOpen, Modifier.centerAt(ox + x.dp, oy + y.dp))
                }
                val names = unit.ids.map { tree.drawnName(it) }
                val label = if (names.size == 2) String.format(couple, names[0], names[1]) else names.joinToString(", ")
                Pill(label, pillStyle, outlined, Modifier.centerAt(ox + unit.x.dp, oy + unit.y.dp + radius.dp + 14.dp))
            }
            if (life.roots.isNotEmpty()) {
                Text(
                    stringResource(R.string.tree_of_life_roots).uppercase(),
                    style = pillStyle.copy(color = Palette.KinInitial, letterSpacing = 1.sp),
                    modifier = Modifier.centerAt(ox, oy + TreeOfLife.GROUND_Y.dp + 14.dp).clearAndSetSemantics {},
                )
            }
            life.roots.forEach { root ->
                val person = tree.people.getValue(root.id)
                Medallion(person, tree.drawnName(root.id), TreeOfLife.ROOT_RADIUS.dp, words[root.id], outlined, onOpen, Modifier.centerAt(ox + root.x.dp, oy + root.y.dp))
                Column(
                    Modifier.centerAt(ox + root.x.dp, oy + root.y.dp + TreeOfLife.ROOT_RADIUS.dp + 18.dp).clearAndSetSemantics {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(tree.drawnName(root.id), style = pillStyle, textAlign = TextAlign.Center)
                    lifeLine(person)?.let { Text(it, style = pillStyle.copy(fontWeight = FontWeight.Normal), textAlign = TextAlign.Center) }
                }
            }
        }
    }
}

/** Places this element with its centre on ([x], [y]) of the drawing. */
private fun Modifier.centerAt(x: Dp, y: Dp): Modifier = layout { measurable, _ ->
    val p = measurable.measure(Constraints())
    layout(p.width, p.height) { p.place(x.roundToPx() - p.width / 2, y.roundToPx() - p.height / 2) }
}

@Composable
private fun Pill(text: String, style: TextStyle, outlined: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        style = style,
        maxLines = 1,
        modifier = modifier
            .clearAndSetSemantics {}
            .background(Color.White.copy(alpha = 0.92f), shape)
            .then(if (outlined) Modifier.border(2.dp, Palette.Outline, shape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

/** One person: the photo or the initial in two rings, 56 dp to touch whatever its size (grille). */
@Composable
private fun Medallion(person: TreePerson, name: String, radius: Dp, word: String?, outlined: Boolean, onOpen: (String) -> Unit, modifier: Modifier) {
    val spoken = listOfNotNull(name, word, lifeLine(person)).joinToString(", ")
    val openLabel = stringResource(R.string.genealogy_open_card)
    val size = radius * 2
    Box(
        modifier
            .size(TreeOfLife.TOUCH.dp)
            .clickable(onClickLabel = openLabel, role = Role.Button) { onOpen(person.id) }
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size).clearAndSetSemantics {}) {
            drawCircle(Palette.Shadow, radius = this.size.minDimension / 2, center = Offset(center.x, center.y + 2.5.dp.toPx()))
        }
        when (person) {
            is TreePerson.OfMember -> {
                val color = memberColor(person.member)
                val photo = LocalMemberPhotos.current[person.id]
                Box(
                    Modifier
                        .size(size)
                        .clearAndSetSemantics {}
                        .then(if (outlined) Modifier.border(2.dp, Palette.Outline, CircleShape) else Modifier)
                        .background(color, CircleShape)
                        .padding(2.5.dp)
                        .background(Color.White, CircleShape)
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center,
                ) {
                    if (photo != null) {
                        Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Text(
                            LocalMemberInitials.current[person.id] ?: person.firstName.take(1).uppercase(),
                            style = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = (radius.value * 0.7f).sp, color = OnMemberColor),
                        )
                    }
                }
            }
            is TreePerson.OfKin -> {
                val photo = LocalMemberPhotos.current[person.id]
                Box(
                    Modifier
                        .size(size)
                        .clearAndSetSemantics {}
                        .then(if (outlined) Modifier.border(2.dp, Palette.Outline, CircleShape) else Modifier.border(2.dp, Palette.DiscEdge, CircleShape))
                        .background(Color.White, CircleShape)
                        .padding(2.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (photo != null) {
                        // Their photo when someone added one (récit 79).
                        Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        // Else the initial, in the sepia of old photos.
                        Text(
                            person.firstName.take(1).uppercase(),
                            style = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = (radius.value * 0.8f).sp, color = Palette.KinInitial),
                        )
                    }
                }
            }
        }
    }
}

/** The disc, the ground, the roots, the trunk, the foliage, the leaves, the branches and the flowers. */
private fun DrawScope.drawScenery(life: TreeOfLife) {
    val d = density
    fun p(x: Float, y: Float) = Offset(x * d, y * d)
    val (left, top, right, bottom) = life.bounds
    val discCenter = p((left + right) / 2, (top + bottom) / 2)
    val discRadius = max(right - left, bottom - top) / 2 * d
    drawCircle(Palette.Disc, discRadius, discCenter)
    drawCircle(Palette.DiscEdge, discRadius, discCenter, style = Stroke(3 * d))
    drawCircle(
        Palette.DiscDots,
        discRadius - 8 * d,
        discCenter,
        style = Stroke(2 * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6 * d, 6 * d))),
    )

    val trunkX = life.trunk?.x ?: 0f
    val ground = TreeOfLife.GROUND_Y
    // The ground and the roots: lines from the foot of the trunk to each relative.
    drawLine(Palette.Ground, p(trunkX - 120, ground), p(trunkX + 120, ground), 6 * d)
    life.roots.forEach { root ->
        val path = Path().apply {
            moveTo(trunkX * d, ground * d)
            cubicTo(trunkX * d, (ground + 24) * d, root.x * d, (root.y - 40) * d, root.x * d, (root.y - TreeOfLife.ROOT_RADIUS) * d)
        }
        drawPath(path, Palette.Branch, style = Stroke(4 * d))
    }
    // The trunk, wider at its foot, up to the couple.
    val trunk = Path().apply {
        moveTo((trunkX - 24) * d, ground * d)
        cubicTo((trunkX - 16) * d, (ground - 50) * d, (trunkX - 14) * d, 40 * d, (trunkX - 16) * d, 0f)
        lineTo((trunkX + 16) * d, 0f)
        cubicTo((trunkX + 14) * d, 40 * d, (trunkX + 16) * d, (ground - 50) * d, (trunkX + 24) * d, ground * d)
        close()
    }
    drawPath(trunk, Palette.Branch)

    life.foliage.forEach { c ->
        drawCircle(
            Brush.radialGradient(listOf(Palette.FoliageIn, Palette.FoliageOut), center = p(c.x, c.y), radius = c.radius * d),
            radius = c.radius * d,
            center = p(c.x, c.y),
            alpha = 0.72f,
        )
    }
    val leaves = life.sprinkles(SEED, 75)
    leaves.take(45).forEachIndexed { i, (x, y) -> drawLeaf(p(x, y), i) }
    // The branches over the foliage, so one sees them (spec).
    life.branches.forEach { b ->
        val midY = (b.fromY + b.toY) / 2
        val path = Path().apply {
            moveTo(b.fromX * d, b.fromY * d)
            cubicTo(b.fromX * d, midY * d, b.toX * d, midY * d, b.toX * d, b.toY * d)
        }
        val w = b.width * d
        translate(0f, 2.5f * d) { drawPath(path, Palette.BranchShade, style = Stroke(w)) }
        drawPath(path, Palette.Branch, style = Stroke(w))
        translate(-w * 0.2f, 0f) { drawPath(path, Palette.BranchLight, style = Stroke(w * 0.28f)) }
    }
    leaves.drop(45).forEachIndexed { i, (x, y) -> drawLeaf(p(x, y), i + 45) }
    life.sprinkles(SEED + 1, 7).forEachIndexed { i, (x, y) -> drawFlower(p(x, y), Palette.Petals[i % Palette.Petals.size]) }
}

private fun DrawScope.drawLeaf(at: Offset, index: Int) {
    val w = 16 * density
    val h = 8 * density
    rotate((index * 47 % 180).toFloat(), pivot = at) {
        drawOval(Palette.Leaves[index % Palette.Leaves.size], topLeft = Offset(at.x - w / 2, at.y - h / 2), size = Size(w, h))
        drawLine(Color.White.copy(alpha = 0.7f), Offset(at.x - w * 0.35f, at.y), Offset(at.x + w * 0.35f, at.y), density)
    }
}

private fun DrawScope.drawFlower(at: Offset, petal: Color) {
    val r = 3.4f * density
    repeat(5) { k ->
        val angle = Math.toRadians(k * 72.0 - 90)
        drawCircle(petal, r, Offset(at.x + r * kotlin.math.cos(angle).toFloat(), at.y + r * kotlin.math.sin(angle).toFloat()))
    }
    drawCircle(Palette.FlowerHeart, r * 0.75f, at)
}

/** The two ways to see the tree when the arbre de vie is on. */
internal enum class TreeView { Tree, List }

/** « Arbre » and « Liste » (spec, 18/11), 56 dp like the other segmented choices of the app. */
@Composable
internal fun TreeViewSwitch(view: TreeView, onChange: (TreeView) -> Unit, modifier: Modifier = Modifier) {
    val labels = listOf(TreeView.Tree to R.string.tree_of_life_tab_tree, TreeView.List to R.string.tree_of_life_tab_list)
    SingleChoiceSegmentedButtonRow(modifier) {
        labels.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = view == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(label)) }
        }
    }
}

/**
 * TalkBack on, or the theme Contraste fort: the screen opens on « Liste » (spec, 18/11). Contraste
 * fort is the only theme without a shadow and with a border.
 */
@Composable
internal fun rememberPrefersList(): Boolean {
    val context = LocalContext.current
    val style = LocalClayStyle.current
    val talkBack = remember(context) {
        (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)
            ?.isTouchExplorationEnabled == true
    }
    return talkBack || isHighContrast(style)
}

internal fun isHighContrast(style: ClayStyle): Boolean =
    style.shadow == ClayShadow.None && style.border != null

/** The « Arbre » tab: the hint, the drawing and, for who may edit, « + » at the top right. */
@Composable
internal fun TreeOfLifeView(state: GenealogyUiState.Ready, onOpen: (String) -> Unit, onAddKin: (() -> Unit)?) {
    val outlined = isHighContrast(LocalClayStyle.current)
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.tree_of_life_hint),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Box(Modifier.fillMaxSize()) {
            TreeOfLifeDrawing(state.tree, state.words, onOpen, outlined)
            if (onAddKin != null) {
                FloatingActionButton(
                    onClick = onAddKin,
                    containerColor = Color(0xFFC9B8F5),
                    contentColor = Palette.Label,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(56.dp),
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.tree_of_life_add_kin),
                    )
                }
            }
        }
    }
}
