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
 * Ordena elementos por criterio del feed de Amigos: `< 50 km` (o sin celda) antes que
 * `> 50 km`, y dentro de cada bloque por fecha descendente e ID estable.
 */
fun <T> sortedByFriendsFeedOrder(
    items: List<T>,
    distanceOf: (T) -> Double?,
    capriceOf: (T) -> Caprice
): List<T> = items.sortedWith(
    compareBy<T> { NearbyCellGrid.isBeyondLocalRadius(distanceOf(it)) }
        .thenByDescending { capriceOf(it).timestamp }
        .thenBy { capriceOf(it).id }
)

/**
 * Ordena elementos por cercanía creciente y, en empate, por fecha reciente descendente.
 */
fun <T> sortedByNearbyFeedOrder(
    items: List<T>,
    distanceOf: (T) -> Double?,
    capriceOf: (T) -> Caprice
): List<T> = items.sortedWith(
    compareBy<T> { distanceOf(it) ?: Double.MAX_VALUE }
        .thenByDescending { capriceOf(it).timestamp }
        .thenBy { capriceOf(it).id }
)

/**
 * Ordena el feed de Amigos manteniendo arriba los caprichos cercanos o sin ubicación
 * (`<= 50 km`) y agrupando abajo los más lejanos (`> 50 km`), ambos por fecha reciente descendente.
 */
fun sortFriendsFeedItems(items: List<CapriceFeedItem>): List<CapriceFeedItem> =
    sortedByFriendsFeedOrder(
        items = items,
        distanceOf = { it.approximateDistanceMeters },
        capriceOf = { it.caprice }
    )

/**
 * Ordena el feed General de menor a mayor distancia y, en empate, por publicación más reciente.
 */
fun sortNearbyFeedItems(items: List<CapriceFeedItem>): List<CapriceFeedItem> =
    sortedByNearbyFeedOrder(
        items = items,
        distanceOf = { it.approximateDistanceMeters },
        capriceOf = { it.caprice }
    )
