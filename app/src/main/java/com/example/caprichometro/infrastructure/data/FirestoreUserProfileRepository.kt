package com.example.caprichometro.infrastructure.data

import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date

class FirestoreUserProfileRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : UserProfileRepository {

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

                val userData = mapOf(
                    "username" to profile.username,
                    "createdAt" to profile.createdAt
                )
                val usernameData = mapOf(
                    "uid" to profile.uid
                )

                transaction.set(userRef, userData)
                transaction.set(usernameRef, usernameData)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserById(userId: String): Result<UserProfile?> {
        return try {
            val userDoc = db.collection("users").document(userId).get().await()
            if (!userDoc.exists()) {
                return Result.success(null)
            }

            val username = userDoc.getString("username")?.trim()
            if (username.isNullOrBlank()) {
                return Result.success(null)
            }

            val createdAt = try {
                userDoc.getDate("createdAt") ?: userDoc.getLong("createdAt")?.let { Date(it) }
            } catch (e: Exception) {
                null
            } ?: return Result.success(null)

            val profile = UserProfile(
                uid = userId,
                username = username,
                createdAt = createdAt
            )
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteProfile(userId: String, username: String): Result<Unit> {
        return try {
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
