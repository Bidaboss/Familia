package fr.familia.app.core.model

import kotlin.math.max
import kotlin.random.Random

/**
 * « L'arbre de vie » (spec DESIGN du 18/11, behind the switch arbreDeVie): where each person of a
 * [GenealogyTree] goes on the drawing, in dp, y going down, the top of the trunk at y = 0.
 *
 * - The trunk carries the eldest row that holds members of the app (the grandparents, today).
 * - The roots, under the ground, carry the relatives without an account of the older rows, six at
 *   most, by year of birth.
 * - The foliage carries the next rows, one level each, upward: each household (a person, or two
 *   partners) stands above its parents, the width shared between the subtrees by their number of
 *   leaves (a « tidy tree » turned upward).
 */
class TreeOfLife(tree: GenealogyTree, labelWidth: (String) -> Float = ::estimatedLabelWidth) {

    /** One medallion, or two side by side for partners; [level] 0 is the trunk, 1 the first branches. */
    data class Unit(val ids: List<String>, val level: Int, val x: Float, val y: Float, val width: Float)

    /** A branch from a household to the one above it, [width] its thickness in dp. */
    data class Branch(val fromX: Float, val fromY: Float, val toX: Float, val toY: Float, val width: Float)

    /** A relative in the roots. */
    data class Root(val id: String, val x: Float, val y: Float)

    data class Circle(val x: Float, val y: Float, val radius: Float)

    val units: List<Unit>

    /** The household on the trunk: the one of the app, whose branches carry the most members. */
    val trunk: Unit?
    val roots: List<Root>
    val branches: List<Branch>

    /** The centre of each medallion, by person id: what the screen places and what one touches. */
    val centers: Map<String, Pair<Float, Float>>

    /** The radius of [id]'s medallion: larger for the couple of the trunk, smaller in the roots. */
    fun radiusOf(id: String): Float = when {
        roots.any { it.id == id } -> ROOT_RADIUS
        trunk?.let { it.ids.size > 1 && id in it.ids } == true -> TRUNK_RADIUS
        else -> RADIUS
    }

    /** Six circles that cover the people of the foliage, with [FOLIAGE_MARGIN] around them. */
    val foliage: List<Circle>

    /** Left, top, right, bottom of everything drawn, roots and foliage included. */
    val bounds: List<Float>

    init {
        val rows = tree.rows
        val trunkIndex = rows.indexOfFirst { row -> row.any { tree.people[it] is TreePerson.OfMember } }.takeIf { it >= 0 } ?: 0
        // The households of each row from the trunk up: partners next to each other go together.
        val levels: List<List<List<String>>> = rows.drop(trunkIndex).map { row -> households(row, tree) }
        val unitOf = HashMap<String, Int>() // person -> index in [flat]
        val flat = levels.flatMapIndexed { level, households -> households.map { level to it } }
        flat.forEachIndexed { i, (_, ids) -> ids.forEach { unitOf[it] = i } }

        // How many members of the app descend from each household: where the family of the app goes.
        val memberDescendants = IntArray(flat.size) { i ->
            val seen = HashSet<String>()
            val queue = ArrayDeque(flat[i].second.flatMap { tree.childrenOf(it) })
            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()
                if (seen.add(id)) queue.addAll(tree.childrenOf(id))
            }
            seen.count { tree.people[it] is TreePerson.OfMember }
        }
        fun hasMember(i: Int) = flat[i].second.any { tree.people[it] is TreePerson.OfMember }
        val preferred = compareBy<Int>({ flat[it].first }, { hasMember(it) }, { memberDescendants[it] }, { -it })

