package fr.familia.app.feature.crashes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.crashGroups
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** « Plantages », opened from Administration by the admin principal, or from its notification. */
@Serializable
data object CrashesDestination

@Composable
fun CrashesRoute(container: AppContainer?, onBack: () -> Unit) {
    val repository = container?.crashRepository
    val states = remember(repository) {
        repository?.observeAll()
            ?.map<_, CrashesState> { CrashesState.Ready(crashGroups(it)) }
            ?.catch { emit(CrashesState.Error) }
            ?: flowOf(CrashesState.Error)
    }
    val state by states.collectAsStateWithLifecycle(initialValue = CrashesState.Loading)
    val scope = rememberCoroutineScope()
    var failed by rememberSaveable { mutableStateOf(false) }
    CrashesScreen(
        state = state,
        onBack = onBack,
        onSettle = { signature ->
            scope.launch {
                try {
                    repository?.settle(signature)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    failed = true
                }
            }
        },
        onTestCrash = { throw CrashTestException() },
        actionFailed = failed,
        onFailureShown = { failed = false },
    )
}

/** Thrown on purpose by « Faire un essai »: its name says so in Plantages. */
class CrashTestException : RuntimeException()
