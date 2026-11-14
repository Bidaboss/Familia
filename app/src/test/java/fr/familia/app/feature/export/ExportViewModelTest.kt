package fr.familia.app.feature.export

import fr.familia.app.core.data.ExportDocument
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

private class FakeExport(private val fails: Boolean = false) : ExportRepository {
    override suspend fun loadFamilyData(): FamilyData {
        if (fails) throw IllegalStateException("offline")
        return FamilyData(listOf(ExportDocument("h1", mapOf("name" to "Foyer"))), emptyList(), emptyList(), emptyList())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = { Instant.parse("2026-11-14T10:00:00Z") }

    @Test
    fun `the backup is written where the admin chose`() = runTest {
        val vm = ExportViewModel(FakeExport(), "1.0.0", now)
        var written = ""
        vm.export { written = it }
        assertEquals(ExportUiState.Done, vm.uiState.value)
        assertTrue(written.contains("\"Foyer\""))
    }

    @Test
    fun `a failure to read or to write is reported`() = runTest {
        val readFails = ExportViewModel(FakeExport(fails = true), "1.0.0", now)
        readFails.export { }
        assertEquals(ExportUiState.Failed, readFails.uiState.value)

        val writeFails = ExportViewModel(FakeExport(), "1.0.0", now)
        writeFails.export { throw IOException("disk full") }
        assertEquals(ExportUiState.Failed, writeFails.uiState.value)
    }

    @Test
    fun `closing the file picker changes nothing`() = runTest {
        val vm = ExportViewModel(FakeExport(), "1.0.0", now)
        vm.cancelled()
        assertEquals(ExportUiState.Idle, vm.uiState.value)
    }
}