        // Each household hangs from the household of its parents, when it stands lower. Two
        // households of parents (a son-in-law's parents added as relatives, PRODUIT-108): it hangs
        // from the one of the app, the other one gets a thin branch.
        val parentOf = IntArray(flat.size) { -1 }
        val otherParents = Array(flat.size) { emptyList<Int>() }
        flat.forEachIndexed { i, (level, ids) ->
            if (level == 0) return@forEachIndexed
            val above = ids.flatMap { tree.parentsOf(it) }.mapNotNull { unitOf[it] }.filter { flat[it].first < level }.distinct()
            parentOf[i] = above.maxWithOrNull(preferred) ?: -1
            otherParents[i] = above - parentOf[i]
        }
        val children = Array(flat.size) { mutableListOf<Int>() }
        flat.indices.forEach { i -> if (parentOf[i] >= 0) children[parentOf[i]].add(i) }

        fun ownWidth(i: Int): Float {
            val ids = flat[i].second
            // Each medallion is touched in a square of TOUCH dp, SPACING dp from the next one.
            val medallions = ids.size * TOUCH + (ids.size - 1) * SPACING
            val label = labelWidth(ids.joinToString(" et ") { tree.drawnName(it) }) + 2 * PILL_PADDING
            return max(medallions, label) + SPACING
        }

        val subtree = FloatArray(flat.size)
        fun measure(i: Int): Float {
            val inside = children[i].sumOf { measure(it).toDouble() }.toFloat()
            subtree[i] = max(ownWidth(i), inside)
            return subtree[i]
        }

        // The trunk households first, then those whose parents are not drawn: each its own subtree.
        val tops = flat.indices.filter { parentOf[it] < 0 }
        tops.forEach { measure(it) }
        val xs = FloatArray(flat.size)
        fun place(i: Int, left: Float) {
            val kids = children[i]
            if (kids.isEmpty()) {
                xs[i] = left + subtree[i] / 2
                return
            }
            val inside = kids.sumOf { subtree[it].toDouble() }.toFloat()
            var x = left + (subtree[i] - inside) / 2
            kids.forEach { k ->
                place(k, x)
                x += subtree[k]
            }
            xs[i] = (xs[kids.first()] + xs[kids.last()]) / 2
        }
        val totalWidth = tops.sumOf { subtree[it].toDouble() }.toFloat()
        var left = -totalWidth / 2
        tops.forEach { t ->
            place(t, left)
            left += subtree[t]
        }
        // The trunk stands under the household of the app, the others of its row beside it.
        val trunkAt = flat.indices.filter { flat[it].first == 0 }.maxWithOrNull(preferred)
        if (trunkAt != null) {
            val shift = xs[trunkAt]
            xs.indices.forEach { xs[it] -= shift }
        }

        units = flat.mapIndexed { i, (level, ids) -> Unit(ids, level, xs[i], -level * LEVEL_GAP, ownWidth(i) - SPACING) }
        trunk = trunkAt?.let { units[it] }
        branches = flat.indices.mapNotNull { i ->
            val p = parentOf[i]
            val from = if (p >= 0) xs[p] to -flat[p].first * LEVEL_GAP else if (flat[i].first > 0) 0f to 0f else return@mapNotNull null
            val width = BRANCH_WIDTHS[(flat[i].first - 1).coerceIn(0, BRANCH_WIDTHS.lastIndex)]
            Branch(from.first, from.second, xs[i], -flat[i].first * LEVEL_GAP, width)
        } + flat.indices.flatMap { i ->
            otherParents[i].map { p -> Branch(xs[p], -flat[p].first * LEVEL_GAP, xs[i], -flat[i].first * LEVEL_GAP, BRANCH_WIDTHS.last()) }
        }

        val older = rows.take(trunkIndex).flatten().mapNotNull { tree.people[it] as? TreePerson.OfKin }
            .sortedWith(compareBy({ it.kin.born?.take(4) ?: "9999" }, { it.firstName.lowercase() }))
            .take(MAX_ROOTS)
        roots = older.mapIndexed { i, kin -> Root(kin.id, (i - (older.size - 1) / 2f) * ROOT_GAP, ROOTS_Y) }

        val map = HashMap<String, Pair<Float, Float>>()
        units.forEach { u ->
            val span = u.ids.size * TOUCH + (u.ids.size - 1) * SPACING
            u.ids.forEachIndexed { k, id -> map[id] = (u.x - span / 2 + TOUCH / 2 + k * (TOUCH + SPACING)) to u.y }
        }
        roots.forEach { map[it.id] = it.x to it.y }
        centers = map

