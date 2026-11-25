package fr.familia.app.core.model

/**
 * A relative without an account (PRD, récit 40): a great-grandparent, someone who passed away, or a
 * living parent who is not in the app. They receive nothing and are not one of the members.
 * [born] and [died] are a year (AAAA) or a day (AAAA-MM-JJ), as known.
 */
data class Kin(
    val id: String,
    val firstName: String,
    val lastName: String? = null,
    val living: Boolean,
    val born: String? = null,
    val died: String? = null,
    val note: String? = null,
    /** The household that added them: its chefs edit them. */
    val householdId: String,
) {
    companion object {
        const val MAX_NAME = 40
        const val MAX_NOTE = 500
        private val DATE = Regex("^[0-9]{4}(-[0-9]{2}-[0-9]{2})?$")

        fun isDate(value: String): Boolean = DATE.matches(value)

        private val FRENCH_DAY = Regex("^([0-9]{1,2})/([0-9]{1,2})/([0-9]{4})$")

        /**
         * What one types for an old date, as stored: « 1921 », « 04/03/1921 » or « 1921-03-04 ».
         * Blank gives "" (no date); anything else, or an impossible day, null.
         */
        fun parseDate(input: String): String? {
            val text = input.trim()
            if (text.isEmpty()) return ""
            if (Regex("^[0-9]{4}$").matches(text)) return text
            val iso = FRENCH_DAY.matchEntire(text)?.destructured?.let { (d, m, y) -> "%s-%02d-%02d".format(y, m.toInt(), d.toInt()) } ?: text
            if (!DATE.matches(iso)) return null
            return runCatching { java.time.LocalDate.parse(iso) }.getOrNull()?.let { iso }
        }
    }
}

/** How two people of the tree are joined. */
enum class KinLinkKind(val key: String) {
    /** [KinLink.aId] is a parent of [KinLink.bId]. */
    Parent("parent"),

    /** Two partners; written in alphabetical order. */
    Partner("partner"),
    ;

    companion object {
        fun of(key: String?): KinLinkKind? = entries.firstOrNull { it.key == key }
    }
}

/** A link between two people of the tree, members or [Kin]. Its id says what it is, so it is never there twice. */
data class KinLink(val kind: KinLinkKind, val aId: String, val bId: String) {
    val id: String get() = "${kind.key}_${aId}_$bId"

    companion object {
        fun parent(parentId: String, childId: String) = KinLink(KinLinkKind.Parent, parentId, childId)

        fun partners(one: String, other: String): KinLink =
            if (one < other) KinLink(KinLinkKind.Partner, one, other) else KinLink(KinLinkKind.Partner, other, one)
    }
}

/** Someone in the tree: a member or a relative without an account. */
sealed interface TreePerson {
    val id: String
    val firstName: String
    val householdId: String

    data class OfMember(val member: Member) : TreePerson {
        override val id get() = member.id
        override val firstName get() = member.firstName
        override val householdId get() = member.householdId
    }

    data class OfKin(val kin: Kin) : TreePerson {
        override val id get() = kin.id
        override val firstName get() = kin.firstName
        override val householdId get() = kin.householdId
    }
}

/**
 * The family tree, ready to draw (PRD, récit 40): everyone with a generation, 0 for the eldest.
 * Links to someone no longer there are left out, and a loop of parents (a mistake) never hangs it.
 */
class GenealogyTree(members: List<Member>, kin: List<Kin>, links: List<KinLink>) {
    val people: Map<String, TreePerson> =
        (members.map { TreePerson.OfMember(it) } + kin.map { TreePerson.OfKin(it) }).associateBy { it.id }

    val links: List<KinLink> = links.filter { it.aId in people && it.bId in people && it.aId != it.bId }.distinctBy { it.id }

    fun parentsOf(id: String): List<String> = links.filter { it.kind == KinLinkKind.Parent && it.bId == id }.map { it.aId }

    fun childrenOf(id: String): List<String> = links.filter { it.kind == KinLinkKind.Parent && it.aId == id }.map { it.bId }

    fun partnersOf(id: String): List<String> =
        links.filter { it.kind == KinLinkKind.Partner && (it.aId == id || it.bId == id) }.map { if (it.aId == id) it.bId else it.aId }

    /**
     * The partners of [id]; without any, the other parents of their children who have no partner
     * either (retour du 18/11: two couples of grandparents were split, the grandfathers together).
     * A shared custody stays apart: the other parent never joins a couple that has a « partner »
     * link.
     */
    fun coupleOf(id: String): List<String> {
        val partners = partnersOf(id)
        if (partners.isNotEmpty()) return partners
        return childrenOf(id).flatMap { parentsOf(it) }.filter { it != id && partnersOf(it).isEmpty() }.distinct()
    }

