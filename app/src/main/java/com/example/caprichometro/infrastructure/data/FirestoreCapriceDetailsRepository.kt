package com.example.caprichometro.infrastructure.data

import android.util.Log
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.repository.CapriceDetailsRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreCapriceDetailsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CapriceDetailsRepository {

    private val collectionName = "caprices"

    override suspend fun getCapriceById(capriceId: String): Result<Caprice?> = withContext(ioDispatcher) {
        if (capriceId.isBlank()) return@withContext Result.success(null)

        try {
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
                val caprice = snapshot.toCapriceOrNull("FirestoreCapriceDetails")
                Result.success(caprice)
            }
        } catch (e: Exception) {
            Log.e("FirestoreCapriceDetails", "Error obteniendo capricho con id $capriceId: ${e.message}", e)
            Result.failure(e)
        }
    }
}
