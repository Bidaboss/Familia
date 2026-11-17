package fr.familia.app.feature.feedback

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.BuildConfig
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Feedback
import fr.familia.app.core.model.FeedbackContext
import fr.familia.app.core.model.FeedbackOrigin
import fr.familia.app.core.model.Member
import fr.familia.app.core.photo.LocalPhotoStorage
import fr.familia.app.core.photo.PhotoEncoder
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** « Une idée ? Un souci ? », opened from the Famille tab or from the Aide (PRD, récit 6 sexies). */
@Serializable
data class FeedbackFormDestination(val origin: String = FeedbackOrigin.Family.key)

/** « Retours », opened from Administration by the admin principal. */
@Serializable
data object FeedbackListDestination

/** « Mes demandes »: what one sent, and the answers. */
@Serializable
data object MyFeedbackDestination

/** What is added on its own to a request: the versions, the phone and the screen. */
fun feedbackContext(origin: FeedbackOrigin): FeedbackContext = FeedbackContext(
    appVersion = BuildConfig.VERSION_NAME.take(40),
    android = Build.VERSION.RELEASE.orEmpty().take(20),
    device = deviceName(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty()).take(80),
    screen = origin,
)

/** « Google Pixel 8 », without saying the maker twice when the model already does (« samsung SM-S911B »). */
internal fun deviceName(manufacturer: String, model: String): String {
    val maker = manufacturer.trim().replaceFirstChar { it.uppercase() }
    val name = model.trim()
    return when {
        name.isEmpty() -> maker
        maker.isEmpty() || name.startsWith(maker, ignoreCase = true) -> name
        else -> "$maker $name"
    }
}

@Composable
fun FeedbackFormRoute(
    container: AppContainer?,
    currentMember: Member,
    origin: FeedbackOrigin,
    onBack: () -> Unit,
    onOpenMine: () -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val viewModel: FeedbackFormViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                // A screenshot needs Storage; without it the form only takes the text.
                val encode: (suspend (String) -> ByteArray)? = container?.photoStorage?.let {
                    val encoder = PhotoEncoder(appContext)
                    val toJpeg: suspend (String) -> ByteArray = { uri ->
                        encoder.encodeSingle(
                            Uri.parse(uri),
                            Feedback.SCREENSHOT_MAX_SIDE,
                            Feedback.SCREENSHOT_TARGET_BYTES,
                            Feedback.SCREENSHOT_LIMIT_BYTES,
                        )
                    }
                    toJpeg
                }
                FeedbackFormViewModel(container?.feedbackRepository, currentMember, feedbackContext(origin), createSavedStateHandle(), encode)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FeedbackFormScreen(
        state,
        onChoose = viewModel::choose,
        onType = viewModel::type,
        onSend = viewModel::send,
        onBack = onBack,
        onAgain = viewModel::again,
        onOpenMine = onOpenMine,
        onScreenshot = viewModel::pickScreenshot,
    )
}

@Composable
fun FeedbackListRoute(container: AppContainer?, onBack: () -> Unit) {
    val viewModel: FeedbackListViewModel = viewModel(
        factory = viewModelFactory { initializer { FeedbackListViewModel(container?.feedbackRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actionFailed by viewModel.actionFailed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val storage = LocalPhotoStorage.current
    val scope = rememberCoroutineScope()
    FeedbackListScreen(
        state = state,
        onBack = onBack,
        onAnswer = viewModel::answer,
        onRemove = viewModel::remove,
        onShareForClaude = { item -> scope.launch { shareFeedbackForClaude(context, storage, item) } },
        // Android 10 and later only: before, MediaStore would need a permission (note C of #288).
        onSaveScreenshot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            { item ->
                scope.launch {
                    val saved = saveFeedbackScreenshot(context, storage, item)
                    val message = if (saved) R.string.feedback_screenshot_saved else R.string.feedback_screenshot_save_failed
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        } else {
            null
        },
        actionFailed = actionFailed,
        onFailureShown = viewModel::failureShown,
    )
}

@Composable
fun MyFeedbackRoute(container: AppContainer?, currentMember: Member, onBack: () -> Unit) {
    val viewModel: MyFeedbackViewModel = viewModel(
        factory = viewModelFactory { initializer { MyFeedbackViewModel(container?.feedbackRepository, currentMember.id) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FeedbackListScreen(state = state, onBack = onBack)
}
