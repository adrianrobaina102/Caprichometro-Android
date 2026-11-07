package com.example.caprichometro.infrastructure.data

import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

class FirestoreUserProfileRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : UserProfileRepository {

    // Caché en memoria para evitar llamadas redundantes de red a users/{uid}
    private val userProfileCache = ConcurrentHashMap<String, UserProfile>()

    override suspend fun isUsernameAvailable(username: String): Result<Boolean> {
        return try {
            val document = db.collection("usernames").document(username).get().await()
            Result.success(!document.exists())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> {
        return try {
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

                val usernameData = mapOf(
                    "uid" to profile.uid
                )

                transaction.set(userRef, userData)
                transaction.set(usernameRef, usernameData)
            }.await()

            userProfileCache[profile.uid] = profile
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserById(userId: String): Result<UserProfile?> {
        if (userId.isBlank()) return Result.success(null)

        userProfileCache[userId]?.let { cached ->
            return Result.success(cached)
        }

        return try {
            val userDoc = db.collection("users").document(userId).get().await()
            if (!userDoc.exists()) {
                return Result.success(null)
            }

            val username = userDoc.getString("username")?.trim()
            if (username.isNullOrBlank()) {
                return Result.success(null)
            }

            val bio = userDoc.getString("bio")?.trim().orEmpty()

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
                bio = bio
            )
            userProfileCache[userId] = profile
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateBio(userId: String, bio: String): Result<Unit> {
        if (userId.isBlank()) return Result.failure(IllegalArgumentException("El userId no puede estar vacío."))
        return try {
            val trimmedBio = bio.trim()
            db.collection("users").document(userId)
                .update("bio", trimmedBio)
                .await()

            userProfileCache[userId]?.let { current ->
                userProfileCache[userId] = current.copy(bio = trimmedBio)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteProfile(userId: String, username: String): Result<Unit> {
        return try {
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
