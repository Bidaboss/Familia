package fr.familia.app.core.ui

import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmationTest {

    @Test
    fun `an accepted write says it is saved`() = runTest {
        val sent = sendWrite(5_000) {}
        assertEquals(WriteSent.Accepted, sent)
        assertEquals(Confirmation.Saved, sent?.or(Confirmation.Saved))
    }

    @Test
    fun `a write still pending after the timeout says it waits for the network`() = runTest {
        val sent = sendWrite(5_000) { awaitCancellation() }
        assertEquals(WriteSent.Queued, sent)
        assertEquals(Confirmation.Queued, sent?.or(Confirmation.Deleted))
    }

    @Test
    fun `a refused write gives nothing to confirm`() = runTest {
        assertNull(sendWrite(5_000) { throw IOException("denied") })
    }
}
