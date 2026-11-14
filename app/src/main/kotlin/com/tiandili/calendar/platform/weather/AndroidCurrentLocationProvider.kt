package com.tiandili.calendar.platform.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import com.tiandili.calendar.domain.weather.GeoPoint
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class AndroidCurrentLocationProvider(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val locationManager = applicationContext.getSystemService(
        LocationManager::class.java,
    )

    suspend fun currentLocation(): GeoPoint? {
        if (!hasLocationPermission()) {
            return null
        }
        return runCatching {
            bestLastKnownLocation()?.toGeoPoint()
                ?: withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
                    requestSingleLocation()?.toGeoPoint()
                }
        }.getOrNull()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun bestLastKnownLocation(): Location? = candidateProviders
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .filter { it.hasValidCoordinates() }
        .maxByOrNull(Location::getTime)

    private suspend fun requestSingleLocation(): Location? {
        val provider = availableProviders().firstOrNull() ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestCurrentLocation(provider)
        } else {
            requestLegacySingleUpdate(provider)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            locationManager.getCurrentLocation(
                provider,
                cancellationSignal,
                ContextCompat.getMainExecutor(applicationContext),
            ) { location ->
                if (continuation.isActive) {
                    continuation.resume(location)
                }
            }
        }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun requestLegacySingleUpdate(provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }

                override fun onProviderDisabled(provider: String) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }

                override fun onProviderEnabled(provider: String) = Unit

                @Suppress("OVERRIDE_DEPRECATION")
                override fun onStatusChanged(
                    provider: String?,
                    status: Int,
                    extras: Bundle?,
                ) = Unit
            }
            continuation.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }
            locationManager.requestSingleUpdate(
                provider,
                listener,
                Looper.getMainLooper(),
            )
        }

    private fun availableProviders(): List<String> = candidateProviders.filter { provider ->
        runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
    }

    private fun Location.toGeoPoint(): GeoPoint? =
        takeIf { it.hasValidCoordinates() }?.let {
            GeoPoint(latitude = it.latitude, longitude = it.longitude)
        }

    private fun Location.hasValidCoordinates(): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 10_000L
        val candidateProviders = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        )
    }
}
