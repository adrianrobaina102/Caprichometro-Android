package com.example.caprichometro.application.usecase

import com.example.caprichometro.application.usecase.friendship.GetAcceptedFriendsUseCase
import com.example.caprichometro.domain.friendship.FriendshipRepository
import com.example.caprichometro.domain.friendship.FriendshipStatus
import com.example.caprichometro.domain.model.ApproximateLocation
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.CapriceRepository
import com.example.caprichometro.domain.model.NearbyCellGrid
import com.example.caprichometro.domain.model.ProgressiveFeedBatch
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.model.UserProfileRepository
import com.example.caprichometro.domain.model.sortFriendsFeedItems
import com.example.caprichometro.domain.repository.DeviceLocationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalCoroutinesApi::class)
class GetFriendsCapricesUseCase(
    private val getAcceptedFriendsUseCase: GetAcceptedFriendsUseCase,
    private val capriceRepository: CapriceRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val friendshipRepository: FriendshipRepository? = null,
    private val userProfileRepository: UserProfileRepository? = null,
    private val locationRepository: DeviceLocationRepository? = null,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val MAX_CONCURRENT_FRIEND_QUERIES = 8
        private const val INITIAL_FAST_BATCH_SIZE = 5
        private const val PROGRESS_EMIT_THROTTLE_MS = 150L
        private const val EARLY_BATCH_FLUSH_DELAY_MS = 200L
        private const val CACHE_LOCATION_TIMEOUT_MS = 80L
    }

    suspend fun execute(
        currentUserId: String,
        perFriendLimit: Int = 20,
        totalLimit: Int = 20
    ): Result<List<CapriceFeedItem>> = withContext(defaultDispatcher) {
        try {
            val finalBatch = observeProgressiveFeed(
                currentUserId = currentUserId,
                initialBatchSize = totalLimit.coerceAtLeast(1),
                perFriendLimit = perFriendLimit,
                totalLimit = totalLimit,
                includeCache = false
            ).last()
            Result.success(finalBatch.items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeProgressiveFeed(
        currentUserId: String,
        initialBatchSize: Int = INITIAL_FAST_BATCH_SIZE,
        perFriendLimit: Int = 20,
        totalLimit: Int = 20,
        includeCache: Boolean = true
    ): Flow<ProgressiveFeedBatch> = channelFlow {
        if (currentUserId.isBlank()) {
            send(ProgressiveFeedBatch(items = emptyList(), isFromCache = false, isComplete = true))
            return@channelFlow
        }

        val viewerLocationDeferred = async { resolveViewerLocationOrNull() }

        val mutex = Mutex()
        val pairsByFriend = linkedMapOf<String, List<Pair<Caprice, UserProfile>>>()
        var emittedCacheBatch = false
        var lastEmittedCount = 0
        var lastEmitAtMillis = 0L

        if (includeCache && friendshipRepository != null && userProfileRepository != null) {
            val cachedPairs = loadCachedFriendCaprices(currentUserId, perFriendLimit)
            if (cachedPairs.isNotEmpty()) {
                cachedPairs.groupBy { it.first.userId }.forEach { (friendId, list) ->
                    pairsByFriend[friendId] = list
                }
                emittedCacheBatch = true
                lastEmittedCount = pairsByFriend.values.sumOf { it.size }
                lastEmitAtMillis = clockMillis()
                val fastLocation = withTimeoutOrNull(CACHE_LOCATION_TIMEOUT_MS) {
                    viewerLocationDeferred.await()
                }
                send(
                    buildSortedBatch(
                        pairs = pairsByFriend.values.flatten(),
                        viewerLocation = fastLocation,
                        totalLimit = totalLimit,
                        isFromCache = true,
                        isComplete = false
                    )
                )
            }
        }

        var emittedFirstNetworkBatch = false
        val fastThreshold = initialBatchSize.coerceAtLeast(1)

        suspend fun maybeEmitPartial(forceIfNonEmpty: Boolean = false) {
            // En refresco manual (includeCache = false) evitamos emitir un subconjunto parcial
            // de un solo amigo que encoja temporalmente la lista ya visible.
            if (!includeCache) return
            val batch = mutex.withLock {
                val currentPairs = pairsByFriend.values.flatten()
                if (currentPairs.isEmpty()) return@withLock null
                val now = clockMillis()
                val shouldEmitFirst = !emittedCacheBatch &&
                    !emittedFirstNetworkBatch &&
                    (currentPairs.size >= fastThreshold || forceIfNonEmpty)
                val shouldEmitUpdate = (emittedCacheBatch || emittedFirstNetworkBatch) &&
                    currentPairs.size != lastEmittedCount &&
                    (now - lastEmitAtMillis) >= PROGRESS_EMIT_THROTTLE_MS
                if (shouldEmitFirst || shouldEmitUpdate) {
                    emittedFirstNetworkBatch = true
                    lastEmitAtMillis = now
                    lastEmittedCount = currentPairs.size
                    val locationSnapshot = if (viewerLocationDeferred.isCompleted) {
                        runCatching { viewerLocationDeferred.getCompleted() }.getOrNull()
                    } else null
                    buildSortedBatch(
                        pairs = currentPairs,
                        viewerLocation = locationSnapshot,
                        totalLimit = totalLimit,
                        isFromCache = false,
                        isComplete = false
                    )
                } else null
            }
            if (batch != null) {
                send(batch)
            }
        }

        val earlyFlushJob = if (includeCache) {
            launch {
                delay(EARLY_BATCH_FLUSH_DELAY_MS)
                maybeEmitPartial(forceIfNonEmpty = true)
            }
        } else {
            null
        }

        val semaphore = Semaphore(MAX_CONCURRENT_FRIEND_QUERIES)
        var confirmedFriendIds: Set<String> = emptySet()
        coroutineScope {
            if (friendshipRepository != null && userProfileRepository != null) {
                val ownBlockedDeferred = async {
                    runCatching {
                        friendshipRepository.getBlockedUserIds(currentUserId).getOrDefault(emptyList()).toSet()
                    }.getOrDefault(emptySet())
                }
                val acceptedFriendships = friendshipRepository.getFriendships(currentUserId)
                    .first()
                    .filter { it.status == FriendshipStatus.ACCEPTED }
                val ownBlockedIds = ownBlockedDeferred.await()

                val candidateFriendIds = acceptedFriendships
                    .mapNotNull { friendship -> friendship.memberIDs.firstOrNull { it != currentUserId } }
                    .distinct()
                    .filter { it.isNotBlank() && it !in ownBlockedIds }
                confirmedFriendIds = candidateFriendIds.toSet()

                candidateFriendIds.map { friendId ->
                    async {
                        semaphore.withPermit {
                            val blockedDeferred = async {
                                friendshipRepository.isBlocked(currentUserId, friendId).getOrNull() ?: false
                            }
                            val profileDeferred = async {
                                userProfileRepository.getUserById(friendId).getOrNull()
                            }
                            val capricesDeferred = async {
                                capriceRepository.getCaprices(friendId, perFriendLimit).getOrDefault(emptyList())
                            }
                            if (blockedDeferred.await()) {
                                mutex.withLock { pairsByFriend.remove(friendId) }
                                return@withPermit
                            }
                            val friendProfile = profileDeferred.await()
                            if (friendProfile == null) {
                                mutex.withLock { pairsByFriend.remove(friendId) }
                                return@withPermit
                            }
                            val visible = capricesDeferred.await().filter { it.isVisibleTo(currentUserId) }
                            mutex.withLock {
                                pairsByFriend[friendId] = visible.map { it to friendProfile }
                            }
                            if (visible.isNotEmpty() || !emittedCacheBatch) {
                                maybeEmitPartial()
                            }
                        }
                    }
                }.awaitAll()
            } else {
                val acceptedFriends = getAcceptedFriendsUseCase.execute(currentUserId).first()
                confirmedFriendIds = acceptedFriends.map { it.uid }.toSet()
                acceptedFriends.map { friend ->
                    async {
                        semaphore.withPermit {
                            val visible = capriceRepository.getCaprices(friend.uid, perFriendLimit)
                                .getOrDefault(emptyList())
                                .filter { it.isVisibleTo(currentUserId) }
                            mutex.withLock {
                                pairsByFriend[friend.uid] = visible.map { it to friend }
                            }
                            if (visible.isNotEmpty()) {
                                maybeEmitPartial()
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        earlyFlushJob?.cancel()
        val finalLocation = viewerLocationDeferred.await()
        val finalPairs = mutex.withLock {
            pairsByFriend.keys.retainAll(confirmedFriendIds)
            pairsByFriend.values.flatten()
        }
        send(
            buildSortedBatch(
                pairs = finalPairs,
                viewerLocation = finalLocation,
                totalLimit = totalLimit,
                isFromCache = false,
                isComplete = true
            )
        )
    }.flowOn(defaultDispatcher)

    private suspend fun loadCachedFriendCaprices(
        currentUserId: String,
        perFriendLimit: Int
    ): List<Pair<Caprice, UserProfile>> {
        val fRepo = friendshipRepository ?: return emptyList()
        val uRepo = userProfileRepository ?: return emptyList()
        val cachedFriendships = fRepo.getCachedFriendships(currentUserId)
            .filter { it.status == FriendshipStatus.ACCEPTED }
        if (cachedFriendships.isEmpty()) return emptyList()

        val cachedBlockedIds = fRepo.getCachedBlockedUserIds(currentUserId).toSet()
        val candidateFriendIds = cachedFriendships
            .mapNotNull { friendship -> friendship.memberIDs.firstOrNull { it != currentUserId } }
            .distinct()
            .filter { it.isNotBlank() && it !in cachedBlockedIds }
        if (candidateFriendIds.isEmpty()) return emptyList()

        val semaphore = Semaphore(MAX_CONCURRENT_FRIEND_QUERIES)
        return coroutineScope {
            candidateFriendIds.map { friendId ->
                async {
                    semaphore.withPermit {
                        val blockedDeferred = async { fRepo.isCachedBlocked(currentUserId, friendId) }
                        val capricesDeferred = async { capriceRepository.getCachedCaprices(friendId, perFriendLimit) }
                        val profileDeferred = async { uRepo.getCachedUserById(friendId) }
                        if (blockedDeferred.await()) return@withPermit emptyList()
                        val caprices = capricesDeferred.await().filter { it.isVisibleTo(currentUserId) }
                        if (caprices.isEmpty()) return@withPermit emptyList()
                        val author = profileDeferred.await() ?: UserProfile(uid = friendId, username = "usuario")
                        caprices.map { it to author }
                    }
                }
            }.awaitAll().flatten()
        }
    }

    private suspend fun resolveViewerLocationOrNull(): ApproximateLocation? {
        val locRepo = locationRepository ?: return null
        if (!locRepo.hasLocationPermission()) return null
        return try {
            locRepo.currentApproximateLocation().getOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun buildSortedBatch(
        pairs: List<Pair<Caprice, UserProfile>>,
        viewerLocation: ApproximateLocation?,
        totalLimit: Int,
        isFromCache: Boolean,
        isComplete: Boolean
    ): ProgressiveFeedBatch {
        val distinctByCapriceId = pairs.distinctBy { it.first.id }
        val feedItems = distinctByCapriceId.map { (caprice, author) ->
            val cell = caprice.distanceCell()
            val distanceMeters = if (viewerLocation != null && cell != null) {
                NearbyCellGrid.distanceInMeters(viewerLocation, cell)
            } else {
                null
            }
            CapriceFeedItem(
                caprice = caprice,
                author = author,
                approximateDistanceMeters = distanceMeters
            )
        }
        return ProgressiveFeedBatch(
            items = sortFriendsFeedItems(feedItems).take(totalLimit.coerceAtLeast(1)),
            isFromCache = isFromCache,
            isComplete = isComplete
        )
    }
}
