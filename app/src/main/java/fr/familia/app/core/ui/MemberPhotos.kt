package fr.familia.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import fr.familia.app.core.data.MemberPhoto
import fr.familia.app.core.data.MemberPhotoRepository
import fr.familia.app.core.photo.PhotoProcessing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow

/**
 * The decoded profile photos, by member id (PRD, récit 3 bis). Provided once at the top of the app,
 * so every [MemberAvatar] shows the photo without each screen asking for it. Empty in previews and
 * tests, where avatars keep the initial.
 */
val LocalMemberPhotos = staticCompositionLocalOf<Map<String, ImageBitmap>> { emptyMap() }

/** Live decoded photos. A photo is decoded again only when it changes; failures keep the initials. */
@Composable
fun rememberMemberPhotos(repository: MemberPhotoRepository?): State<Map<String, ImageBitmap>> {
    val flow = remember(repository) { decodedPhotos(repository) }
    return flow.collectAsState(initial = emptyMap())
}

internal fun decodedPhotos(repository: MemberPhotoRepository?): Flow<Map<String, ImageBitmap>> {
    if (repository == null) return flowOf(emptyMap())
    val cache = HashMap<String, Pair<Long, ImageBitmap>>()
    return flow {
        emitAll(
            repository.observePhotos().map { photos ->
                cache.keys.retainAll(photos.keys)
                photos.values.mapNotNull { photo -> decodeCached(cache, photo)?.let { photo.memberId to it } }.toMap()
            },
        )
    }
        .catch { e ->
            if (e is CancellationException) throw e
            // Photos are a nicety: without them the avatars show the initials.
            emit(emptyMap())
        }
        .flowOn(Dispatchers.Default)
}

private fun decodeCached(cache: HashMap<String, Pair<Long, ImageBitmap>>, photo: MemberPhoto): ImageBitmap? {
    cache[photo.memberId]?.takeIf { it.first == photo.version }?.let { return it.second }
    val bitmap = PhotoProcessing.decodeAvatar(photo.jpeg)?.asImageBitmap() ?: return null
    cache[photo.memberId] = photo.version to bitmap
    return bitmap
}
