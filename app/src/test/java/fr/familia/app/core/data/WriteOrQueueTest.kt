package fr.familia.app.core.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A write offline, refused, or refused later (PRODUIT, 17/11), on the virtual clock of the test. */
@OptIn(ExperimentalCoroutinesApi::class)
class WriteOrQueueTest {

    @Test
    fun `a write done at once is true, one refused at once is false`() = runTest {
        var late = 0
        assertTrue(writeOrQueue(backgroundScope, onLateFailure = { late++ }) { })
        assertFalse(writeOrQueue(backgroundScope, onLateFailure = { late++ }) { error("refused") })
        runCurrent()
        assertEquals(0, late)
    }

    @Test
    fun `offline the button comes back after 4 s, the write still waiting for the network`() = runTest {
        val answer = async { writeOrQueue(backgroundScope, onLateFailure = {}) { awaitCancellation() } }
        advanceTimeBy(WRITE_WAIT_MILLIS - 1)
        assertFalse(answer.isCompleted)
        advanceTimeBy(2)
        assertTrue(answer.await())
    }

    @Test
    fun `a refusal once the network is back is said, never lost`() = runTest {
        val server = CompletableDeferred<Unit>()
        var late = 0
        val answer = async { writeOrQueue(backgroundScope, onLateFailure = { late++ }) { server.await() } }
        advanceTimeBy(WRITE_WAIT_MILLIS + 1)
        assertTrue(answer.await())
        assertEquals(0, late)
        server.completeExceptionally(IllegalStateException("PERMISSION_DENIED"))
        runCurrent()
        assertEquals(1, late)
    }

    @Test
    fun `a write that reaches the server later says nothing more`() = runTest {
        val server = CompletableDeferred<Unit>()
        var late = 0
        val answer = async { writeOrQueue(backgroundScope, onLateFailure = { late++ }) { server.await() } }
        advanceTimeBy(WRITE_WAIT_MILLIS + 1)
        assertTrue(answer.await())
        server.complete(Unit)
        runCurrent()
        assertEquals(0, late)
    }
}
