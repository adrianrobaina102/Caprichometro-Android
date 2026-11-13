package com.example.caprichometro.infrastructure.data

import com.example.caprichometro.domain.friendship.Friendship
import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.friendship.FriendshipStatus
import com.example.caprichometro.domain.model.UserProfile
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

class FirestoreFriendshipRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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

    override suspend fun searchUserByUsername(username: String): Result<UserProfile?> = withContext(ioDispatcher) {
        try {
            val usernameDoc = db.collection("usernames").document(username).get().await()
            if (!usernameDoc.exists()) {
                return@withContext Result.success(null)
            }
            val uid = usernameDoc.getString("uid") ?: return@withContext Result.failure(Exception("UID no encontrado"))
            val userDoc = db.collection("users").document(uid).get().await()
            if (!userDoc.exists()) {
                return@withContext Result.success(null)
            }

            val resolvedUsername = userDoc.getString("username")?.trim()
            if (resolvedUsername.isNullOrBlank()) {
                return@withContext Result.success(null)
            }

            val createdAt = try {
                userDoc.getDate("createdAt") ?: userDoc.getLong("createdAt")?.let { Date(it) } ?: Date()
            } catch (e: Exception) {
                userDoc.getLong("createdAt")?.let { Date(it) } ?: Date()
            }

            val photoUrl = (userDoc.get("photoUrl") as? String)?.trim().orEmpty()

            val profile = UserProfile(
                uid = uid,
                username = resolvedUsername,
                createdAt = createdAt,
                photoUrl = photoUrl
            )
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendFriendRequest(friendship: Friendship): Result<Unit> = withContext(ioDispatcher) {
        try {
            val sortedMembers = friendship.memberIDs.sorted()
            val friendshipId = sortedMembers.joinToString("_")
            val requesterId = friendship.requestedBy
            val recipientId = sortedMembers.firstOrNull { it != requesterId }
                ?: return@withContext Result.failure(IllegalArgumentException("Destinatario inválido"))

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
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
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

    override suspend fun removeFriendship(userId: String, friendId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
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

    override suspend fun blockUser(blockerId: String, blockedUserId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
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

    override suspend fun unblockUser(blockerId: String, blockedUserId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            blockReference(blockerId, blockedUserId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getBlockedUserIds(userId: String): Result<List<String>> = withContext(ioDispatcher) {
        try {
            val snapshot = db.collection("userBlocks")
                .document(userId)
                .collection("blockedUsers")
                .get()
                .await()
            val ids = snapshot.documents.map { it.id }
            Result.success(ids)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isBlocked(userId: String, otherUserId: String): Result<Boolean> = withContext(ioDispatcher) {
        try {
            val outgoing = blockReference(userId, otherUserId).get().await()
            if (outgoing.exists()) return@withContext Result.success(true)
            val incoming = blockReference(otherUserId, userId).get().await()
            Result.success(incoming.exists())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFriendshipsForUser(userId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val snapshot = db.collection("friendships")
                .whereArrayContains("memberIDs", userId)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                snapshot.documents.chunked(450).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteBlocksForUser(userId: String): Result<Unit> = withContext(ioDispatcher) {
        if (userId.isBlank()) return@withContext Result.success(Unit)
        try {
            val snapshot = db.collection("userBlocks")
                .document(userId)
                .collection("blockedUsers")
                .get()
                .await()

            if (!snapshot.isEmpty) {
                snapshot.documents.chunked(450).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }
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
                            val id = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id

                            val rawMembers = doc.get("memberIDs") as? List<*>
                            val memberIDs = rawMembers
                                ?.mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) }
                                ?.takeIf { it.size == 2 && it[0] != it[1] }
                                ?: return@mapNotNull null

                            val requestedBy = doc.getString("requestedBy")?.trim()
                                ?.takeIf { it.isNotEmpty() && memberIDs.contains(it) }
                                ?: return@mapNotNull null

                            val statusString = doc.getString("status")?.trim() ?: return@mapNotNull null
                            val mappedStatus = when (statusString.lowercase()) {
                                "pending" -> FriendshipStatus.PENDING
                                "accepted" -> FriendshipStatus.ACCEPTED
                                "declined", "rejected" -> FriendshipStatus.DECLINED
                                else -> return@mapNotNull null
                            }

                            val createdAt = try {
                                doc.getDate("createdAt") ?: doc.getLong("createdAt")?.let { Date(it) }
                            } catch (e: Exception) {
                                null
                            } ?: return@mapNotNull null

                            val updatedAt = try {
                                doc.getDate("updatedAt") ?: doc.getLong("updatedAt")?.let { Date(it) }
                            } catch (e: Exception) {
                                null
                            } ?: createdAt

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
    }.flowOn(ioDispatcher)
}