        val leafy = units.filter { it.level > 0 }.ifEmpty { units }
        val minX = (leafy.minOfOrNull { it.x - it.width / 2 } ?: 0f) - FOLIAGE_MARGIN
        val maxX = (leafy.maxOfOrNull { it.x + it.width / 2 } ?: 0f) + FOLIAGE_MARGIN
        val minY = (leafy.minOfOrNull { it.y } ?: 0f) - RADIUS - FOLIAGE_MARGIN
        val maxY = (leafy.maxOfOrNull { it.y } ?: 0f) + RADIUS + LABEL_DROP
        val w = maxX - minX
        val h = maxY - minY
        val r = max(w / 3.2f, h / 2.2f)
        foliage = listOf(
            Circle(minX + w * 0.2f, maxY - h * 0.3f, r * 0.85f),
            Circle(minX + w * 0.5f, maxY - h * 0.25f, r * 0.9f),
            Circle(minX + w * 0.8f, maxY - h * 0.3f, r * 0.85f),
            Circle(minX + w * 0.3f, minY + h * 0.35f, r * 0.85f),
            Circle(minX + w * 0.7f, minY + h * 0.35f, r * 0.85f),
            Circle(minX + w * 0.5f, minY + h * 0.2f, r * 0.75f),
        )
        val allX = foliage.flatMap { listOf(it.x - it.radius, it.x + it.radius) } + roots.flatMap { listOf(it.x - ROOT_GAP / 2, it.x + ROOT_GAP / 2) }
        val bottom = if (roots.isEmpty()) GROUND_Y + RADIUS else ROOTS_Y + ROOT_RADIUS + LABEL_DROP * 2
        bounds = listOf(allX.min(), foliage.minOf { it.y - it.radius }, allX.max(), bottom)
    }

    /**
     * The little leaves and flowers: drawn by chance, but the same chance for one family each time
     * ([seed], from the family's id), so the drawing never changes from one opening to the next.
     */
    fun sprinkles(seed: Int, count: Int): List<Pair<Float, Float>> {
        val random = Random(seed)
        return List(count) {
            val c = foliage[random.nextInt(foliage.size)]
            val angle = random.nextDouble(0.0, 2 * Math.PI)
            val distance = c.radius * kotlin.math.sqrt(random.nextDouble()).toFloat() * 0.92f
            (c.x + distance * kotlin.math.cos(angle).toFloat()) to (c.y + distance * kotlin.math.sin(angle).toFloat())
        }
    }

    companion object {
        const val RADIUS = 19f
        const val TRUNK_RADIUS = 21f
        const val ROOT_RADIUS = 16f
        /** The square one touches around each medallion, and the room between two (grille : 56 et 8 dp). */
        const val TOUCH = 56f
        const val SPACING = 8f
        const val PILL_PADDING = 10f
        const val LEVEL_GAP = 104f
        const val LABEL_DROP = 30f
        const val FOLIAGE_MARGIN = 24f
        const val GROUND_Y = 120f
        const val ROOTS_Y = 168f
        const val ROOT_GAP = 76f
        const val MAX_ROOTS = 6
        val BRANCH_WIDTHS = listOf(16f, 13f, 8f, 4f)

        /** About the width of a name in Nunito 800 at 12 sp; the screen passes the measured one. */
        fun estimatedLabelWidth(text: String): Float = text.length * 7f

        /** Couples side by side in a row (partners, or the parents of one child) make one household. */
        private fun households(row: List<String>, tree: GenealogyTree): List<List<String>> {
            val out = mutableListOf<MutableList<String>>()
            for (id in row) {
                val last = out.lastOrNull()
                if (last != null && last.any { it in tree.coupleOf(id) }) last.add(id) else out.add(mutableListOf(id))
            }
            return out
        }
    }
}
