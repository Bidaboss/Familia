package fr.familia.app.feature.feed.publish

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.familia.app.FamiliaApplication
import fr.familia.app.R
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.model.Member
import fr.familia.app.core.place.AndroidPlaceLocator
import fr.familia.app.core.theme.PaperTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * « Publier dans la gazette » in a sheet that rises from the bottom over the gazette, still seen
 * darkened above it (prototype, retour du 26/11: never a page). Closed by ✕, by a touch on the
 * gazette, or by « retour »; when something would be lost, it asks first. [postId] set: « Modifier
 * le texte », the same sheet already filled. [onClose] once it is gone, published or not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostEditSheet(
    container: AppContainer?,
    currentMember: Member,
    postId: String?,
    onClose: () -> Unit,
) {
    val online = (LocalContext.current.applicationContext as? FamiliaApplication)?.networkMonitor?.isOnline ?: flowOf(true)
    val viewModel: PostEditViewModel = viewModel(
        key = "gazettePost-$postId",
        factory = viewModelFactory {
            initializer {
                PostEditViewModel(
                    container?.gazettePostRepository, container?.memberRepository, container?.photoUploader, currentMember.id, postId, online,
                    locator = container?.placeLocator,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The location permission is asked « pendant l'utilisation de l'appli », only when the member wants it.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) viewModel.locate() else viewModel.locationRefused()
    }
    val locator = container?.placeLocator
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val keep by rememberUpdatedState(hasChanges(state) || (state as? PostEditUiState.Editing)?.isSaving == true)
    val saving by rememberUpdatedState((state as? PostEditUiState.Editing)?.isSaving == true)
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            // A swipe, a touch on the gazette or « retour » with something written: ask, stay open.
            if (value == SheetValue.Hidden && keep) {
                if (!saving) confirmLeave = true
                false
            } else {
                true
            }
        },
    )
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheet.hide() }.invokeOnCompletion { onClose() } }
    LaunchedEffect(state) { if (state == PostEditUiState.Done) close() }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = SheetCream,
        contentColor = SheetBrown,
    ) {
        // A light sheet whatever the theme, like the prototype.
        PaperTheme {
            PostEditScreen(
                state = state,
                onText = viewModel::setText,
                onAddPhotos = viewModel::addPhotos,
                onRemovePhoto = viewModel::removePhoto,
                onMentions = viewModel::setMentions,
                onSave = viewModel::save,
                onDismissFailure = viewModel::dismissFailure,
                onCancelSending = viewModel::cancelSending,
                onClose = { if (hasChanges(state)) confirmLeave = true else close() },
                onLocate = locator?.let { place ->
                    { if (place.hasPermission()) viewModel.locate() else permission.launch(AndroidPlaceLocator.PERMISSIONS) }
                },
                onRemovePlace = viewModel::removePlace,
            )
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.gazette_post_leave_title)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.member_leave_keep)) } },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        // Leaving: what was written goes, the sheet closes.
                        onClose()
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.member_leave_discard)) }
            },
        )
    }
}
