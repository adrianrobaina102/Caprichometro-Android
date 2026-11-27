package com.example.caprichometro.infrastructure.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.caprichometro.domain.model.ApproximateLocation
import com.example.caprichometro.domain.model.DeviceLocationException
import com.example.caprichometro.domain.repository.DeviceLocationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AndroidLocationRepository(
    private val context: Context,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) : DeviceLocationRepository {

    companion object {
        // Para celdas aproximadas de ~5.5 km una lectura reciente de 10 minutos evita esperas de GPS en General y Amigos
        private const val MAX_CACHED_LOCATION_AGE_MS = 10 * 60_000L
        private const val MAX_FALLBACK_MEMORY_AGE_MS = 2 * 60_000L
        private const val MAX_ACCEPTABLE_ACCURACY_METERS = 20_000f
        private const val CURRENT_LOCATION_TIMEOUT_MS = 2_500L
    }

    private data class MemoryCachedLocation(
        val location: ApproximateLocation,
        val cachedAtMillis: Long,
        val ttlMillis: Long
    )

    private val appContext = context.applicationContext
    private val requestMutex = Mutex()

    @Volatile
    private var memoryCachedLocation: MemoryCachedLocation? = null

    fun clearCache() {
        memoryCachedLocation = null
    }

    override fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    override suspend fun currentApproximateLocation(): Result<ApproximateLocation> {
        if (!hasLocationPermission()) {
            memoryCachedLocation = null
            return Result.failure(DeviceLocationException.PermissionDenied())
        }

        val now = clockMillis()
        memoryCachedLocation?.takeIf { (now - it.cachedAtMillis) in 0L..it.ttlMillis }?.let { cached ->
            return Result.success(cached.location)
        }

        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return Result.failure(DeviceLocationException.LocationUnavailable())

        if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
            return Result.failure(DeviceLocationException.LocationServicesDisabled())
        }

        val providers = candidateProviders(locationManager)
        if (providers.isEmpty()) {
            return Result.failure(DeviceLocationException.LocationServicesDisabled())
        }

        findCachedLocation(locationManager, checkFreshness = true)?.let { cached ->
            memoryCachedLocation = MemoryCachedLocation(cached, clockMillis(), MAX_CACHED_LOCATION_AGE_MS)
            return Result.success(cached)
        }

        return requestMutex.withLock {
            try {
                val lockNow = clockMillis()
                memoryCachedLocation?.takeIf { (lockNow - it.cachedAtMillis) in 0L..it.ttlMillis }?.let { cached ->
                    return@withLock Result.success(cached.location)
                }

                findCachedLocation(locationManager, checkFreshness = true)?.let { cached ->
                    memoryCachedLocation = MemoryCachedLocation(cached, clockMillis(), MAX_CACHED_LOCATION_AGE_MS)
                    return@withLock Result.success(cached)
                }

                val fallbackCached = findCachedLocation(locationManager, checkFreshness = false)
                val firstFresh = fetchFirstAvailableLocation(locationManager, providers)
                if (firstFresh != null) {
                    memoryCachedLocation = MemoryCachedLocation(firstFresh, clockMillis(), MAX_CACHED_LOCATION_AGE_MS)
                    return@withLock Result.success(firstFresh)
                }

                fallbackCached?.let { fallback ->
                    memoryCachedLocation = MemoryCachedLocation(fallback, clockMillis(), MAX_FALLBACK_MEMORY_AGE_MS)
                    return@withLock Result.success(fallback)
                }

                Result.failure(DeviceLocationException.LocationUnavailable())
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                Result.failure(DeviceLocationException.PermissionDenied())
            } catch (e: Exception) {
                Result.failure(DeviceLocationException.LocationUnavailable())
            }
        }
    }

    private suspend fun fetchFirstAvailableLocation(
        locationManager: LocationManager,
        providers: List<String>
    ): ApproximateLocation? {
        return withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) {
            coroutineScope {
                val winner = CompletableDeferred<ApproximateLocation?>()
                val jobs = providers.map { provider ->
                    launch {
                        val raw = fetchCurrentLocationFromProvider(locationManager, provider)
                        val validated = raw?.toApproximateLocationOrNull(checkFreshness = false)
                        if (validated != null) {
                            winner.complete(validated)
                        }
                    }
                }
                launch {
                    jobs.forEach { it.join() }
                    winner.complete(null)
                }
                val result = winner.await()
                jobs.forEach { it.cancel() }
                result
            }
        }
    }

    private fun candidateProviders(locationManager: LocationManager): List<String> {
        val candidates = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            candidates.add(LocationManager.FUSED_PROVIDER)
        }
        candidates.add(LocationManager.NETWORK_PROVIDER)
        candidates.add(LocationManager.PASSIVE_PROVIDER)
        // Solo con ACCESS_COARSE_LOCATION el sistema degrada/ofusca cualquier lectura GNSS antes de entregarla
        candidates.add(LocationManager.GPS_PROVIDER)

        return candidates.filter { provider ->
            try {
                locationManager.allProviders.contains(provider) && locationManager.isProviderEnabled(provider)
            } catch (_: Exception) {
                false
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun findCachedLocation(
        locationManager: LocationManager,
        checkFreshness: Boolean
    ): ApproximateLocation? {
        val searchProviders = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        return searchProviders
            .mapNotNull { provider ->
                try {
                    locationManager.getLastKnownLocation(provider)
                } catch (_: SecurityException) {
                    null
                } catch (_: Exception) {
                    null
                }
            }
            .sortedByDescending { it.time }
            .firstNotNullOfOrNull { it.toApproximateLocationOrNull(checkFreshness = checkFreshness) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchCurrentLocationFromProvider(
        locationManager: LocationManager,
        provider: String
    ): Location? = withContext(Dispatchers.Main.immediate) {
        if (provider == LocationManager.PASSIVE_PROVIDER) {
            return@withContext try {
                locationManager.getLastKnownLocation(provider)
            } catch (_: Exception) {
                null
            }
        }

        withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation {
                    cancellationSignal.cancel()
                }
                try {
                    val executor = ContextCompat.getMainExecutor(appContext)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val request = LocationRequest.Builder(0L)
                            .setQuality(LocationRequest.QUALITY_BALANCED_POWER_ACCURACY)
                            .setDurationMillis(CURRENT_LOCATION_TIMEOUT_MS)
                            .setMaxUpdates(1)
                            .build()
                        locationManager.getCurrentLocation(
                            provider,
                            request,
                            cancellationSignal,
                            executor
                        ) { location ->
                            if (continuation.isActive) {
                                continuation.resume(location)
                            }
                        }
                    } else {
                        LocationManagerCompat.getCurrentLocation(
                            locationManager,
                            provider,
                            cancellationSignal,
                            executor
                        ) { location ->
                            if (continuation.isActive) {
                                continuation.resume(location)
                            }
                        }
                    }
                } catch (_: SecurityException) {
                    if (continuation.isActive) continuation.resume(null)
                } catch (_: IllegalArgumentException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    private fun Location.toApproximateLocationOrNull(checkFreshness: Boolean): ApproximateLocation? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        if (hasAccuracy() && accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) return null
        if (checkFreshness) {
            val ageMs = clockMillis() - time
            if (ageMs > MAX_CACHED_LOCATION_AGE_MS || ageMs < -MAX_CACHED_LOCATION_AGE_MS) {
                return null
            }
        }
        return ApproximateLocation(latitude = latitude, longitude = longitude)
    }
}
