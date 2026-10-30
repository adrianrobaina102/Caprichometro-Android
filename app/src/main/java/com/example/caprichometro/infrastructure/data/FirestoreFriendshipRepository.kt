package com.example.caprichometro.infrastructure.data

import com.example.caprichometro.domain.friendship.Friendship
import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.friendship.FriendshipStatus
import com.example.caprichometro.domain.model.UserProfile
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

class FirestoreFriendshipRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : FriendshipRepository {

    private fun blockReference(blockerId: String, blockedUserId: String): DocumentReference {
        return db.collection("userBlocks")
            .document(blockerId)
            .collection("blockedUsers")
            .document(blockedUserId)
    }

    private fun isBlockedInTransaction(
        transaction: Transaction,
        userA: String,
        userB: String
    ): Boolean {
        val outgoing = transaction.get(blockReference(userA, userB))
        val incoming = transaction.get(blockReference(userB, userA))
        return outgoing.exists() || incoming.exists()
    }

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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendFriendRequest(friendship: Friendship): Result<Unit> {
        return try {
            val sortedMembers = friendship.memberIDs.sorted()
            val friendshipId = sortedMembers.joinToString("_")
            val requesterId = friendship.requestedBy
            val recipientId = sortedMembers.firstOrNull { it != requesterId }
                ?: return Result.failure(IllegalArgumentException("Destinatario inválido"))

            val friendshipRef = db.collection("friendships").document(friendshipId)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(friendshipRef)
                if (isBlockedInTransaction(transaction, requesterId, recipientId)) {
                    throw IllegalStateException("No se puede enviar una solicitud a un usuario bloqueado.")
                }

                if (snapshot.exists()) {
                    val existingStatus = snapshot.getString("status")?.lowercase()
                    val canResend = existingStatus == "declined" || existingStatus == "rejected"
                    if (!canResend) {
                        throw IllegalStateException("Ya existe una relación entre estos usuarios.")
                    }
                }

                val data = hashMapOf(
                    "id" to friendshipId,
                    "memberIDs" to sortedMembers,
                    "requestedBy" to requesterId,
                    "status" to FriendshipStatus.PENDING.value,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                transaction.set(friendshipRef, data)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateFriendshipStatus(
        friendshipId: String,
        status: FriendshipStatus
    ): Result<Unit> {
        return try {
            val friendshipRef = db.collection("friendships").document(friendshipId)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(friendshipRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("La solicitud ya no existe.")
                }

                val rawMembers = snapshot.get("memberIDs") as? List<*>
                val members = rawMembers?.mapNotNull { it?.toString() } ?: emptyList()
                if (members.size == 2 && isBlockedInTransaction(transaction, members[0], members[1])) {
                    throw IllegalStateException("No se puede responder a una solicitud entre usuarios bloqueados.")
                }

                transaction.update(
                    friendshipRef,
                    mapOf(
                        "status" to status.value,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeFriendship(userId: String, friendId: String): Result<Unit> {
        return try {
            val friendshipId = listOf(userId, friendId).sorted().joinToString("_")
            val friendshipRef = db.collection("friendships").document(friendshipId)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(friendshipRef)
                if (!snapshot.exists()) {
                    return@runTransaction null
                }

                val status = snapshot.getString("status")?.lowercase()
                val rawMembers = snapshot.get("memberIDs") as? List<*>
                val members = rawMembers?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()

                if (status != "accepted" || members != setOf(userId, friendId)) {
                    throw IllegalStateException("La relación ha cambiado. Actualiza la lista de amigos.")
                }

                transaction.delete(friendshipRef)
                null
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun blockUser(blockerId: String, blockedUserId: String): Result<Unit> {
        return try {
            val friendshipId = listOf(blockerId, blockedUserId).sorted().joinToString("_")
            val friendshipRef = db.collection("friendships").document(friendshipId)
            val blockRef = blockReference(blockerId, blockedUserId)

            db.runTransaction { transaction ->
                val friendshipSnap = transaction.get(friendshipRef)
                val existingBlockSnap = transaction.get(blockRef)

                if (friendshipSnap.exists()) {
                    val rawMembers = friendshipSnap.get("memberIDs") as? List<*>
                    val members = rawMembers?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()
                    if (members.size != 2 || members != setOf(blockerId, blockedUserId)) {
                        throw IllegalStateException("Datos de amistad inválidos.")
                    }
                }

                if (!existingBlockSnap.exists()) {
                    transaction.set(
                        blockRef,
                        mapOf("createdAt" to FieldValue.serverTimestamp())
                    )
                }

                if (friendshipSnap.exists()) {
                    transaction.delete(friendshipRef)
                }
                null
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isBlocked(userId: String, otherUserId: String): Result<Boolean> {
        return try {
            val outgoing = blockReference(userId, otherUserId).get().await()
            if (outgoing.exists()) return Result.success(true)
            val incoming = blockReference(otherUserId, userId).get().await()
            Result.success(incoming.exists())
        } catch (e: Exception) {
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
