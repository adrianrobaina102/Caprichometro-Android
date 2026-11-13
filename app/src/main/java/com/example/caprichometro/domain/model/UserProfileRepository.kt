package com.example.caprichometro.domain.model

interface UserProfileRepository {
    suspend fun isUsernameAvailable(username: String): Result<Boolean>
    suspend fun saveProfile(profile: UserProfile): Result<Unit>
    suspend fun getUserById(userId: String): Result<UserProfile?>
    suspend fun updateBio(userId: String, bio: String): Result<Unit>
    suspend fun updatePhotoUrl(userId: String, photoUrl: String): Result<Unit> = Result.success(Unit)
    suspend fun deleteProfile(userId: String, username: String): Result<Unit>
}
