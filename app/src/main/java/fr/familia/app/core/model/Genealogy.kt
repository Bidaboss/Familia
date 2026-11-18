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
     * One generation below the lowest of one's parents' ones, and partners side by side (the
     * deeper of the two), like the parents of one child. Settled in a few passes: a family has few
     * generations.
     */
    val generations: Map<String, Int> = run {
        val gen = people.keys.associateWith { 0 }.toMutableMap()
        repeat(people.size + 1) {
            var changed = false
            for (link in this.links) {
                val want = when (link.kind) {
                    KinLinkKind.Parent -> gen.getValue(link.aId) + 1 to link.bId
                    KinLinkKind.Partner -> maxOf(gen.getValue(link.aId), gen.getValue(link.bId)).let { g ->
                        if (gen.getValue(link.aId) < g) g to link.aId else g to link.bId
                    }
                }
                val (value, who) = want
                if (gen.getValue(who) < value && value <= people.size) {
                    gen[who] = value
                    changed = true
                }
            }
            // The parents of one child stand on one row, like partners.
            for (child in people.keys) {
                val parents = parentsOf(child)
                if (parents.size < 2) continue
                val g = parents.maxOf { gen.getValue(it) }
                parents.filter { gen.getValue(it) < g }.forEach { gen[it] = g; changed = true }
            }
            if (!changed) return@run gen
        }
        gen
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

    private fun orderRow(ids: List<String>, place: Map<String, Double>): List<String> {
        // Two of one first name: the last name, then the id, so the order never changes.
        val sorted = ids.sortedWith(compareBy({ people.getValue(it).firstName.lowercase() }, { drawnName(it).lowercase() }, { it }))
        val placed = HashSet<String>()
        val groups = mutableListOf<List<String>>()
        for (id in sorted) {
            if (id in placed) continue
            val group = (listOf(id) + coupleOf(id).filter { it in ids && it !in placed }.sortedBy { people.getValue(it).firstName.lowercase() })
                // Within a couple, each one stands on the side of their own parents.
                .let { couple ->
                    val sides = couple.map { member -> parentsOf(member).mapNotNull { place[it] }.ifEmpty { null }?.average() }
                    if (sides.all { it != null }) couple.zip(sides).sortedBy { it.second }.map { it.first } else couple
                }
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
