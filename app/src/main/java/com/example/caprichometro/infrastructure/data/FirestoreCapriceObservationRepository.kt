package com.example.caprichometro.infrastructure.data

import android.util.Log
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.repository.CapriceObservationRepository
import com.example.caprichometro.domain.repository.FriendCapriceUpdate
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

class FirestoreCapriceObservationRepository : CapriceObservationRepository {

    private val db = FirebaseFirestore.getInstance()
    private val collectionName = "caprices"

    override fun observeFriendCaprices(friendUserId: String, sinceTimestamp: Long): Flow<Caprice> =
        observeFriendUpdates(
            friendUserId = friendUserId,
            includeNewCaprices = true,
            includeFinalDecisions = false,
            sinceTimestamp = sinceTimestamp
        ).map { it.caprice }

    override fun observeFriendFinalDecisions(friendUserId: String, sinceTimestamp: Long): Flow<Caprice> =
        observeFriendUpdates(
            friendUserId = friendUserId,
            includeNewCaprices = false,
            includeFinalDecisions = true,
            sinceTimestamp = sinceTimestamp
        ).map { it.caprice }

    override fun observeFriendUpdates(friendUserId: String, sinceTimestamp: Long): Flow<FriendCapriceUpdate> =
        observeFriendUpdates(
            friendUserId = friendUserId,
            includeNewCaprices = true,
            includeFinalDecisions = true,
            sinceTimestamp = sinceTimestamp
        )

    private fun observeFriendUpdates(
        friendUserId: String,
        includeNewCaprices: Boolean,
        includeFinalDecisions: Boolean,
        sinceTimestamp: Long
    ): Flow<FriendCapriceUpdate> = callbackFlow {
        if (friendUserId.isBlank()) {
            close()
            return@callbackFlow
        }

        val query = db.collection(collectionName)
            .whereEqualTo("userId", friendUserId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(10)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                val isLoggedOut = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null
                if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED && isLoggedOut) {
                    close()
                } else {
                    Log.w("FirestoreCapriceObs", "Error observando caprichos de $friendUserId: ${error.message}")
                    close(error)
                }
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            for (change in snapshot.documentChanges) {
                val isAdded = change.type == DocumentChange.Type.ADDED
                val isModified = change.type == DocumentChange.Type.MODIFIED
                if (!isAdded && !(includeFinalDecisions && isModified)) continue

                val caprice = change.document.toCapriceOrNull("FirestoreCapriceObs") ?: continue

                if (includeNewCaprices && isAdded && caprice.timestamp > sinceTimestamp) {
                    Log.d("FirestoreCapriceObs", "Nuevo capricho detectado en directo de $friendUserId: ${caprice.id} (${caprice.question})")
                    trySend(FriendCapriceUpdate(caprice = caprice, isFinalDecision = false))
                }

                if (includeFinalDecisions) {
                    val decidedAt = caprice.finalDecisionAt ?: 0L
                    if ((caprice.finalDecision == 1 || caprice.finalDecision == 2) && decidedAt > sinceTimestamp) {
                        trySend(FriendCapriceUpdate(caprice = caprice, isFinalDecision = true))
                    }
                }
            }
        }

        awaitClose {
            registration.remove()
        }
    }
}
