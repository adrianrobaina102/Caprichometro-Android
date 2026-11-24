package com.example.caprichometro.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rejilla interoperable con iOS para representar ubicación aproximada en celdas de 0,05° (`v1_latIdx_lonIdx`).
 */
object NearbyCellGrid {
    const val CELL_SIZE_DEGREES = 0.05
    const val EARTH_RADIUS_METERS = 6_371_000.0
    const val LOCAL_RADIUS_METERS = 50_000.0
    private const val LATITUDE_CELL_COUNT = 3_600
    private const val LONGITUDE_CELL_COUNT = 7_200

    fun isBeyondLocalRadius(distanceMeters: Double?): Boolean =
        distanceMeters != null && distanceMeters.isFinite() && distanceMeters > LOCAL_RADIUS_METERS

    fun cellId(location: ApproximateLocation): String {
        val latitudeIndex = min(
            LATITUDE_CELL_COUNT - 1,
            max(0, floor((location.latitude + 90.0) / CELL_SIZE_DEGREES).toInt())
        )
        val rawLongitudeIndex = floor((location.longitude + 180.0) / CELL_SIZE_DEGREES).toInt()
        val longitudeIndex = ((rawLongitudeIndex % LONGITUDE_CELL_COUNT) + LONGITUDE_CELL_COUNT) % LONGITUDE_CELL_COUNT
        return "v1_${latitudeIndex}_${longitudeIndex}"
    }

    /**
     * Devuelve una cuadrícula de `(2 * radiusCells + 1)^2` celdas alrededor de [location]
     * (por defecto 5x5 = 25 celdas, dentro del límite de 30 elementos de `whereIn` en Firestore).
     */
    fun nearbyCellIds(location: ApproximateLocation, radiusCells: Int = 2): List<String> {
        val safeRadius = radiusCells.coerceIn(0, 2)
        val centerLatIdx = min(
            LATITUDE_CELL_COUNT - 1,
            max(0, floor((location.latitude + 90.0) / CELL_SIZE_DEGREES).toInt())
        )
        val rawLonIdx = floor((location.longitude + 180.0) / CELL_SIZE_DEGREES).toInt()
        val centerLonIdx = ((rawLonIdx % LONGITUDE_CELL_COUNT) + LONGITUDE_CELL_COUNT) % LONGITUDE_CELL_COUNT

        val cells = LinkedHashSet<String>()
        for (dLat in -safeRadius..safeRadius) {
            val latIdx = (centerLatIdx + dLat).coerceIn(0, LATITUDE_CELL_COUNT - 1)
            for (dLon in -safeRadius..safeRadius) {
                val lonIdx = ((centerLonIdx + dLon) % LONGITUDE_CELL_COUNT + LONGITUDE_CELL_COUNT) % LONGITUDE_CELL_COUNT
                cells.add("v1_${latIdx}_${lonIdx}")
            }
        }
        return cells.toList()
    }

    fun centerLocation(cellId: String): ApproximateLocation? {
        val parts = cellId.trim().split('_')
        if (parts.size != 3 || parts[0] != "v1") return null

        val latIdx = parts[1].toIntOrNull()?.takeIf { it in 0 until LATITUDE_CELL_COUNT } ?: return null
        val lonIdx = parts[2].toIntOrNull()?.takeIf { it in 0 until LONGITUDE_CELL_COUNT } ?: return null

        val latitude = latIdx * CELL_SIZE_DEGREES - 90.0 + CELL_SIZE_DEGREES / 2.0
        val longitude = lonIdx * CELL_SIZE_DEGREES - 180.0 + CELL_SIZE_DEGREES / 2.0
        return ApproximateLocation(latitude = latitude, longitude = longitude)
    }

    fun distanceInMeters(from: ApproximateLocation, toCellId: String): Double? {
        val target = centerLocation(toCellId) ?: return null
        return distanceInMeters(from, target)
    }

    fun distanceInMeters(from: ApproximateLocation, to: ApproximateLocation): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(to.longitude - from.longitude)

        val sinHalfLat = sin(deltaLat / 2.0)
        val sinHalfLon = sin(deltaLon / 2.0)
        val haversine = sinHalfLat * sinHalfLat +
            cos(lat1) * cos(lat2) * sinHalfLon * sinHalfLon
        val angularDistance = 2.0 * asin(min(1.0, sqrt(haversine)))
        return EARTH_RADIUS_METERS * angularDistance
    }

    fun formatApproximateDistanceMeters(meters: Double): String {
        if (!meters.isFinite() || meters < 2_500.0) {
            return "En tu zona"
        }
        val roundedKm = max(5, ((meters / 5_000.0).roundToInt()) * 5)
        return "Aprox. $roundedKm km"
    }
}
