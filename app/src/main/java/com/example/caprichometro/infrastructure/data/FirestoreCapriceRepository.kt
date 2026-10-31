package com.example.caprichometro.infrastructure.data

import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class FirestoreCapriceRepository : CapriceRepository {

    private val db = FirebaseFirestore.getInstance()
    private val collectionName = "caprices"

    override suspend fun save(caprice: Caprice): Result<String> {
        return try {
            val data = mapOf(
                "userId" to caprice.userId,
                "type" to caprice.type,
                "question" to caprice.question,
                "answer1" to caprice.answer1,
                "answer2" to caprice.answer2,
                "timestamp" to caprice.timestamp
            )
            val document = db.collection(collectionName).add(data).await()
            Result.success(document.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCaprices(userId: String, limit: Int): Result<List<Caprice>> {
        return try {
            val snapshot = db.collection(collectionName)
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val caprices = snapshot.documents.mapNotNull { doc ->
                Caprice(
                    id = doc.id,
                    userId = doc.getString("userId") ?: "",
                    type = doc.getString("type") ?: "",
                    question = doc.getString("question") ?: "",
                    answer1 = doc.getString("answer1") ?: "",
                    answer2 = doc.getString("answer2") ?: "",
                    timestamp = doc.getLong("timestamp") ?: 0L
                )
            }

            Result.success(caprices)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