    /**
     * The name on the drawing: the first name, and the last name too when it is noted and someone
     * else in the tree has the same first name (retour du 18/11: two grandfathers of one name).
     */
    fun drawnName(id: String): String {
        val person = people[id] ?: return ""
        val first = person.firstName.trim()
        val last = (person as? TreePerson.OfKin)?.kin?.lastName?.trim()?.takeIf { it.isNotEmpty() } ?: return person.firstName
        val twin = people.values.any { it.id != id && it.firstName.trim().equals(first, ignoreCase = true) }
        return if (twin) "$first $last" else person.firstName
    }

    /**
     * One row per generation (brief Famille-recettes, règle 13): a child one below their parents,
     * partners and brothers and sisters on one row, settled from the links between members first,
     * so relatives added on one side (the grandparents of a son-in-law) never push a couple and
     * their children a row below their cousins. Someone with no link to the family (an unlinked
     * child) stands by their household: a child one row below its adults, an adult beside them.
     * A loop of parents, a mistake, keeps the first row found. 0 is the eldest row.
     */
    val generations: Map<String, Int> = run {
        val steps = people.keys.associateWith { mutableListOf<Pair<String, Int>>() }
        for (link in this.links) {
            val down = if (link.kind == KinLinkKind.Parent) 1 else 0
            steps.getValue(link.aId).add(link.bId to down)
            steps.getValue(link.bId).add(link.aId to -down)
        }
        fun isMember(id: String) = people[id] is TreePerson.OfMember
        val ordered = people.keys.sortedWith(compareBy({ !isMember(it) }, { it }))
        // Each group of linked people, walked from a member: the links between members are
        // followed first, those through a relative only after them (first row found wins).
        val gen = HashMap<String, Int>()
        val groups = mutableListOf<List<String>>()
        for (start in ordered) if (start !in gen) {
            val group = mutableListOf<String>()
            val queue = ArrayDeque(listOf(start to 0))
            while (queue.isNotEmpty()) {
                val (id, g) = queue.removeFirst()
                if (id in gen) continue
                gen[id] = g
                group.add(id)
                for ((next, step) in steps.getValue(id)) {
                    if (next in gen) continue
                    if (isMember(id) && isMember(next)) queue.addFirst(next to g + step) else queue.addLast(next to g + step)
                }
            }
            groups.add(group)
        }
        val main = groups.maxByOrNull { group -> group.count(::isMember) } ?: emptyList()
        val trunk = main.filter(::isMember).minOfOrNull { gen.getValue(it) } ?: 0
        fun isChild(id: String) = (people[id] as? TreePerson.OfMember)?.member?.profile.let { it != null && it != MemberProfile.Adult }
        for (group in groups) {
            if (group === main) continue
            val shift = group.filter(::isMember).firstNotNullOfOrNull { id ->
                main.filter { isMember(it) && people.getValue(it).householdId == people.getValue(id).householdId }
                    .sortedWith(compareBy({ isChild(it) }, { it }))
                    .firstOrNull()
                    ?.let { other ->
                        val below = (if (isChild(id)) 1 else 0) - (if (isChild(other)) 1 else 0)
                        gen.getValue(other) + below - gen.getValue(id)
                    }
            } ?: ((if (group.any(::isMember)) trunk else 0) - group.minOf { gen.getValue(it) })
            group.forEach { gen[it] = gen.getValue(it) + shift }
        }
        val eldest = gen.values.minOrNull() ?: 0
        people.keys.associateWith { gen.getValue(it) - eldest }
    }

    /**
     * By generation, eldest first. Within one, couples (partners, or the parents of one child) next
     * to each other, each on the side of their own parents, and brothers and
     * sisters together under their parents (retour du 17/11: the children of two sisters were
     * mixed), then by first name.
     */
    val rows: List<List<String>> = run {
        val byGeneration = generations.entries.groupBy({ it.value }, { it.key }).toSortedMap().values
        // Where each one stands in their row, from 0 to 1, so rows of different lengths compare.
        val place = HashMap<String, Double>()
        byGeneration.map { ids ->
            val row = orderRow(ids, place)
            row.forEachIndexed { i, id -> place[id] = (i + 0.5) / row.size }
            row
        }
    }

    /**
     * Where each one is drawn, in columns of one card (retour du 18/11: the children stood left of
     * their parents). Each family of brothers and sisters, with their partners, is centered under
     * the middle of its parents, pushed right only as far as needed not to cover the one before.
     * The leftmost card is at column 0.
     */
    val columns: Map<String, Double> by lazy { treeColumns(rows, ::parentsOf) }

