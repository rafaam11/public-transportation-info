package com.rafaam11.businfo.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.rafaam11.businfo.domain.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

interface CurrentLocationDataSource {
    suspend fun current(): Result<GeoPoint>
}

class AndroidCurrentLocationDataSource(private val context: Context) : CurrentLocationDataSource {
    private val manager = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    override suspend fun current(): Result<GeoPoint> {
        if (!hasPermission()) return Result.failure(LocationPermissionMissingException())
        val providers = enabledLocationProviders(
            networkEnabled = manager.isEnabledSafely(LocationManager.NETWORK_PROVIDER),
            gpsEnabled = manager.isEnabledSafely(LocationManager.GPS_PROVIDER),
        )
        return resolveCurrentDaeguLocation(providers) { provider -> requestCurrentPoint(provider) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentPoint(provider: String): GeoPoint? =
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                cancellation,
                ContextCompat.getMainExecutor(context),
            ) { location ->
                if (continuation.isActive) {
                    continuation.resume(location?.let { GeoPoint(it.longitude, it.latitude) })
                }
            }
        }

    private fun LocationManager.isEnabledSafely(provider: String): Boolean =
        runCatching { isProviderEnabled(provider) }.getOrDefault(false)

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

}

internal fun enabledLocationProviders(networkEnabled: Boolean, gpsEnabled: Boolean): List<String> = buildList {
    if (networkEnabled) add(LocationManager.NETWORK_PROVIDER)
    if (gpsEnabled) add(LocationManager.GPS_PROVIDER)
}

internal suspend fun resolveCurrentDaeguLocation(
    providers: List<String>,
    timeoutMillis: Long = LOCATION_REQUEST_TIMEOUT_MILLIS,
    request: suspend (String) -> GeoPoint?,
): Result<GeoPoint> {
    providers.forEach { provider ->
        val point = try {
            withTimeoutOrNull(timeoutMillis) { request(provider) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (point != null && point.isInDaegu()) return Result.success(point)
    }
    return Result.failure(CurrentLocationUnavailableException())
}

private fun GeoPoint.isInDaegu(): Boolean = longitude in 128.0..129.2 && latitude in 35.3..36.3

private const val LOCATION_REQUEST_TIMEOUT_MILLIS = 5_000L

class LocationPermissionMissingException : IllegalStateException("Location permission is missing")
class CurrentLocationUnavailableException : IllegalStateException("Current Daegu location is unavailable")
