package fr.familia.app.feature.update

import fr.familia.app.core.data.AppConfig
import fr.familia.app.core.data.AppConfigOfflineException
import fr.familia.app.core.data.AppConfigRepository
import fr.familia.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private class FakeConfig(var result: () -> AppConfig) : AppConfigRepository {
    var fetches = 0
    override fun observeAppConfig(): Flow<AppConfig> = emptyFlow()
    override suspend fun fetchAppConfig(): AppConfig {
        fetches++
        return result()
    }
    override suspend fun setFeature(key: String, enabled: Boolean) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateCheckViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val url = "https://drive.google.com/drive/folders/example"

    @Test
    fun `the check runs as soon as the screen opens`() = runTest {
        val repo = FakeConfig { AppConfig(1, "0.1.0") }
        val vm = UpdateCheckViewModel(repo, installedVersionCode = 1, downloadUrl = url)
        assertEquals(UpdateCheckUiState.UpToDate, vm.uiState.value)
        assertEquals(1, repo.fetches)
    }

    @Test
    fun `a newer version is offered with the drive folder`() = runTest {
        val vm = UpdateCheckViewModel(FakeConfig { AppConfig(2, "0.2.0") }, 1, url)
        assertEquals(UpdateCheckUiState.Available(AppUpdate("0.2.0", url)), vm.uiState.value)
    }

    @Test
    fun `no config yet means up to date`() = runTest {
        val vm = UpdateCheckViewModel(FakeConfig { AppConfig(null, null) }, 1, url)
        assertEquals(UpdateCheckUiState.UpToDate, vm.uiState.value)
    }

    @Test
    fun `offline says so, and checking again reads the server again`() = runTest {
        val repo = FakeConfig { throw AppConfigOfflineException() }
        val vm = UpdateCheckViewModel(repo, 1, url)
        assertEquals(UpdateCheckUiState.Offline, vm.uiState.value)
        repo.result = { AppConfig(2, "0.2.0") }
        vm.check()
        assertEquals(UpdateCheckUiState.Available(AppUpdate("0.2.0", url)), vm.uiState.value)
        assertEquals(2, repo.fetches)
    }

    @Test
    fun `a server that never answers ends as offline`() = runTest {
        val repo = object : AppConfigRepository {
            override fun observeAppConfig(): Flow<AppConfig> = emptyFlow()
            override suspend fun fetchAppConfig(): AppConfig = awaitCancellation()
            override suspend fun setFeature(key: String, enabled: Boolean) = Unit
        }
        val vm = UpdateCheckViewModel(repo, 1, url)
        assertEquals(UpdateCheckUiState.Checking, vm.uiState.value)
        advanceTimeBy(8_001)
        assertEquals(UpdateCheckUiState.Offline, vm.uiState.value)
    }

    @Test
    fun `another error says the check failed`() = runTest {
        val vm = UpdateCheckViewModel(FakeConfig { throw IllegalStateException("boom") }, 1, url)
        assertEquals(UpdateCheckUiState.Failed, vm.uiState.value)
    }
}
