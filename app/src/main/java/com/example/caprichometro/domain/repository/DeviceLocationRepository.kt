package com.example.caprichometro.domain.repository

import com.example.caprichometro.domain.model.ApproximateLocation

interface DeviceLocationRepository {
    fun hasLocationPermission(): Boolean
    suspend fun currentApproximateLocation(): Result<ApproximateLocation>
}
