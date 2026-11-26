package com.example.caprichometro.infrastructure.data

import com.example.caprichometro.domain.model.ProfileCacheControl
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.repository.UserProfileRepository
import com.example.caprichometro.domain.model.UsernameRules
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

class FirestoreUserProfileRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) : UserProfileRepository, ProfileCacheControl {

    private data class CachedProfileEntry(
        val profile: UserProfile,
        val cachedAtMillis: Long
    )

    companion object {
        internal const val PROFILE_CACHE_TTL_MS = 60_000L
    }

    // Cache en memoria con TTL de 60s para evitar llamadas redundantes y refrescar cambios recientes
    private val userProfileCache = ConcurrentHashMap<String, CachedProfileEntry>()
    private val userFetchLocks = ConcurrentHashMap<String, Mutex>()

    override fun invalidateUser(userId: String) {
        if (userId.isNotBlank()) {
            userProfileCache.remove(userId)
        }
    }

    override fun invalidateAll() {
        userProfileCache.clear()
    }

    override suspend fun isUsernameAvailable(username: String): Result<Boolean> = withContext(ioDispatcher) {
        try {
            val document = db.collection("usernames").document(username).get().await()
            Result.success(!document.exists())
        } catch (e: CancellationException) {
            throw e
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
                val normalizedLocationCell = profile.locationCell?.trim()?.takeIf { it.isNotEmpty() }
                if (normalizedLocationCell != null) {
                    userData["locationCell"] = normalizedLocationCell
                }

                val usernameData = mapOf(
                    "uid" to profile.uid
                )

                transaction.set(userRef, userData, SetOptions.merge())
                transaction.set(usernameRef, usernameData, SetOptions.merge())
            }.await()

            userProfileCache[profile.uid] = CachedProfileEntry(profile, clockMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
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

        val lock = userFetchLocks.computeIfAbsent(userId) { Mutex() }
        lock.withLock {
            userProfileCache[userId]?.let { entry ->
                if ((clockMillis() - entry.cachedAtMillis) < PROFILE_CACHE_TTL_MS) {
                    return@withLock Result.success(entry.profile)
                } else {
                    userProfileCache.remove(userId, entry)
                }
            }

            try {
                val userDoc = db.collection("users").document(userId).get().await()
                val profile = userDoc.toUserProfileOrNull(userId)
                    ?: return@withLock Result.success(null)
                userProfileCache[userId] = CachedProfileEntry(profile, clockMillis())
                Result.success(profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getCachedUserById(userId: String): UserProfile? = withContext(ioDispatcher) {
        if (userId.isBlank()) return@withContext null
        userProfileCache[userId]
            ?.takeIf { (clockMillis() - it.cachedAtMillis) < PROFILE_CACHE_TTL_MS }
            ?.profile?.let { return@withContext it }
        try {
            val doc = db.collection("users").document(userId).get(Source.CACHE).await()
            doc.toUserProfileOrNull(userId)?.also { profile ->
                userProfileCache.putIfAbsent(userId, CachedProfileEntry(profile, clockMillis()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun DocumentSnapshot.toUserProfileOrNull(userId: String): UserProfile? {
        if (!exists()) return null
        val username = getString("username")?.trim()
        if (username.isNullOrBlank()) return null

        val bio = (get("bio") as? String)?.trim().orEmpty()
        val photoUrl = (get("photoUrl") as? String)?.trim().orEmpty()
        val locationCell = (get("locationCell") as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val createdAt = try {
            getTimestamp("createdAt")?.toDate()
                ?: getDate("createdAt")
                ?: getLong("createdAt")?.let { Date(it) }
                ?: Date()
        } catch (_: Exception) {
            Date()
        }

        return UserProfile(
            uid = userId,
            username = username,
            createdAt = createdAt,
            bio = bio,
            photoUrl = photoUrl,
            locationCell = locationCell
        )
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
        } catch (e: CancellationException) {
            throw e
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateLocationCell(userId: String, locationCell: String?): Result<Unit> =
        withContext(ioDispatcher) {
            if (userId.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("El userId no puede estar vacío."))
            }
            val normalizedCell = locationCell?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@withContext Result.success(Unit)
            if (userProfileCache[userId]?.profile?.locationCell == normalizedCell) {
                return@withContext Result.success(Unit)
            }
            try {
                db.collection("users").document(userId)
                    .update("locationCell", normalizedCell)
                    .await()

                userProfileCache[userId]?.let { entry ->
                    userProfileCache[userId] = CachedProfileEntry(
                        profile = entry.profile.copy(locationCell = normalizedCell),
                        cachedAtMillis = clockMillis()
                    )
                }
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun deleteProfile(userId: String, username: String): Result<Unit> = withContext(ioDispatcher) {
        if (userId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("UID de usuario inválido"))
        }
        try {
            userProfileCache.remove(userId)
            val normalizedCandidate = UsernameRules.normalize(username)
            val ownedUsernameDocs = db.collection("usernames")
                .whereEqualTo("uid", userId)
                .get()
                .await()
                .documents
                .map { it.id }
            val candidateUsernames = (ownedUsernameDocs + listOf(normalizedCandidate))
                .filter { it.isNotBlank() }
                .distinct()

            db.runTransaction { transaction ->
                val userRef = db.collection("users").document(userId)
                val usernameRefsToDelete = candidateUsernames.mapNotNull { uname ->
                    val ref = db.collection("usernames").document(uname)
                    val snap = transaction.get(ref)
                    if (snap.exists() && snap.getString("uid") == userId) ref else null
                }
                transaction.delete(userRef)
                usernameRefsToDelete.forEach { ref ->
                    transaction.delete(ref)
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
