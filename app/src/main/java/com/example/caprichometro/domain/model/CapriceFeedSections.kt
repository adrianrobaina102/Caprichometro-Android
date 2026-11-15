package com.example.caprichometro.domain.model

data class CapriceFeedSections(
    val localItems: List<CapriceFeedItem>,
    val farItems: List<CapriceFeedItem>
)

/**
 * Divide un listado ya paginado o completo de [CapriceFeedItem] en dos secciones:
 * primera sección (`<= 50 km` o sin distancia conocida) y segunda sección (`> 50 km`).
 */
fun splitByDistanceRadius(items: List<CapriceFeedItem>): CapriceFeedSections {
    val localItems = ArrayList<CapriceFeedItem>(items.size)
    val farItems = ArrayList<CapriceFeedItem>()
    for (item in items) {
        if (NearbyCellGrid.isBeyondLocalRadius(item.approximateDistanceMeters)) {
            farItems.add(item)
        } else {
            localItems.add(item)
        }
    }
    return CapriceFeedSections(localItems = localItems, farItems = farItems)
}

/**
 * Ordena el feed de Amigos manteniendo arriba los caprichos cercanos o sin ubicación
 * (`<= 50 km`) y agrupando abajo los más lejanos (`> 50 km`), ambos por fecha reciente descendente.
 */
fun sortFriendsFeedItems(items: List<CapriceFeedItem>): List<CapriceFeedItem> {
    return items.sortedWith(
        compareBy<CapriceFeedItem> { NearbyCellGrid.isBeyondLocalRadius(it.approximateDistanceMeters) }
            .thenByDescending { it.caprice.timestamp }
            .thenBy { it.caprice.id }
    )
}

/**
 * Ordena el feed General de menor a mayor distancia y, en empate, por publicación más reciente.
 */
fun sortNearbyFeedItems(items: List<CapriceFeedItem>): List<CapriceFeedItem> {
    return items.sortedWith(
        compareBy<CapriceFeedItem> { it.approximateDistanceMeters ?: Double.MAX_VALUE }
            .thenByDescending { it.caprice.timestamp }
            .thenBy { it.caprice.id }
    )
}
