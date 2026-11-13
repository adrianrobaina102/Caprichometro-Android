package com.example.caprichometro.infrastructure.data

import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

class FirestoreUserProfileRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) : UserProfileRepository {

    private data class CachedProfileEntry(
        val profile: UserProfile,
        val cachedAtMillis: Long
    )

    companion object {
        internal const val PROFILE_CACHE_TTL_MS = 60_000L
    }

    // Cache en memoria con TTL de 60s para evitar llamadas redundantes y refrescar cambios recientes
    private val userProfileCache = ConcurrentHashMap<String, CachedProfileEntry>()

    override suspend fun isUsernameAvailable(username: String): Result<Boolean> = withContext(ioDispatcher) {
        try {
            val document = db.collection("usernames").document(username).get().await()
            Result.success(!document.exists())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> = withContext(ioDispatcher) {
        try {
            val userRef = db.collection("users").document(profile.uid)
            val usernameRef = db.collection("usernames").document(profile.username)

            db.runTransaction { transaction ->
                val usernameSnap = transaction.get(usernameRef)
                if (usernameSnap.exists() && usernameSnap.getString("uid") != profile.uid) {
                    throw IllegalStateException("El nombre de usuario @${profile.username} ya está en uso.")
                }

                val userData = mutableMapOf<String, Any>(
                    "username" to profile.username,
                    "createdAt" to profile.createdAt
                )
                if (profile.bio.isNotBlank()) {
                    userData["bio"] = profile.bio
                }
                if (profile.photoUrl.isNotBlank()) {
                    userData["photoUrl"] = profile.photoUrl
                }

                val usernameData = mapOf(
                    "uid" to profile.uid
                )

                transaction.set(userRef, userData, SetOptions.merge())
                transaction.set(usernameRef, usernameData, SetOptions.merge())
            }.await()

            userProfileCache[profile.uid] = CachedProfileEntry(profile, clockMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserById(userId: String): Result<UserProfile?> = withContext(ioDispatcher) {
        if (userId.isBlank()) return@withContext Result.success(null)

        userProfileCache[userId]?.let { entry ->
            if ((clockMillis() - entry.cachedAtMillis) < PROFILE_CACHE_TTL_MS) {
                return@withContext Result.success(entry.profile)
            } else {
                userProfileCache.remove(userId, entry)
            }
        }

        try {
            val userDoc = db.collection("users").document(userId).get().await()
            if (!userDoc.exists()) {
                return@withContext Result.success(null)
            }

            val username = userDoc.getString("username")?.trim()
            if (username.isNullOrBlank()) {
                return@withContext Result.success(null)
            }

            val bio = (userDoc.get("bio") as? String)?.trim().orEmpty()
            val photoUrl = (userDoc.get("photoUrl") as? String)?.trim().orEmpty()

            val createdAt = try {
                userDoc.getTimestamp("createdAt")?.toDate()
                    ?: userDoc.getDate("createdAt")
                    ?: userDoc.getLong("createdAt")?.let { Date(it) }
                    ?: Date()
            } catch (e: Exception) {
                Date()
            }

            val profile = UserProfile(
                uid = userId,
                username = username,
                createdAt = createdAt,
                bio = bio,
                photoUrl = photoUrl
            )
            userProfileCache[userId] = CachedProfileEntry(profile, clockMillis())
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateBio(userId: String, bio: String): Result<Unit> = withContext(ioDispatcher) {
        if (userId.isBlank()) return@withContext Result.failure(IllegalArgumentException("El userId no puede estar vacío."))
        try {
            val trimmedBio = bio.trim()
            db.collection("users").document(userId)
                .update("bio", trimmedBio)
                .await()

            userProfileCache[userId]?.let { entry ->
                userProfileCache[userId] = CachedProfileEntry(
                    profile = entry.profile.copy(bio = trimmedBio),
                    cachedAtMillis = clockMillis()
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updatePhotoUrl(userId: String, photoUrl: String): Result<Unit> = withContext(ioDispatcher) {
        if (userId.isBlank()) return@withContext Result.failure(IllegalArgumentException("El userId no puede estar vacío."))
        try {
            val trimmedUrl = photoUrl.trim()
            db.collection("users").document(userId)
                .update("photoUrl", trimmedUrl)
                .await()

            userProfileCache[userId]?.let { entry ->
                userProfileCache[userId] = CachedProfileEntry(
                    profile = entry.profile.copy(photoUrl = trimmedUrl),
                    cachedAtMillis = clockMillis()
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteProfile(userId: String, username: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            userProfileCache.remove(userId)
            val batch = db.batch()
            batch.delete(db.collection("users").document(userId))

            var targetUsername = username
            if (targetUsername.isBlank()) {
                val usernameDoc = db.collection("usernames")
                    .whereEqualTo("uid", userId)
                    .limit(1)
                    .get()
                    .await()
                targetUsername = usernameDoc.documents.firstOrNull()?.id ?: ""
            }

            if (targetUsername.isNotBlank()) {
                batch.delete(db.collection("usernames").document(targetUsername))
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
