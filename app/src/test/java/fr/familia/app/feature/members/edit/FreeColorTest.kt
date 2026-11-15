package fr.familia.app.feature.members.edit

import fr.familia.app.core.theme.MemberColors
import fr.familia.app.feature.events.TestFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FreeColorTest {

    @Test
    fun `a new member gets the first color nobody has`() {
        val family = TestFamily.family.copy(
            members = TestFamily.family.members.mapIndexed { i, m -> if (i < 2) m.copy(colorIndex = i) else m },
        )
        assertEquals(2, freeColor(family))
    }

    @Test
    fun `when every color is taken, none is forced`() {
        val many = MemberColors.indices.map { TestFamily.adult.copy(id = "m$it", colorIndex = it) }
        assertNull(freeColor(TestFamily.family.copy(members = many)))
    }
}
