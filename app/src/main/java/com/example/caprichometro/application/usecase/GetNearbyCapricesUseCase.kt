package com.example.caprichometro.application.usecase

import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.NearbyCellGrid
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.domain.repository.DeviceLocationRepository
import com.example.caprichometro.domain.repository.NearbyCapriceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class GetNearbyCapricesUseCase(
    private val locationRepository: DeviceLocationRepository,
    private val nearbyCapriceRepository: NearbyCapriceRepository,
    private val userProfileRepository: UserProfileRepository,
    private val friendshipRepository: FriendshipRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val MAX_CONCURRENT_AUTHOR_QUERIES = 4
    }

    private data class Candidate(
        val caprice: Caprice,
        val distanceMeters: Double
    )

    suspend fun execute(currentUserId: String): Result<List<CapriceFeedItem>> = withContext(ioDispatcher) {
        if (currentUserId.isBlank()) return@withContext Result.success(emptyList())

        try {
            val location = locationRepository.currentApproximateLocation().getOrThrow()
            val discoverableCaprices = nearbyCapriceRepository.getDiscoverableCaprices().getOrThrow()
            val outgoingBlockedIds = friendshipRepository
                ?.getBlockedUserIds(currentUserId)
                ?.getOrDefault(emptyList())
                ?.toSet()
                .orEmpty()

            val candidates = discoverableCaprices.mapNotNull { caprice ->
                val cell = caprice.nearbyCell?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                if (caprice.userId == currentUserId || caprice.userId.isBlank()) return@mapNotNull null
                if (caprice.userId in outgoingBlockedIds) return@mapNotNull null
                val distance = NearbyCellGrid.distanceInMeters(location, cell) ?: return@mapNotNull null
                Candidate(caprice = caprice, distanceMeters = distance)
            }.sortedWith(
                compareBy<Candidate> { it.distanceMeters }
                    .thenByDescending { it.caprice.timestamp }
                    .thenBy { it.caprice.id }
            )

            if (candidates.isEmpty()) return@withContext Result.success(emptyList())

            val uniqueAuthorIds = candidates.map { it.caprice.userId }.distinct()
            val semaphore = Semaphore(MAX_CONCURRENT_AUTHOR_QUERIES)
            val authorsById = coroutineScope {
                uniqueAuthorIds.map { authorId ->
                    async(ioDispatcher) {
                        semaphore.withPermit {
                            val profile = userProfileRepository.getUserById(authorId).getOrNull()
                                ?: UserProfile(uid = authorId, username = "usuario")
                            authorId to profile
                        }
                    }
                }.awaitAll().toMap()
            }

            val items = candidates.map { candidate ->
                CapriceFeedItem(
                    caprice = candidate.caprice,
                    author = authorsById[candidate.caprice.userId]
                        ?: UserProfile(uid = candidate.caprice.userId, username = "usuario"),
                    canOpenAuthorProfile = false,
                    approximateDistanceMeters = candidate.distanceMeters
                )
            }
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
