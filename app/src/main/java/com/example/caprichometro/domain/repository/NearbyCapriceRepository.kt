package com.example.caprichometro.domain.repository

import com.example.caprichometro.domain.model.Caprice

interface NearbyCapriceRepository {
    suspend fun getDiscoverableCaprices(): Result<List<Caprice>>
}
