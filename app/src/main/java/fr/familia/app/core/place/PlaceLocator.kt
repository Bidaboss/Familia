package fr.familia.app.core.place

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

/**
 * What the phone knows about places, behind an interface so the screens can be tested. Nothing
 * here follows the member: the position is read once, when they touch the button (PRD, récit 33).
 */
interface PlaceLocator {
    /** The position of the moment, or null (no permission, location off, nothing within 20 s). */
    suspend fun currentPosition(): Pair<Double, Double>?

    /** The postal address of a point, or null when the geocoder of the phone has none. */
    suspend fun addressOf(lat: Double, lng: Double): String?

    /** The point of an address typed by the member, with its full address, or null. */
    suspend fun find(query: String): Place?

    /** The long link behind a short Google Maps link (maps.app.goo.gl), or null. */
    suspend fun expand(shortLink: String): String?
}

/** Android's own location and geocoder: free, with no API key. */
class AndroidPlaceLocator(private val context: Context) : PlaceLocator {

    private val locationManager get() = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    override suspend fun currentPosition(): Pair<Double, Double>? {
        if (!hasPermission()) return null
        val manager = locationManager ?: return null
        // The fused provider (Android 12) mixes GPS and networks; before it, GPS then the networks.
        val candidates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        }
        val enabled = candidates.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (enabled.isEmpty()) return null
        // GPS can stay silent indoors: each provider gets its turn before an older fix is used.
        var fresh: Location? = null
        for (provider in enabled) {
            fresh = withTimeoutOrNull(TIMEOUT_MS / enabled.size) { oneFix(manager, provider) }
            if (fresh != null) break
        }
        val location = fresh ?: recentFix(manager)
        return location?.let { it.latitude to it.longitude }
    }

    @SuppressLint("MissingPermission")
    private suspend fun oneFix(manager: LocationManager, provider: String): Location? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            manager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                if (cont.isActive) cont.resume(location)
            }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdates(this)
                    if (cont.isActive) cont.resume(location)
                }

                @Deprecated("Needed below API 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                override fun onProviderDisabled(provider: String) {
                    manager.removeUpdates(this)
                    if (cont.isActive) cont.resume(null)
                }
            }
            cont.invokeOnCancellation { manager.removeUpdates(listener) }
            manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        }
    }

    /** A fix from the last two minutes, when no fresh one came in time. */
    @SuppressLint("MissingPermission")
    private fun recentFix(manager: LocationManager): Location? = manager.allProviders
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .filter { System.currentTimeMillis() - it.time < RECENT_MS }
        .maxByOrNull { it.time }

    override suspend fun addressOf(lat: Double, lng: Double): String? =
        geocode(Request.Reverse(lat, lng))?.firstOrNull()?.let(::addressLine)

    override suspend fun find(query: String): Place? {
        val found = geocode(Request.Forward(query))?.firstOrNull() ?: return null
        if (!found.hasLatitude() || !found.hasLongitude()) return null
        return Place(found.latitude, found.longitude, address = addressLine(found) ?: query.take(Place.MAX_ADDRESS))
    }

    private sealed interface Request {
        data class Reverse(val lat: Double, val lng: Double) : Request
        data class Forward(val query: String) : Request
    }

    /** Runs the geocoder: its listener from Android 13, its blocking call before, off the main thread. */
    private suspend fun geocode(request: Request): List<Address>? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.FRANCE)
        return withTimeoutOrNull(TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listening(geocoder, request)
            } else {
                withContext(Dispatchers.IO) { runCatching { blocking(geocoder, request) }.getOrNull() }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun listening(geocoder: Geocoder, request: Request): List<Address>? = suspendCancellableCoroutine { cont ->
        val listener = object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                if (cont.isActive) cont.resume(addresses)
            }

            override fun onError(errorMessage: String?) {
                if (cont.isActive) cont.resume(null)
            }
        }
        when (request) {
            is Request.Reverse -> geocoder.getFromLocation(request.lat, request.lng, 1, listener)
            is Request.Forward -> geocoder.getFromLocationName(request.query, 1, listener)
        }
    }

    @Suppress("DEPRECATION")
    private fun blocking(geocoder: Geocoder, request: Request): List<Address>? = when (request) {
        is Request.Reverse -> geocoder.getFromLocation(request.lat, request.lng, 1)
        is Request.Forward -> geocoder.getFromLocationName(request.query, 1)
    }

    override suspend fun expand(shortLink: String): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TIMEOUT_MS) {
            runCatching {
                var link = shortLink
                // Two hops at most: maps.app.goo.gl → goo.gl → google.com/maps.
                repeat(3) {
                    val connection = URL(link).openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = false
                    connection.requestMethod = "HEAD"
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 5_000
                    val next = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (next == null) return@runCatching link.takeIf { it != shortLink }
                    link = next
                }
                link
            }.getOrNull()
        }
    }

    companion object {
        /** « Pendant l'utilisation de l'appli » only: no background location is ever asked. */
        val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        private const val TIMEOUT_MS = 20_000L
        private const val RECENT_MS = 2 * 60_000L
    }
}

private fun addressLine(address: Address): String? =
    address.getAddressLine(0)?.trim()?.takeIf { it.isNotEmpty() }?.take(Place.MAX_ADDRESS)
