package com.example.caprichometro.infrastructure.data

import android.util.Log
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.repository.CapriceObservationRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreCapriceObservationRepository : CapriceObservationRepository {

    private val db = FirebaseFirestore.getInstance()
    private val collectionName = "caprices"

    override fun observeFriendCaprices(friendUserId: String, sinceTimestamp: Long): Flow<Caprice> = callbackFlow {
        if (friendUserId.isBlank()) {
            close()
            return@callbackFlow
        }

        // Filtramos por userId sin forzar índice compuesto para máxima fiabilidad
        val query = db.collection(collectionName)
            .whereEqualTo("userId", friendUserId)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("FirestoreCapriceObs", "Error observando caprichos de $friendUserId: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            for (change in snapshot.documentChanges) {
                if (change.type == DocumentChange.Type.ADDED) {
                    val caprice = parseCapriceDocument(change.document)
                    // Solo emitimos los caprichos que se hayan creado después del inicio de la sesión en primer plano
                    if (caprice != null && caprice.timestamp > sinceTimestamp) {
                        Log.d("FirestoreCapriceObs", "Nuevo capricho detectado en directo de $friendUserId: ${caprice.id} (${caprice.question})")
                        trySend(caprice)
                    }
                }
            }
        }

        awaitClose {
            registration.remove()
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
            Log.e("FirestoreCapriceObs", "Error parseando documento ${doc.id}: ${e.message}", e)
            null
        }
    }
}
