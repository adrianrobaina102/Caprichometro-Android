package com.example.caprichometro.application.usecase

import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.model.ApproximateLocation
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.NearbyCellGrid
import com.example.caprichometro.domain.model.ProgressiveFeedBatch
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.domain.model.sortNearbyFeedItems
import com.example.caprichometro.domain.repository.DeviceLocationRepository
import com.example.caprichometro.domain.repository.NearbyCapriceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class GetNearbyCapricesUseCase(
    private val locationRepository: DeviceLocationRepository,
    private val nearbyCapriceRepository: NearbyCapriceRepository,
    private val userProfileRepository: UserProfileRepository,
    private val friendshipRepository: FriendshipRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val MAX_CONCURRENT_AUTHOR_QUERIES = 8
        private const val INITIAL_FAST_BATCH_SIZE = 5
        private const val PAGE_SIZE = 20
    }

    private data class Candidate(
        val caprice: Caprice,
        val distanceMeters: Double
    )

    suspend fun execute(currentUserId: String): Result<List<CapriceFeedItem>> = withContext(ioDispatcher) {
        if (currentUserId.isBlank()) return@withContext Result.success(emptyList())

        try {
            val location = locationRepository.currentApproximateLocation().getOrThrow()
            val (discoverableCaprices, outgoingBlockedIds) = coroutineScope {
                val capricesDeferred = async { nearbyCapriceRepository.getDiscoverableCaprices().getOrThrow() }
                val blockedDeferred = async {
                    friendshipRepository
                        ?.getBlockedUserIds(currentUserId)
                        ?.getOrDefault(emptyList())
                        ?.toSet()
                        .orEmpty()
                }
                capricesDeferred.await() to blockedDeferred.await()
            }
            val items = buildSortedFeedItems(
                currentUserId = currentUserId,
                location = location,
                rawCaprices = discoverableCaprices,
                blockedIds = outgoingBlockedIds,
                resolvedAuthors = ConcurrentHashMap(),
                useCacheOnlyProfiles = false
            )
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeProgressiveFeed(
        currentUserId: String,
        includeCache: Boolean = true,
        initialBatchSize: Int = INITIAL_FAST_BATCH_SIZE,
        pageSize: Int = PAGE_SIZE
    ): Flow<ProgressiveFeedBatch> = channelFlow {
        if (currentUserId.isBlank()) {
            send(ProgressiveFeedBatch(items = emptyList(), isFromCache = false, isComplete = true))
            return@channelFlow
        }

        val locationDeferred = async(ioDispatcher) { locationRepository.currentApproximateLocation().getOrThrow() }
        val cachedBlockedDeferred = if (includeCache) {
            async(ioDispatcher) {
                friendshipRepository?.getCachedBlockedUserIds(currentUserId)?.toSet().orEmpty()
            }
        } else null
        val serverBlockedDeferred = async(ioDispatcher) {
            friendshipRepository
                ?.getBlockedUserIds(currentUserId)
                ?.getOrDefault(emptyList())
                ?.toSet()
                .orEmpty()
        }
        val resolvedAuthors = ConcurrentHashMap<String, UserProfile>()

        nearbyCapriceRepository.streamDiscoverableCaprices(
            initialBatchSize = initialBatchSize,
            pageSize = pageSize,
            includeCache = includeCache
        ).collect { rawBatch ->
            val location = locationDeferred.await()
            val blockedIds = if (rawBatch.isFromCache && cachedBlockedDeferred != null) {
                cachedBlockedDeferred.await()
            } else {
                serverBlockedDeferred.await()
            }
            val sortedItems = buildSortedFeedItems(
                currentUserId = currentUserId,
                location = location,
                rawCaprices = rawBatch.caprices,
                blockedIds = blockedIds,
                resolvedAuthors = resolvedAuthors,
                useCacheOnlyProfiles = rawBatch.isFromCache
            )
            send(
                ProgressiveFeedBatch(
                    items = sortedItems,
                    isFromCache = rawBatch.isFromCache,
                    isComplete = rawBatch.isComplete
                )
            )
        }
    }.flowOn(ioDispatcher)

    private suspend fun buildSortedFeedItems(
        currentUserId: String,
        location: ApproximateLocation,
        rawCaprices: List<Caprice>,
        blockedIds: Set<String>,
        resolvedAuthors: ConcurrentHashMap<String, UserProfile>,
        useCacheOnlyProfiles: Boolean
    ): List<CapriceFeedItem> {
        val candidates = rawCaprices.mapNotNull { caprice ->
            val cell = caprice.nearbyCell?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (caprice.userId == currentUserId || caprice.userId.isBlank()) return@mapNotNull null
            if (caprice.userId in blockedIds) return@mapNotNull null
            val distance = NearbyCellGrid.distanceInMeters(location, cell) ?: return@mapNotNull null
            Candidate(caprice = caprice, distanceMeters = distance)
        }

        val missingAuthorIds = candidates
            .map { it.caprice.userId }
            .distinct()
            .filter { !resolvedAuthors.containsKey(it) }
        if (missingAuthorIds.isNotEmpty()) {
            val semaphore = Semaphore(MAX_CONCURRENT_AUTHOR_QUERIES)
            coroutineScope {
                missingAuthorIds.map { authorId ->
                    async(ioDispatcher) {
                        semaphore.withPermit {
                            val profile = if (useCacheOnlyProfiles) {
                                userProfileRepository.getCachedUserById(authorId)
                            } else {
                                userProfileRepository.getUserById(authorId).getOrNull()
                            }
                            if (profile != null) {
                                resolvedAuthors[authorId] = profile
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        return sortNearbyFeedItems(
            candidates.map { candidate ->
                CapriceFeedItem(
                    caprice = candidate.caprice,
                    author = resolvedAuthors[candidate.caprice.userId]
                        ?: UserProfile(uid = candidate.caprice.userId, username = "usuario"),
                    canOpenAuthorProfile = false,
                    approximateDistanceMeters = candidate.distanceMeters
                )
            }
        )
    }
}
