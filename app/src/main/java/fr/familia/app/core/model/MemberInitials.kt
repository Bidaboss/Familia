package fr.familia.app.core.model

import java.text.Normalizer

/**
 * The letters in each member's avatar (DESIGN-19): one letter, unless another member of the family
 * starts with the same one. Then a compound first name takes the initial of each part (« Anne-Sophie »
 * gives « AS »), and the simple first names take their first two letters (« Lé », « Lo »). A simple
 * first name keeps its single letter when the only others sharing it are compound ones.
 */
fun memberInitials(members: List<Member>): Map<String, String> {
    val named = members.filter { it.firstName.isNotBlank() }
    return named.groupBy { letterKey(it.firstName) }.flatMap { (_, group) ->
        if (group.size == 1) return@flatMap group.map { it.id to single(it.firstName) }
        val (compound, simple) = group.partition { parts(it.firstName).size > 1 }
        compound.map { it.id to parts(it.firstName).take(2).joinToString("") { part -> part.take(1).uppercase() } } +
            if (simple.size == 1) {
                listOf(simple.single().id to single(simple.single().firstName))
            } else {
                simple.map { it.id to twoLetters(it.firstName) }
            }
    }.toMap()
}

private fun parts(firstName: String): List<String> = firstName.trim().split('-', ' ').filter { it.isNotBlank() }

private fun single(firstName: String): String = firstName.trim().take(1).uppercase()

private fun twoLetters(firstName: String): String {
    val name = firstName.trim()
    return name.take(1).uppercase() + name.drop(1).take(1).lowercase()
}

/** « É » and « E » are the same letter for this purpose. */
private fun letterKey(firstName: String): String =
    Normalizer.normalize(firstName.trim().take(1), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").uppercase()
