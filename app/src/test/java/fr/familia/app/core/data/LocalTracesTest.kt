package fr.familia.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalTracesTest {

    private class Owner(var id: String? = null) : TraceOwner {
        override fun get(): String? = id
        override fun put(memberId: String) {
            id = memberId
        }
    }

    @Test
    fun `the first member and the same member again keep everything, another one wipes first`() {
        val owner = Owner()
        var wipes = 0
        val traces = LocalTraces(owner) { wipes++ }
        traces.signedIn("adult1")
        traces.signedIn("adult1")
        assertEquals(0, wipes)
        traces.signedIn("adult2")
        assertEquals(1, wipes)
        assertEquals("adult2", owner.id)
        traces.signedIn("adult2")
        assertEquals(1, wipes)
    }
}
