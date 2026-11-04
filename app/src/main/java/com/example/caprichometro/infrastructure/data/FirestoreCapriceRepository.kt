package com.example.caprichometro.infrastructure.data

import android.util.Log
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

class FirestoreCapriceRepository : CapriceRepository {

    private val db = FirebaseFirestore.getInstance()
    private val collectionName = "caprices"

    override suspend fun save(caprice: Caprice): Result<String> {
        return try {
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
            val document = db.collection(collectionName).add(data).await()
            Result.success(document.id)
        } catch (e: Exception) {
            Log.e("FirestoreCapriceRepo", "Error guardando capricho: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getCaprices(userId: String, limit: Int): Result<List<Caprice>> {
        return try {
            val documents = fetchCapriceDocuments(userId, limit)
            val caprices = documents
                .mapNotNull { doc -> parseCapriceDocument(doc) }
                .sortedByDescending { it.timestamp }
                .take(limit)

            Log.d("FirestoreCapriceRepo", "getCaprices(userId=$userId, limit=$limit) -> ${caprices.size} caprichos")
            Result.success(caprices)
        } catch (e: Exception) {
            Log.e("FirestoreCapriceRepo", "Error obteniendo caprichos para $userId: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchCapriceDocuments(userId: String, limit: Int): List<DocumentSnapshot> {
        // 1. Intenta consulta indexada desde el servidor (userId + orderBy timestamp DESC)
        try {
            val indexedQuery = db.collection(collectionName)
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())

            val snapshot = try {
                indexedQuery.get(Source.SERVER).await()
            } catch (_: Exception) {
                indexedQuery.get().await()
            }
            return snapshot.documents
        } catch (indexedError: Exception) {
            // 2. Fallback sin índice compuesto (usa índice simple automático por userId y ordena en memoria)
            Log.w(
                "FirestoreCapriceRepo",
                "Consulta con orderBy requirió índice compuesto o falló (${indexedError.message}); usando fallback por userId."
            )
            val fallbackQuery = db.collection(collectionName)
                .whereEqualTo("userId", userId)

            val snapshot = try {
                fallbackQuery.get(Source.SERVER).await()
            } catch (_: Exception) {
                fallbackQuery.get().await()
            }
            return snapshot.documents
        }
    }

    private fun parseCapriceDocument(doc: DocumentSnapshot): Caprice? {
        return try {
            val rawTimestamp = doc.get("timestamp")
            val parsedTimestamp = when (rawTimestamp) {
                is Number -> rawTimestamp.toLong()
                is Timestamp -> rawTimestamp.toDate().time
                is String -> rawTimestamp.toLongOrNull() ?: 0L
                else -> 0L
            }

            val question = doc.getString("question")?.trim().orEmpty()
            val answer1 = doc.getString("answer1")?.trim().orEmpty()
            val answer2 = doc.getString("answer2")?.trim().orEmpty()
            if (question.isEmpty() || answer1.isEmpty() || answer2.isEmpty()) {
                return null
            }

            Caprice(
                id = doc.id,
                userId = doc.getString("userId") ?: "",
                type = doc.getString("type") ?: "Compra",
                question = question,
                answer1 = answer1,
                answer2 = answer2,
                imageUrl = doc.getString("imageUrl").orEmpty(),
                timestamp = parsedTimestamp
            )
        } catch (e: Exception) {
            Log.e("FirestoreCapriceRepo", "Documento caprice inválido ${doc.id}: ${e.message}", e)
            null
        }
    }
}
