package fr.familia.app.core.data

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirestoreCacheResetTest {

    private fun prefs() = RuntimeEnvironment.getApplication().getSharedPreferences("local-traces", Context.MODE_PRIVATE)

    @Test
    fun `asked on a change of member, kept until the cache is empty, then forgotten`() {
        assertFalse(FirestoreCacheReset(prefs()).pending())
        FirestoreCacheReset(prefs()).ask()
        // The next start of the app reads it again from the preferences.
        val next = FirestoreCacheReset(prefs())
        assertTrue(next.pending())
        next.done()
        assertFalse(FirestoreCacheReset(prefs()).pending())
    }

    @Test
    fun `the same member again asks for nothing, another one asks once`() {
        var owner: String? = null
        val reset = FirestoreCacheReset(prefs())
        val traces = LocalTraces(
            object : TraceOwner {
                override fun get(): String? = owner
                override fun put(memberId: String) {
                    owner = memberId
                }
            },
        ) { reset.ask() }
        traces.signedIn("adult1")
        traces.signedIn("adult1")
        assertFalse(reset.pending())
        traces.signedIn("adult2")
        assertTrue(reset.pending())
    }
}
