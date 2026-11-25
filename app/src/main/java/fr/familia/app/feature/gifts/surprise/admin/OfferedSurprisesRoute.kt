package fr.familia.app.feature.gifts.surprise.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Administration > « Surprises offertes récemment » (brief § 9), for the admin principal. */
@Serializable
data object OfferedSurprisesDestination

@Composable
fun OfferedSurprisesRoute(container: AppContainer?, currentMember: Member, onBack: () -> Unit) {
    val repository = container?.surpriseRepository
    val states = remember(repository, currentMember.id) {
        repository?.observeOffered(currentMember.id)
            ?.map<_, OfferedSurprisesState> { OfferedSurprisesState.Ready(it) }
            ?.catch { emit(OfferedSurprisesState.Error) }
            ?: flowOf(OfferedSurprisesState.Error)
    }
    val state by states.collectAsStateWithLifecycle(initialValue = OfferedSurprisesState.Loading)
    val scope = rememberCoroutineScope()
    var failed by rememberSaveable { mutableStateOf(false) }
    OfferedSurprisesScreen(
        state = state,
        onBack = onBack,
        onCancel = { id ->
            scope.launch {
                try {
                    repository?.askCancelOffered(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    failed = true
                }
            }
        },
        actionFailed = failed,
        onFailureShown = { failed = false },
    )
}
