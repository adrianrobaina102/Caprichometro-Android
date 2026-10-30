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
            var username = if (userDoc.exists()) userDoc.getString("username") else null

            // Si no se encuentra el username en users/{id} (documento ausente o campo vacío),
            // consultamos la colección pública 'usernames' que mapea {uid} con el ID del documento
            if (username.isNullOrBlank()) {
                val usernameSnapshot = db.collection("usernames")
                    .whereEqualTo("uid", userId)
                    .limit(1)
                    .get()
                    .await()
                username = usernameSnapshot.documents.firstOrNull()?.id
            }

            if (username.isNullOrBlank()) {
                return Result.success(null)
            }

            val createdAt = try {
                userDoc.getDate("createdAt") ?: userDoc.getLong("createdAt")?.let { Date(it) } ?: Date()
            } catch (e: Exception) {
                Date()
            }

            val profile = UserProfile(
                uid = userId,
                username = username,
                createdAt = createdAt
            )
            Result.success(profile)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreUserProfile", "Error consultando users/$userId: ${e.message}")
            try {
                val usernameSnapshot = db.collection("usernames")
                    .whereEqualTo("uid", userId)
                    .limit(1)
                    .get()
                    .await()
                val foundUsername = usernameSnapshot.documents.firstOrNull()?.id
                if (!foundUsername.isNullOrBlank()) {
                    return Result.success(
                        UserProfile(
                            uid = userId,
                            username = foundUsername,
                            createdAt = Date()
                        )
                    )
                }
            } catch (fallbackError: Exception) {
                android.util.Log.e("FirestoreUserProfile", "Fallback a usernames falló: ${fallbackError.message}")
            }
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