    /** How many columns the widest row needs. */
    val width: Double by lazy { (columns.values.maxOrNull() ?: -1.0) + 1.0 }

    /**
     * [couple] and, next to them, the other parent of a child of theirs who is in no couple of
     * [ids] (retour du 20/11: a shared custody, the children stood far from their mother, joined
     * by a long bar). The other parent goes on the far side of the one they share the children
     * with, so the children stand between their two parents: « Partner, Parent, Other parent ».
     */
    private fun withCoParents(couple: List<String>, ids: List<String>, placed: Set<String>): List<String> {
        val row = couple.toMutableList()
        for (parent in couple) {
            val others = childrenOf(parent).flatMap { parentsOf(it) }
                .filter { it != parent && it in ids && it !in placed && it !in row && partnersOf(it).none { p -> p in ids } }
                .distinct()
                .sortedBy { people.getValue(it).firstName.lowercase() }
            if (others.isEmpty()) continue
            val at = row.indexOf(parent)
            // The far side: after the parent when their partner stands before, before when after.
            if (at == row.lastIndex || at == 0 && row.size == 1) row.addAll(at + 1, others) else row.addAll(at, others)
        }
        return row
    }

    private fun orderRow(ids: List<String>, place: Map<String, Double>): List<String> {
        // Two of one first name: the last name, then the id, so the order never changes.
        val sorted = ids.sortedWith(compareBy({ people.getValue(it).firstName.lowercase() }, { drawnName(it).lowercase() }, { it }))
        val placed = HashSet<String>()
        val groups = mutableListOf<List<String>>()
        // The other parent of a couple's children waits for that couple, to stand next to them.
        fun waits(id: String) = partnersOf(id).none { it in ids } &&
            childrenOf(id).flatMap { parentsOf(it) }.any { it != id && it in ids && partnersOf(it).any { p -> p in ids } }
        for (id in sorted.filterNot(::waits) + sorted.filter(::waits)) {
            if (id in placed) continue
            val couple = (listOf(id) + coupleOf(id).filter { it in ids && it !in placed }.sortedBy { people.getValue(it).firstName.lowercase() })
                // Within a couple, each one stands on the side of their own parents.
                .let { couple ->
                    val sides = couple.map { member -> parentsOf(member).mapNotNull { place[it] }.ifEmpty { null }?.average() }
                    if (sides.all { it != null }) couple.zip(sides).sortedBy { it.second }.map { it.first } else couple
                }
            val group = withCoParents(couple, ids, placed)
            placed += group
            groups += group
        }
        // Under the middle of their parents; those without parents drawn keep their place by name.
        return groups.withIndex().sortedBy { (index, group) ->
            val above = group.flatMap { parentsOf(it) }.mapNotNull { place[it] }
            if (above.isEmpty()) (index + 0.5) / groups.size else above.average()
        }.flatMap { it.value }
    }
}

/**
 * The columns of [rows] (see GenealogyTree.columns). A person whose parents are not drawn (a
 * partner who married in) stays with the family next to them.
 */
fun treeColumns(rows: List<List<String>>, parentsOf: (String) -> List<String>): Map<String, Double> {
    val column = HashMap<String, Double>()
    for (row in rows) {
        // Families of one row, in its order: the same parents drawn, and the partners with them.
        val blocks = mutableListOf<Pair<MutableList<String>, Set<String>?>>()
        for (id in row) {
            val above = parentsOf(id).filter { it in column }.toSet().ifEmpty { null }
            val last = blocks.lastOrNull()
            when {
                last == null -> blocks += mutableListOf(id) to above
                above == null -> last.first += id
                last.second == null -> blocks[blocks.lastIndex] = (last.first.apply { add(id) }) to above
                last.second == above -> last.first += id
                else -> blocks += mutableListOf(id) to above
            }
        }
        var cursor = Double.NEGATIVE_INFINITY
        for ((ids, above) in blocks) {
            val wanted = above?.let { parents -> parents.map { column.getValue(it) }.average() - (ids.size - 1) / 2.0 }
            val start = when {
                cursor == Double.NEGATIVE_INFINITY -> wanted ?: 0.0
                wanted == null -> cursor
                else -> maxOf(wanted, cursor)
            }
            ids.forEachIndexed { i, id -> column[id] = start + i }
            cursor = start + ids.size
        }
    }
    val left = column.values.minOrNull() ?: 0.0
    return column.mapValues { it.value - left }
}

