package com.example.caprichometro.infrastructure.data

import android.util.Log
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.repository.CapriceObservationRepository
import com.google.firebase.firestore.DocumentChange
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
                    val caprice = change.document.toCapriceOrNull("FirestoreCapriceObs")
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

    override fun observeFriendFinalDecisions(friendUserId: String, sinceTimestamp: Long): Flow<Caprice> = callbackFlow {
        if (friendUserId.isBlank()) {
            close()
            return@callbackFlow
        }

        val query = db.collection(collectionName)
            .whereEqualTo("userId", friendUserId)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("FirestoreCapriceObs", "Error observando decisiones de $friendUserId: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            for (change in snapshot.documentChanges) {
                if (change.type == DocumentChange.Type.ADDED || change.type == DocumentChange.Type.MODIFIED) {
                    val caprice = change.document.toCapriceOrNull("FirestoreCapriceObs")
                    val decidedAt = caprice?.finalDecisionAt ?: 0L
                    if (caprice != null && (caprice.finalDecision == 1 || caprice.finalDecision == 2) && decidedAt > sinceTimestamp) {
                        trySend(caprice)
                    }
                }
            }
        }

        awaitClose {
            registration.remove()
        }
    }
}
