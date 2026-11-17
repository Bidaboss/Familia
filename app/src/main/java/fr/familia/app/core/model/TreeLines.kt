package fr.familia.app.core.model

/** Where a person landed on the drawing, in any unit, y going down. */
data class TreeBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2
}

/** A straight piece of line to draw. */
data class TreeSegment(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

/** Parents and the children they share: one drop, one bar, one line to each child. */
data class TreeFamily(val parents: List<String>, val children: List<String>)

/** The children grouped by who their parents are, so two families never share a bar. */
fun treeFamilies(links: List<KinLink>): List<TreeFamily> =
    links.filter { it.kind == KinLinkKind.Parent }
        .groupBy({ it.bId }, { it.aId })
        .entries
        .groupBy({ it.value.distinct().sorted() }, { it.key })
        .map { (parents, children) -> TreeFamily(parents, children) }

/**
 * The lines from parents to children (retour du 17/11, « les traits de lien ne sont pas bons »).
 * Two partners send one line down from the middle of the line between them; a single parent from
 * under their card. Each family gets its own bar, at its own height in the gap between the rows,
 * so one can always tell whose children are whose.
 */
fun treeParentLines(links: List<KinLink>, boxes: Map<String, TreeBox>): List<TreeSegment> {
    val partners = links.filter { it.kind == KinLinkKind.Partner }.map { setOf(it.aId, it.bId) }.toSet()

    class Drop(val x: Float, val y: Float, val below: Float, val children: List<TreeBox>)

    val drops = treeFamilies(links).mapNotNull { family ->
        val parents = family.parents.mapNotNull { boxes[it] }
        val children = family.children.mapNotNull { boxes[it] }
        if (parents.isEmpty() || children.isEmpty()) return@mapNotNull null
        val below = parents.maxOf { it.bottom }
        val couple = parents.size == 2 && family.parents.toSet() in partners && parents[0].centerY == parents[1].centerY
        if (couple) {
            val (left, right) = parents.sortedBy { it.centerX }
            Drop((left.right + right.left) / 2, left.centerY, below, children)
        } else {
            Drop(parents.map { it.centerX }.average().toFloat(), below, below, children)
        }
    }

    val lines = mutableListOf<TreeSegment>()
    // Families whose parents stand on the same row share the gap below it: each takes a lane.
    drops.groupBy { it.below }.values.forEach { row ->
        val sorted = row.sortedBy { it.x }
        sorted.forEachIndexed { i, drop ->
            val gapBottom = drop.children.minOf { it.top }
            val barY = drop.below + (gapBottom - drop.below) * (i + 1) / (sorted.size + 1)
            lines += TreeSegment(drop.x, drop.y, drop.x, barY)
            val xs = drop.children.map { it.centerX } + drop.x
            lines += TreeSegment(xs.min(), barY, xs.max(), barY)
            drop.children.forEach { child -> lines += TreeSegment(child.centerX, barY, child.centerX, child.top) }
        }
    }
    return lines
}
