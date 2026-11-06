package com.example.caprichometro.infrastructure.data

import android.util.Log
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.repository.CapriceDetailsRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

class FirestoreCapriceDetailsRepository : CapriceDetailsRepository {

    private val db = FirebaseFirestore.getInstance()
    private val collectionName = "caprices"

    override suspend fun getCapriceById(capriceId: String): Result<Caprice?> {
        if (capriceId.isBlank()) return Result.success(null)

        return try {
            val docRef = db.collection(collectionName).document(capriceId)
            val snapshot = try {
                docRef.get(Source.SERVER).await()
            } catch (_: Exception) {
                docRef.get().await()
            }

            if (!snapshot.exists()) {
                Log.d("FirestoreCapriceDetails", "Capricho no encontrado con id: $capriceId")
                Result.success(null)
            } else {
                val caprice = parseCapriceDocument(snapshot)
                Result.success(caprice)
            }
        } catch (e: Exception) {
            Log.e("FirestoreCapriceDetails", "Error obteniendo capricho con id $capriceId: ${e.message}", e)
            Result.failure(e)
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
            Log.e("FirestoreCapriceDetails", "Error parseando documento ${doc.id}: ${e.message}", e)
            null
        }
    }
}
