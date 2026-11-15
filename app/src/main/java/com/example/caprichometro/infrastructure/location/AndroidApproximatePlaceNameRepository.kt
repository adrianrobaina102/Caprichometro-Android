package com.example.caprichometro.infrastructure.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.caprichometro.domain.model.NearbyCellGrid
import com.example.caprichometro.domain.repository.ApproximatePlaceNameRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class AndroidApproximatePlaceNameRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ApproximatePlaceNameRepository {

    companion object {
        private const val GEOCODE_TIMEOUT_MS = 5_000L
    }

    private val appContext = context.applicationContext
    private val cache = ConcurrentHashMap<String, String>()

    override suspend fun resolvePlaceName(cellId: String): String? {
        val normalizedCell = cellId.trim()
        if (normalizedCell.isEmpty()) return null
        cache[normalizedCell]?.let { return it }

        val center = NearbyCellGrid.centerLocation(normalizedCell) ?: return null
        if (!Geocoder.isPresent()) return null

        val name = try {
            withContext(ioDispatcher) {
                val geocoder = Geocoder(appContext, Locale.forLanguageTag("es-ES"))
                val addresses = reverseGeocode(geocoder, center.latitude, center.longitude)
                extractApproximateName(addresses.firstOrNull())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        if (!name.isNullOrBlank()) {
            cache[normalizedCell] = name
        }
        return name
    }

    private suspend fun reverseGeocode(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double
    ): List<Address> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(
                        latitude,
                        longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (continuation.isActive) {
                                    continuation.resume(addresses)
                                }
                            }

                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) {
                                    continuation.resume(emptyList())
                                }
                            }
                        }
                    )
                }
            }.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(latitude, longitude, 1).orEmpty()
        }
    }

    private fun extractApproximateName(address: Address?): String? {
        if (address == null) return null
        return listOf(
            address.locality,
            address.subAdminArea,
            address.adminArea,
            address.countryName
        ).firstOrNull { !it.isNullOrBlank() }?.trim()
    }
}
