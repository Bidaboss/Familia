package fr.familia.app.feature.export

import fr.familia.app.core.data.ExportDocument
import fr.familia.app.core.data.ExportOfflineException
import fr.familia.app.core.data.ExportRepository
import fr.familia.app.core.data.FamilyData
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

private class FakeExport(private val failure: Exception? = null) : ExportRepository {
    var loads = 0
    override suspend fun loadFamilyData(): FamilyData {
        loads++
        failure?.let { throw it }
        return FamilyData(listOf(ExportDocument("h1", mapOf("name" to "Foyer"))), emptyList(), emptyList(), emptyList())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Midday UTC, so the file name is the same day whatever the time zone of the test machine.
    private val now = { Instant.parse("2026-11-14T12:00:00Z") }

    @Test
    fun `the data is read first, then written where the admin chose`() = runTest {
        val vm = ExportViewModel(FakeExport(), "1.0.0", now)
        vm.prepare()
        assertEquals(ExportUiState.AwaitingDestination("familia-sauvegarde-2026-11-14.json"), vm.uiState.value)
        var written = ""
        vm.save { written = it }
        assertEquals(ExportUiState.Done, vm.uiState.value)
        assertTrue(written.contains("\"Foyer\""))
    }

    @Test
    fun `offline, no backup is made and the admin is told to connect`() = runTest {
        val vm = ExportViewModel(FakeExport(ExportOfflineException(IllegalStateException("unavailable"))), "1.0.0", now)
        vm.prepare()
        assertEquals(ExportUiState.Offline, vm.uiState.value)
        // Nothing to save: the file picker is never opened.
        vm.save { error("must not write") }
        assertEquals(ExportUiState.Offline, vm.uiState.value)
    }

    @Test
    fun `a failure to read or to write is reported`() = runTest {
        val readFails = ExportViewModel(FakeExport(IllegalStateException("denied")), "1.0.0", now)
        readFails.prepare()
        assertEquals(ExportUiState.Failed, readFails.uiState.value)

        val writeFails = ExportViewModel(FakeExport(), "1.0.0", now)
        writeFails.prepare()
        writeFails.save { throw IOException("disk full") }
        assertEquals(ExportUiState.Failed, writeFails.uiState.value)
    }

    @Test
    fun `closing the file picker drops the prepared file`() = runTest {
        val repo = FakeExport()
        val vm = ExportViewModel(repo, "1.0.0", now)
        vm.prepare()
        vm.cancelled()
        assertEquals(ExportUiState.Idle, vm.uiState.value)
        vm.save { error("must not write") }
        assertEquals(ExportUiState.Idle, vm.uiState.value)
    }

    @Test
    fun `a second tap while preparing reads the data only once`() = runTest {
        val repo = FakeExport()
        val vm = ExportViewModel(repo, "1.0.0", now)
        vm.prepare()
        vm.prepare()
        assertEquals(1, repo.loads)
    }
}
