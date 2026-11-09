package com.example.caprichometro.infrastructure.data

import android.util.Log
import com.example.caprichometro.domain.model.Vote
import com.example.caprichometro.domain.repository.VoteRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreVoteRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : VoteRepository {

    override suspend fun castVote(vote: Vote): Result<Unit> = withContext(ioDispatcher) {
        try {
            val voteDoc = db.collection("caprices")
                .document(vote.capriceId)
                .collection("votes")
                .document(vote.voterId)

            val data = mapOf(
                "option" to vote.option,
                "voterId" to vote.voterId,
                "timestamp" to vote.timestamp
            )

            voteDoc.set(data).await()
            Log.d("FirestoreVoteRepo", "Voto guardado exitosamente: capriceId=${vote.capriceId}, voter=${vote.voterId}, option=${vote.option}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirestoreVoteRepo", "Error al emitir voto para capricho ${vote.capriceId}: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun observeVotes(capriceId: String): Flow<List<Vote>> = callbackFlow {
        if (capriceId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection("caprices")
            .document(capriceId)
            .collection("votes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val isLoggedOut = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED && isLoggedOut) {
                        Log.d("FirestoreVoteRepo", "Usuario desconectado, manteniendo canal abierto para reconexion")
                        return@addSnapshotListener
                    }
                    Log.w("FirestoreVoteRepo", "Aviso temporal escuchando votos de $capriceId: ${error.message}")
                    return@addSnapshotListener
                }

                val votes = snapshot?.documents?.mapNotNull { doc ->
                    val option = doc.getLong("option")?.toInt() ?: return@mapNotNull null
                    val voterId = doc.getString("voterId") ?: doc.id
                    val timestamp = doc.getLong("timestamp") ?: 0L

                    Vote(
                        capriceId = capriceId,
                        voterId = voterId,
                        option = option,
                        timestamp = timestamp
                    )
                }.orEmpty()

                Log.d("FirestoreVoteRepo", "observeVotes($capriceId) emitio ${votes.size} votos en tiempo real")
                trySend(votes).isSuccess
            }

        awaitClose { listener.remove() }
    }.flowOn(ioDispatcher)

    override suspend fun getVotes(capriceId: String): Result<List<Vote>> = withContext(ioDispatcher) {
        if (capriceId.isBlank()) return@withContext Result.success(emptyList())

        try {
            val snapshot = db.collection("caprices")
                .document(capriceId)
                .collection("votes")
                .get()
                .await()

            val votes = snapshot.documents.mapNotNull { doc ->
                val option = doc.getLong("option")?.toInt() ?: return@mapNotNull null
                val voterId = doc.getString("voterId") ?: doc.id
                val timestamp = doc.getLong("timestamp") ?: 0L

                Vote(
                    capriceId = capriceId,
                    voterId = voterId,
                    option = option,
                    timestamp = timestamp
                )
            }
            Result.success(votes)
        } catch (e: Exception) {
            Log.e("FirestoreVoteRepo", "Error al obtener votos de $capriceId: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getUserVote(capriceId: String, voterId: String): Result<Vote?> = withContext(ioDispatcher) {
        if (capriceId.isBlank() || voterId.isBlank()) return@withContext Result.success(null)

        try {
            val doc = db.collection("caprices")
                .document(capriceId)
                .collection("votes")
                .document(voterId)
                .get()
                .await()

            if (!doc.exists()) {
                Result.success(null)
            } else {
                val option = doc.getLong("option")?.toInt() ?: 1
                val timestamp = doc.getLong("timestamp") ?: 0L
                Result.success(
                    Vote(
                        capriceId = capriceId,
                        voterId = voterId,
                        option = option,
                        timestamp = timestamp
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("FirestoreVoteRepo", "Error al obtener voto de $voterId en $capriceId: ${e.message}", e)
            Result.failure(e)
        }
    }
}
