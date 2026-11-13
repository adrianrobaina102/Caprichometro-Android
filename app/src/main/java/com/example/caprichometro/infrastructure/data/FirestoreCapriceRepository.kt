package com.example.caprichometro.infrastructure.data

import android.util.Log
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreCapriceRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CapriceRepository {

    private val collectionName = "caprices"

    override suspend fun save(caprice: Caprice): Result<String> = withContext(ioDispatcher) {
        try {
            val data = mutableMapOf<String, Any>(
                "userId" to caprice.userId,
                "type" to caprice.type,
                "question" to caprice.question,
                "answer1" to caprice.answer1,
                "answer2" to caprice.answer2,
                "timestamp" to caprice.timestamp
            )
            if (caprice.imageUrl.isNotBlank()) {
                data["imageUrl"] = caprice.imageUrl
            }
            if (caprice.visibleToUserIds.isNotEmpty()) {
                data["visibleToUserIds"] = (listOf(caprice.userId) + caprice.visibleToUserIds)
                    .filter { it.isNotBlank() }
                    .distinct()
            }
            val document = db.collection(collectionName).add(data).await()
            Result.success(document.id)
        } catch (e: Exception) {
            Log.e("FirestoreCapriceRepo", "Error guardando capricho: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getCaprices(userId: String, limit: Int): Result<List<Caprice>> =
        withContext(ioDispatcher) {
            try {
                val documents = fetchCapriceDocuments(userId, limit)
                val caprices = documents
                    .mapNotNull { doc -> doc.toCapriceOrNull("FirestoreCapriceRepo") }
                    .sortedByDescending { it.timestamp }
                    .take(limit)

                Result.success(caprices)
            } catch (e: Exception) {
                Log.e("FirestoreCapriceRepo", "Error obteniendo caprichos para $userId: ${e.message}", e)
                Result.failure(e)
            }
        }

    override suspend fun deleteCapricesForUser(userId: String): Result<Unit> =
        withContext(ioDispatcher) {
            if (userId.isBlank()) return@withContext Result.success(Unit)
            try {
                val snapshot = db.collection(collectionName)
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()

                for (doc in snapshot.documents) {
                    val votesSnapshot = doc.reference.collection("votes").get().await()
                    if (!votesSnapshot.isEmpty) {
                        votesSnapshot.documents.chunked(450).forEach { chunk ->
                            val batch = db.batch()
                            chunk.forEach { voteDoc ->
                                batch.delete(voteDoc.reference)
                            }
                            batch.commit().await()
                        }
                    }
                    doc.reference.delete().await()
                }
                Log.d("FirestoreCapriceRepo", "Eliminados ${snapshot.size()} caprichos del usuario $userId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("FirestoreCapriceRepo", "Error eliminando caprichos del usuario $userId: ${e.message}", e)
                Result.failure(e)
            }
        }

    override suspend fun updateFinalDecision(
        capriceId: String,
        decision: Int,
        decidedAt: Long
    ): Result<Unit> = withContext(ioDispatcher) {
        if (capriceId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("ID de capricho inválido"))
        }
        try {
            db.collection(collectionName)
                .document(capriceId)
                .update(
                    mapOf(
                        "finalDecision" to decision,
                        "finalDecisionAt" to decidedAt
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreCapriceRepo", "Error guardando decisión final para $capriceId: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchCapriceDocuments(userId: String, limit: Int): List<DocumentSnapshot> {
        return try {
            val query = db.collection(collectionName).whereEqualTo("userId", userId)
            val snapshot = query.get().await()
            snapshot.documents
        } catch (e: Exception) {
            Log.w("FirestoreCapriceRepo", "Error en fetchCapriceDocuments para $userId: ${e.message}")
            emptyList()
        }
    }
}
