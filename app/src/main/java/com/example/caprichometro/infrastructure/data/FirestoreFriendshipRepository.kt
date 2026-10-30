package com.example.caprichometro.infrastructure.data

import com.example.caprichometro.domain.friendship.Friendship
import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.friendship.FriendshipStatus
import com.example.caprichometro.domain.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

class FirestoreFriendshipRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
): FriendshipRepository {
    override suspend fun searchUserByUsername(username: String): Result<UserProfile?> {
        return try {
            val usernameDoc = db.collection("usernames").document(username).get().await()
            if (!usernameDoc.exists()) {
                return Result.success(null)
            }
            val uid = usernameDoc.getString("uid") ?: return Result.failure(Exception("UID no encontrado"))
            val userDoc = db.collection("users").document(uid).get().await()
            if (!userDoc.exists()) {
                return Result.success(null)
            }

            val createdAt = try {
                userDoc.getDate("createdAt") ?: userDoc.getLong("createdAt")?.let { Date(it) } ?: Date()
            } catch (e: Exception) {
                userDoc.getLong("createdAt")?.let { Date(it) } ?: Date()
            }

            val profile = UserProfile(
                uid = uid,
                username = userDoc.getString("username") ?: username,
                createdAt = createdAt
            )
            Result.success(profile)
        }catch (e: Exception) {
            Result.failure(e)
        }

    }

    override suspend fun sendFriendRequest(friendship: Friendship): Result<Unit> {
       return try {
           val data = hashMapOf(
               "id" to friendship.id,
               "memberIDs" to friendship.memberIDs,
               "requestedBy" to friendship.requestedBy,
               "status" to friendship.status.value,
               "createdAt" to friendship.createdAt,
               "updatedAt" to friendship.updatedAt
           )
           db.collection("friendships").document(friendship.id).set(data).await()
           Result.success(Unit)
       }catch (e: Exception) {
           Result.failure(e)
       }
    }

    override suspend fun updateFriendshipStatus(
        friendshipId: String,
        status: FriendshipStatus
    ): Result<Unit> {
        return try {
            db.collection("friendships").document(friendshipId)
                .update(
                    "status", status.value,
                    "updatedAt", Date()
                ).await()
            Result.success(Unit)
        }catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFriendshipsForUser(userId: String): Result<Unit> {
        return try {
            val snapshot = db.collection("friendships")
                .whereArrayContains("memberIDs", userId)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                val batch = db.batch()
                snapshot.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getFriendships(userId: String): Flow<List<Friendship>> = callbackFlow {
        val subscription = db.collection("friendships")
            .whereArrayContains("memberIDs", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val isLoggedOut = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED && isLoggedOut) {
                        close()
                        return@addSnapshotListener
                    }
                    android.util.Log.e("FirestoreFriendship", "Error en SnapshotListener: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val friendships = snapshot.documents.mapNotNull { doc ->
                        try {
                            val id = doc.getString("id") ?: doc.id

                            val rawMembers = doc.get("memberIDs") as? List<*>
                            val memberIDs = rawMembers?.mapNotNull { it?.toString() }
                                ?: run {
                                    val u1 = doc.getString("user1Id")
                                    val u2 = doc.getString("user2Id")
                                    if (u1 != null && u2 != null) listOf(u1, u2) else null
                                }
                                ?: return@mapNotNull null

                            val requestedBy = doc.getString("requestedBy")
                                ?: doc.getString("senderId")
                                ?: doc.getString("user1Id")
                                ?: memberIDs.firstOrNull()
                                ?: ""

                            val statusString = doc.getString("status") ?: "pending"
                            val mappedStatus = FriendshipStatus.entries.firstOrNull {
                                it.value.equals(statusString, ignoreCase = true) || it.name.equals(statusString, ignoreCase = true)
                            } ?: FriendshipStatus.PENDING

                            val createdAt = try {
                                doc.getDate("createdAt") ?: doc.getLong("createdAt")?.let { Date(it) } ?: Date()
                            } catch (e: Exception) {
                                doc.getLong("createdAt")?.let { Date(it) } ?: Date()
                            }

                            val updatedAt = try {
                                doc.getDate("updatedAt") ?: doc.getLong("updatedAt")?.let { Date(it) } ?: Date()
                            } catch (e: Exception) {
                                doc.getLong("updatedAt")?.let { Date(it) } ?: Date()
                            }

                            Friendship(
                                id = id,
                                memberIDs = memberIDs,
                                requestedBy = requestedBy,
                                status = mappedStatus,
                                createdAt = createdAt,
                                updatedAt = updatedAt
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("FirestoreFriendship", "Error parseando amistad ${doc.id}: ${e.message}", e)
                            null
                        }
                    }
                    trySend(friendships).isSuccess
                }
            }
        awaitClose { subscription.remove() }
    }
}
