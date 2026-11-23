package com.example.system

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class DetectedLocation(
    val latitude: Double,
    val longitude: Double,
    val displayName: String
)

class LocationHelper(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun detectCurrentLocation(): DetectedLocation? = withContext(Dispatchers.IO) {
        if (!PermissionStatusHelper.hasLocationPermission(context)) {
            return@withContext null
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        // 1. Try fresh balanced location with timeout
        val freshLocation = withTimeoutOrNull(6500L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cts = CancellationTokenSource()
                cont.invokeOnCancellation { cts.cancel() }
                try {
                    fusedClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cts.token
                    ).addOnSuccessListener { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }.addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
                } catch (_: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

        // 2. Fallback to fused lastLocation
        val location = freshLocation ?: withTimeoutOrNull(2500L) {
            suspendCancellableCoroutine<Location?> { cont ->
                try {
                    fusedClient.lastLocation
                        .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        } ?: getBestLastKnownFromSystemManager()

        if (location == null) {
            return@withContext null
        }

        val cityName = resolvePlaceName(location.latitude, location.longitude)
        DetectedLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            displayName = cityName
        )
    }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownFromSystemManager(): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (provider in providers) {
            try {
                if (manager.isProviderEnabled(provider)) {
                    val candidate = manager.getLastKnownLocation(provider)
                    if (candidate != null && (best == null || candidate.time > best.time)) {
                        best = candidate
                    }
                }
            } catch (_: Exception) {
                // Ignore provider errors
            }
        }
        return best
    }

    @Suppress("DEPRECATION")
    private suspend fun resolvePlaceName(latitude: Double, longitude: Double): String {
        val fallback = String.format(Locale.US, "%.4f°, %.4f°", latitude, longitude)
        return try {
            if (!Geocoder.isPresent()) return fallback
            val geocoder = Geocoder(context, Locale("ru"))
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(2500L) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(latitude, longitude, 1) { list ->
                            if (cont.isActive) cont.resume(list)
                        }
                    }
                }
            } else {
                geocoder.getFromLocation(latitude, longitude, 1)
            }
            val addr = addresses?.firstOrNull()
            val locality = addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea
            val country = addr?.countryName
            when {
                !locality.isNullOrBlank() && !country.isNullOrBlank() -> "$locality · $country"
                !locality.isNullOrBlank() -> locality
                !country.isNullOrBlank() -> country
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }
}
