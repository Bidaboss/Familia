package fr.familia.app.core.place

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** « Autre photo » (demande de l'admin du 25/11 soir): each tap gives the next photo around the destination. */
class CoverChoicesTest {
    private val pages = """
        {"query":{"pages":[
          {"pageid":3,"title":"Zoo de La Samoure","index":2,"thumbnail":{"source":"https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Zoo.jpg/1080px-Zoo.jpg"},"original":{"source":"https://upload.wikimedia.org/wikipedia/commons/a/ab/Zoo.jpg"}},
          {"pageid":1,"title":"Les Camilles","index":1,"thumbnail":{"source":"https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/Blason.svg/1080px-Blason.svg.png"},"original":{"source":"https://upload.wikimedia.org/wikipedia/commons/c/cd/Blason.svg"}},
          {"pageid":2,"title":"Phare de la Noé","index":3,"thumbnail":{"source":"https://upload.wikimedia.org/wikipedia/commons/thumb/e/ef/Phare.jpg/1080px-Phare.jpg"},"original":{"source":"https://upload.wikimedia.org/wikipedia/commons/e/ef/Phare.jpg"}},
          {"pageid":4,"title":"Plage de la Noé","index":4,"thumbnail":{"source":"https://upload.wikimedia.org/wikipedia/commons/thumb/e/ef/Phare.jpg/1080px-Phare.jpg"},"original":{"source":"https://upload.wikimedia.org/wikipedia/commons/e/ef/Phare.jpg"}}
        ]}}
    """.trimIndent()

    @Test
    fun everyRealPhotoAroundIsOfferedOnceNearestFirst() {
        // The coat of arms is not a photo; the lighthouse of two pages comes once.
        assertEquals(listOf("Zoo.jpg", "Phare.jpg"), parseNearbyImages(pages).map { it.file })
        assertEquals(emptyList<SummaryImage>(), parseNearbyImages("pas du json"))
    }

    @Test
    fun theNextPhotoComesAfterTheCurrentOneAndGoesRound() {
        val images = parseNearbyImages(pages)
        val (zoo, phare) = images
        // The photo of the page of the place itself is not among them: the nearest comes first.
        assertEquals(zoo, nextChoice(images, "https://upload.wikimedia.org/autre.jpg"))
        assertEquals(zoo, nextChoice(images, null))
        assertEquals(phare, nextChoice(images, zoo.url))
        assertEquals(zoo, nextChoice(images, phare.url))
        assertNull(nextChoice(listOf(zoo), zoo.url))
        assertNull(nextChoice(emptyList(), null))
    }

    @Test
    fun theChosenPhotoComesWithItsCreditAndOnlyThePointIsSent() = runBlocking {
        val asked = mutableListOf<String>()
        val credit = """{"query":{"pages":[{"imageinfo":[{"extmetadata":{"Artist":{"value":"Camille"},"LicenseShortName":{"value":"CC BY-SA 4.0"}}}]}]}}"""
        val choices = CoverChoices(read = { address ->
            asked += address
            if ("geosearch" in address) pages else credit
        })
        val first = choices.next(45.123456, 1.987654, current = null)
        assertEquals("Zoo.jpg", fileName(first!!.url))
        assertEquals("Camille, CC BY-SA 4.0, Wikimedia Commons", first.credit)
        assertEquals("Phare.jpg", fileName(choices.next(45.123456, 1.987654, current = first.url)!!.url))
        // The pages around are asked once for the session, with the point rounded.
        assertEquals(1, asked.count { "geosearch" in it })
        assertEquals(true, asked.first().contains("45.123%7C1.988"))
    }

    @Test
    fun withoutNetworkThereIsNoOtherPhoto() = runBlocking {
        assertNull(CoverChoices(read = { null }).next(45.0, 1.0, current = null))
    }
}
