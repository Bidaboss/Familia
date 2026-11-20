package fr.familia.app.feature.genealogy.poster

import fr.familia.app.core.model.GenealogyTree
import fr.familia.app.core.model.KinLink
import fr.familia.app.core.model.KinLinkKind
import fr.familia.app.core.model.MemberProfile
import fr.familia.app.core.model.TreePerson
import fr.familia.app.core.share.photoAllowed
import kotlinx.serialization.Serializable
import java.util.Locale

/*
 * L'affiche de l'arbre (PRD rév. 243, récit 53), behind Feature.TreePoster: the member picks a
 * title, where the tree starts and whether to show photos; the phone makes one landscape PDF page
 * (A4 or A3) in the Argile style, with the engine of the recipe book, and the share sheet opens.
 */

@Serializable
data object TreePosterDestination

/** The size of the sheet, landscape, in PDF points (1/72 inch). */
enum class PosterFormat(val width: Int, val height: Int) {
    A4(842, 595),
    A3(1191, 842),
}

/** Where the poster starts: the whole family, or one person with their partners and descendants. */
sealed interface PosterStart {
    data object Everyone : PosterStart

    data class DescendantsOf(val personId: String) : PosterStart
}

/**
 * Someone on the poster: a first name, a birth year at most (never the full date), and the member
 * whose profile photo is drawn, or null for the initial.
 */
data class PosterPerson(val id: String, val firstName: String, val year: Int?, val photoOf: String?) {
    val initial: String get() = firstName.trim().take(1).uppercase(Locale.FRENCH)
}

/** What the printer needs: rows by generation, eldest first, and the links between the people drawn. */
data class PosterContent(
    val title: String,
    val year: Int,
    val format: PosterFormat,
    val rows: List<List<PosterPerson>>,
    val links: List<KinLink>,
) {
    val photoCount: Int get() = rows.sumOf { row -> row.count { it.photoOf != null } }
}

/** The people of [start]: everyone, or the person, their descendants and the partners of all of them. */
fun posterIds(tree: GenealogyTree, start: PosterStart): Set<String> = when (start) {
    PosterStart.Everyone -> tree.people.keys
    is PosterStart.DescendantsOf -> {
        if (start.personId !in tree.people) {
            emptySet()
        } else {
            val line = LinkedHashSet<String>()
            val queue = ArrayDeque(listOf(start.personId))
            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()
                if (line.add(id)) queue.addAll(tree.childrenOf(id))
            }
            line + line.flatMap { tree.partnersOf(it) }
        }
    }
}

/**
 * The tree cut down to [ids], so its generations start again from the eldest kept, and its rows put
 * each family under its parents: a row follows the places of the parents in the rows above.
 */
fun posterRows(tree: GenealogyTree, ids: Set<String>): List<List<String>> {
    val people = tree.people.filterKeys { it in ids }.values
    val cut = GenealogyTree(
        members = people.filterIsInstance<TreePerson.OfMember>().map { it.member },
        kin = people.filterIsInstance<TreePerson.OfKin>().map { it.kin },
        links = tree.links,
    )
    val place = HashMap<String, Float>()
    return cut.rows.map { row ->
        // Couples stay side by side: a group is a person and the partners (or co-parents) just after them.
        val groups = mutableListOf<MutableList<String>>()
        row.forEach { id ->
            val last = groups.lastOrNull()
            if (last != null && last.size < 2 && last.any { it in cut.coupleOf(id) }) last += id else groups += mutableListOf(id)
        }
        val ordered = groups.withIndex().sortedBy { (index, group) ->
            val above = group.flatMap { cut.parentsOf(it) }.mapNotNull { place[it] }
            if (above.isEmpty()) (index + 0.5f) / groups.size else above.average().toFloat()
        }.flatMap { it.value }
        ordered.forEachIndexed { i, id -> place[id] = (i + 0.5f) / ordered.size }
        ordered
    }
}

/**
 * How [person] is drawn for [makerId]. Carte EXPERT-2 and récit 47: a child's photo leaves the app
 * only when the maker is one of that child's adults; otherwise the initial. A relative without an
 * account shows the photo added in the tree, if any (récit 79). Only the year of birth is kept.
 */
fun posterPerson(
    person: TreePerson,
    makerId: String,
    withPhotos: Boolean,
    hasPhoto: (String) -> Boolean,
    name: String = person.firstName,
): PosterPerson = when (person) {
    is TreePerson.OfMember -> {
        val member = person.member
        val allowed = member.profile == MemberProfile.Adult || photoAllowed(makerId, listOf(member.parentIds))
        PosterPerson(member.id, name, member.birthDate?.year, member.id.takeIf { withPhotos && allowed && hasPhoto(it) })
    }
    // A relative's photo (récit 79) was added by an adult who manages their household.
    is TreePerson.OfKin -> PosterPerson(person.kin.id, name, person.kin.born?.take(4)?.toIntOrNull(), person.kin.id.takeIf { withPhotos && hasPhoto(it) })
}

/** Everything the poster shows, ready to print. */
fun posterContent(
    tree: GenealogyTree,
    start: PosterStart,
    makerId: String,
    withPhotos: Boolean,
    hasPhoto: (String) -> Boolean,
    title: String,
    year: Int,
    format: PosterFormat,
): PosterContent {
    val ids = posterIds(tree, start)
    val rows = posterRows(tree, ids).map { row -> row.map { posterPerson(tree.people.getValue(it), makerId, withPhotos, hasPhoto, tree.drawnName(it)) } }
    val links = tree.links.filter { it.aId in ids && it.bId in ids }
    return PosterContent(title, year, format, rows, links)
}

/** The parents of [id] drawn on the poster, for the lines down to them. */
fun PosterContent.parentsOf(id: String): List<String> = links.filter { it.kind == KinLinkKind.Parent && it.bId == id }.map { it.aId }

/** « arbre-de-la-famille-2026.pdf »: a name every phone and every printing shop accepts. */
fun posterFileName(year: Int): String = "arbre-de-la-famille-$year.pdf"

/** The title of the poster, 60 characters at most so it fits on one line on A4. */
const val POSTER_TITLE_MAX = 60
