package fr.familia.app.feature.gifts

import fr.familia.app.feature.gifts.item.GiftItemForm
import fr.familia.app.feature.gifts.item.GiftItemFormError
import fr.familia.app.feature.gifts.item.onlineWhere
import fr.familia.app.feature.gifts.item.validateItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** « Où trouver le cadeau ? » (correctif E6): free text, 120 characters, « En ligne · … » from a link. */
class WhereToFindTest {

    @Test
    fun `a pasted link proposes « En ligne » and its site`() {
        assertEquals("En ligne · boutique-exemple.fr", onlineWhere("https://www.boutique-exemple.fr/casque"))
        assertEquals("En ligne · boutique-exemple.fr", onlineWhere("boutique-exemple.fr/casque"))
        assertNull(onlineWhere("pas un lien"))
    }

    @Test
    fun `where to find it holds 120 characters at most`() {
        val form = GiftItemForm(title = "Un casque")
        assertFalse(GiftItemFormError.WhereTooLong in validateItem(form.copy(whereToFind = "x".repeat(120))))
        assertTrue(GiftItemFormError.WhereTooLong in validateItem(form.copy(whereToFind = "x".repeat(121))))
    }
}
