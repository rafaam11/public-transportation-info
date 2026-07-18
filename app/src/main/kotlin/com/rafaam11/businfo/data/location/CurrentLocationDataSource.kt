package com.rafaam11.businfo.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.rafaam11.businfo.domain.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface CurrentLocationDataSource {
    suspend fun current(): Result<GeoPoint>
}

class AndroidCurrentLocationDataSource(private val context: Context) : CurrentLocationDataSource {
    private val manager = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    override suspend fun current(): Result<GeoPoint> {
        if (!hasPermission()) return Result.failure(LocationPermissionMissingException())
        val provider = when {
            runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) -> LocationManager.GPS_PROVIDER
            runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) -> LocationManager.NETWORK_PROVIDER
            else -> return Result.failure(CurrentLocationUnavailableException())
        }
        return suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                cancellation,
                ContextCompat.getMainExecutor(context),
            ) { location ->
                if (continuation.isActive) {
                    continuation.resume(location?.toPointResult() ?: Result.failure(CurrentLocationUnavailableException()))
                }
            }
        }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun Location.toPointResult(): Result<GeoPoint> =
        if (longitude in 128.0..129.2 && latitude in 35.3..36.3) {
            Result.success(GeoPoint(longitude, latitude))
        } else Result.failure(CurrentLocationUnavailableException())
}

class LocationPermissionMissingException : IllegalStateException("Location permission is missing")
class CurrentLocationUnavailableException : IllegalStateException("Current Daegu location is unavailable")
