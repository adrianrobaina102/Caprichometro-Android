package com.example.caprichometro.profile

import com.example.caprichometro.application.usecase.profile.UpdateProfilePhotoUseCase
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateProfilePhotoUseCaseTest {

    private class FakeUserProfileRepository : UserProfileRepository {
        var lastUpdatedUserId: String? = null
        var lastUpdatedPhotoUrl: String? = null
        var resultToReturn: Result<Unit> = Result.success(Unit)

        override suspend fun isUsernameAvailable(username: String): Result<Boolean> = Result.success(true)
        override suspend fun saveProfile(profile: UserProfile): Result<Unit> = Result.success(Unit)
        override suspend fun getUserById(userId: String): Result<UserProfile?> = Result.success(null)
        override suspend fun updateBio(userId: String, bio: String): Result<Unit> = Result.success(Unit)
        override suspend fun updatePhotoUrl(userId: String, photoUrl: String): Result<Unit> {
            lastUpdatedUserId = userId
            lastUpdatedPhotoUrl = photoUrl
            return resultToReturn
        }
        override suspend fun deleteProfile(userId: String, username: String): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun `execute fails when userId is blank`() = runTest {
        val repo = FakeUserProfileRepository()
        val useCase = UpdateProfilePhotoUseCase(repo)

        val result = useCase.execute("   ", "data:image/webp;base64,abc")

        assertTrue(result.isFailure)
        assertEquals(null, repo.lastUpdatedUserId)
    }

    @Test
    fun `execute trims photoUrl and delegates to repository`() = runTest {
        val repo = FakeUserProfileRepository()
        val useCase = UpdateProfilePhotoUseCase(repo)

        val result = useCase.execute("uid_123", "  data:image/webp;base64,abc  ")

        assertTrue(result.isSuccess)
        assertEquals("uid_123", repo.lastUpdatedUserId)
        assertEquals("data:image/webp;base64,abc", repo.lastUpdatedPhotoUrl)
    }

    @Test
    fun `execute allows empty photoUrl to remove profile photo`() = runTest {
        val repo = FakeUserProfileRepository()
        val useCase = UpdateProfilePhotoUseCase(repo)

        val result = useCase.execute("uid_123", "")

        assertTrue(result.isSuccess)
        assertEquals("uid_123", repo.lastUpdatedUserId)
        assertEquals("", repo.lastUpdatedPhotoUrl)
    }
}
