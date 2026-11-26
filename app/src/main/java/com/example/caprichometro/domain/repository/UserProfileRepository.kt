package com.example.caprichometro.domain.repository

import com.example.caprichometro.domain.model.UserProfile

/**
 * Lecturas y comprobación de disponibilidad de perfiles de usuario.
 */
interface UserProfileReadRepository {
    suspend fun isUsernameAvailable(username: String): Result<Boolean>
    suspend fun getUserById(userId: String): Result<UserProfile?>
    suspend fun getCachedUserById(userId: String): UserProfile? = null
}

/**
 * Escritura, actualizaciones parciales y borrado de perfiles de usuario.
 */
interface UserProfileWriteRepository {
    suspend fun saveProfile(profile: UserProfile): Result<Unit>
    suspend fun updateBio(userId: String, bio: String): Result<Unit>
    suspend fun updatePhotoUrl(userId: String, photoUrl: String): Result<Unit> = Result.success(Unit)
    suspend fun updateLocationCell(userId: String, locationCell: String?): Result<Unit> = Result.success(Unit)
    suspend fun deleteProfile(userId: String, username: String): Result<Unit>
}

/**
 * Puerto compuesto mantenido para adaptadores de infraestructura completos.
 */
interface UserProfileRepository :
    UserProfileReadRepository,
    UserProfileWriteRepository
