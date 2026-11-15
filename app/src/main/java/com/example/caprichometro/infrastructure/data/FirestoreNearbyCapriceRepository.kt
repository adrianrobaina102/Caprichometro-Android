package com.example.caprichometro.infrastructure.data

import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.repository.NearbyCapriceRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreNearbyCapriceRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : NearbyCapriceRepository {

    companion object {
        const val PAGE_SIZE = 20
        private const val COLLECTION_NAME = "caprices"
    }

    override suspend fun getDiscoverableCaprices(): Result<List<Caprice>> = withContext(ioDispatcher) {
        try {
            val collected = mutableListOf<Caprice>()
            var lastDocument: DocumentSnapshot? = null

            while (true) {
                var query = db.collection(COLLECTION_NAME)
                    .whereNotEqualTo("nearbyCell", "")
                    .orderBy("nearbyCell")
                    .limit(PAGE_SIZE.toLong())

                if (lastDocument != null) {
                    query = query.startAfter(lastDocument)
                }

                val snapshot = query.get(Source.SERVER).await()
                val documents = snapshot.documents
                for (doc in documents) {
                    doc.toCapriceOrNull("FirestoreNearbyCapriceRepo")?.let { caprice ->
                        if (!caprice.nearbyCell.isNullOrBlank()) {
                            collected.add(caprice)
                        }
                    }
                }

                if (documents.size < PAGE_SIZE) {
                    break
                }
                lastDocument = documents.lastOrNull() ?: break
            }

            Result.success(collected)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
