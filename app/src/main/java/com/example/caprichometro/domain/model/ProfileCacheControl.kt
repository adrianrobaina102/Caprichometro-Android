package com.example.caprichometro.domain.model

/**
 * Permite invalidar la caché en memoria de perfiles cuando el usuario fuerza un refresco
 * o visita un perfil concreto para ver cambios recientes de foto o biografía.
 */
interface ProfileCacheControl {
    fun invalidateUser(userId: String)
    fun invalidateAll()
}
