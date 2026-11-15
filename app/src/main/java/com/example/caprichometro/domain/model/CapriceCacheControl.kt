package com.example.caprichometro.domain.model

/**
 * Permite invalidar la caché en memoria de caprichos por usuario cuando se publica,
 * actualiza o el usuario solicita un refresco manual en el feed.
 */
interface CapriceCacheControl {
    fun invalidateUser(userId: String)
    fun invalidateAll()
}
