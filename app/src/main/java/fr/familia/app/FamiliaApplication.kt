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
import fr.familia.app.core.place.WIKIMEDIA_USER_AGENT
import okhttp3.OkHttpClient

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
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    if (isWikimedia(request.url.host)) {
                        chain.proceed(request.newBuilder().header("User-Agent", WIKIMEDIA_USER_AGENT).build())
                    } else {
                        chain.proceed(request)
                    }
                }
                .build()
        }
        .build()

    companion object {
        const val IMAGE_CACHE_BYTES = 250L * 1024 * 1024

        /**
         * The Wikimedia sites ask every client for a User-Agent that names it (retour E-47): the
         * images of Commons get ours, the generic one of the app; the other images keep the default.
         */
        fun isWikimedia(host: String): Boolean =
            WIKIMEDIA_HOSTS.any { host == it || host.endsWith(".$it") }

        private val WIKIMEDIA_HOSTS = listOf("wikimedia.org", "wikipedia.org", "wikivoyage.org")
    }
}
