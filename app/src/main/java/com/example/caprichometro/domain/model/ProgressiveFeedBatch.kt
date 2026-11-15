package com.example.caprichometro.domain.model

/**
 * Emisión incremental de un feed paginado que mantiene el conteo total conocido
 * aunque solo se hayan hidratado los elementos de la página actual.
 */
data class ProgressiveFeedBatch(
    val items: List<CapriceFeedItem>,
    val isFromCache: Boolean,
    val isComplete: Boolean,
    val totalAvailableCount: Int = items.size,
    val hasViewerLocation: Boolean = false
)
