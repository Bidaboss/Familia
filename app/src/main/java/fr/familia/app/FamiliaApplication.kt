package fr.familia.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import fr.familia.app.core.crash.CrashCatcher
import fr.familia.app.core.data.AppContainer
import fr.familia.app.core.data.FirebaseAppContainer
import fr.familia.app.core.data.NetworkMonitor
import fr.familia.app.core.notifications.NotificationChannels

class FamiliaApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set
    lateinit var networkMonitor: NetworkMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        // First, so a crash during the start is kept too (« Plantages », récit 6 sexies).
        CrashCatcher.install(this)
        container = FirebaseAppContainer(this)
        networkMonitor = NetworkMonitor(this)
        NotificationChannels.create(this)
    }

    /**
     * The image loader of the whole app. A photo of Storage never changes behind its link (a new
     * photo gets a new link), so a photo in the cache is shown without asking the server again,
     * whatever the server's cache headers say: no second download, and it shows offline. 250 Mo
     * holds several hundred photos and all their thumbnails (an album of the 21 is about 85 Mo).
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .respectCacheHeaders(false)
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(IMAGE_CACHE_BYTES)
                .build()
        }
        .build()

    companion object {
        const val IMAGE_CACHE_BYTES = 250L * 1024 * 1024
    }
}
