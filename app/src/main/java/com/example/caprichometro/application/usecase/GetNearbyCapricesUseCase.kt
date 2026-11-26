package com.example.caprichometro.application.usecase

import com.example.caprichometro.domain.repository.FriendshipRepository
import com.example.caprichometro.domain.model.ApproximateLocation
import com.example.caprichometro.domain.model.Caprice
import com.example.caprichometro.domain.model.CapriceFeedItem
import com.example.caprichometro.domain.model.NearbyCellGrid
import com.example.caprichometro.domain.model.ProgressiveFeedBatch
import com.example.caprichometro.domain.model.UserProfile
import com.example.caprichometro.domain.repository.UserProfileRepository
import com.example.caprichometro.domain.model.sortedByNearbyFeedOrder
import com.example.caprichometro.domain.repository.CursorPagedNearbyCapriceRepository
import com.example.caprichometro.domain.repository.DeviceLocationRepository
import com.example.caprichometro.domain.repository.NearbyCapriceRepository
import com.example.caprichometro.domain.repository.PreferredCellsNearbyCapriceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
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

    private data class NearbyPageSnapshot(
        val candidates: List<Candidate>,
        val resolvedAuthors: ConcurrentHashMap<String, UserProfile>,
        val rawCaprices: List<Caprice> = emptyList(),
        val location: ApproximateLocation? = null,
        val serverBlockedIds: Set<String> = emptySet(),
        val checkedServerBlocks: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap(),
        val nextPageToken: String? = null
    )

    private val cachedSnapshots = ConcurrentHashMap<String, NearbyPageSnapshot>()

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
            val rawCandidates = buildSortedCandidates(
                currentUserId = currentUserId,
                location = location,
                rawCaprices = discoverableCaprices,
                blockedIds = outgoingBlockedIds
            )
            val candidates = filterBidirectionalBlocks(
                currentUserId = currentUserId,
                candidates = rawCandidates,
                useCacheOnly = false
            )
            val resolvedAuthors = ConcurrentHashMap<String, UserProfile>()
            resolveAuthorProfiles(
                candidates = candidates,
                resolvedAuthors = resolvedAuthors,
                useCacheOnlyProfiles = false
            )
            Result.success(toFeedItems(candidates, resolvedAuthors))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Emite primero caché local (`5` ítems iniciales y el resto de página hasta `pageSize`)
     * y transmite la primera página de servidor ordenando todos los candidatos sin hidratar
     * perfiles de desconocidos más allá de la página visible inicial.
     */
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
        val checkedServerBlocks = ConcurrentHashMap<String, Boolean>()
        val stepSize = initialBatchSize.coerceAtLeast(1)
        val pageLimit = pageSize.coerceAtLeast(stepSize)
        val initialLocation = locationDeferred.await()
        val preferredCells = NearbyCellGrid.nearbyCellIds(initialLocation)

        val cursorRepo = nearbyCapriceRepository as? CursorPagedNearbyCapriceRepository
        val discoverableFlow = cursorRepo?.streamFirstDiscoverableCapricePage(
            initialBatchSize = stepSize,
            pageSize = pageLimit,
            includeCache = includeCache,
            preferredCells = preferredCells
        ) ?: (nearbyCapriceRepository as? PreferredCellsNearbyCapriceRepository)
            ?.streamDiscoverableCaprices(
                initialBatchSize = stepSize,
                pageSize = pageLimit,
                includeCache = includeCache,
                preferredCells = preferredCells
            )
            ?: nearbyCapriceRepository.streamDiscoverableCaprices(
                initialBatchSize = stepSize,
                pageSize = pageLimit,
                includeCache = includeCache
            )

        discoverableFlow.collect { rawBatch ->
            val location = initialLocation
            val blockedIds = if (rawBatch.isFromCache && cachedBlockedDeferred != null) {
                cachedBlockedDeferred.await()
            } else {
                serverBlockedDeferred.await()
            }
            val rawCandidates = buildSortedCandidates(
                currentUserId = currentUserId,
                location = location,
                rawCaprices = rawBatch.caprices,
                blockedIds = blockedIds
            )
            val candidates = filterBidirectionalBlocks(
                currentUserId = currentUserId,
                candidates = rawCandidates,
                useCacheOnly = rawBatch.isFromCache,
                memoizedServerBlocks = if (rawBatch.isFromCache) null else checkedServerBlocks
            )
            cachedSnapshots[currentUserId] = NearbyPageSnapshot(
                candidates = candidates,
                resolvedAuthors = resolvedAuthors,
                rawCaprices = rawBatch.caprices,
                location = location,
                serverBlockedIds = if (rawBatch.isFromCache) emptySet() else blockedIds,
                checkedServerBlocks = checkedServerBlocks,
                nextPageToken = if (rawBatch.isFromCache) null else rawBatch.nextPageToken
            )

            val visibleTarget = minOf(pageLimit, candidates.size)
            if (rawBatch.isFromCache) {
                if (candidates.isEmpty()) return@collect
                var prevCursor = 0
                var cursor = minOf(stepSize, visibleTarget)
                while (true) {
                    resolveAuthorProfiles(
                        candidates = candidates.subList(prevCursor, cursor),
                        resolvedAuthors = resolvedAuthors,
                        useCacheOnlyProfiles = true
                    )
                    send(
                        ProgressiveFeedBatch(
                            items = toFeedItems(candidates.subList(0, cursor), resolvedAuthors),
                            isFromCache = true,
                            isComplete = false,
                            totalAvailableCount = candidates.size
                        )
                    )
                    if (cursor >= visibleTarget) break
                    prevCursor = cursor
                    cursor = minOf(cursor + stepSize, visibleTarget)
                }
            } else {
                val slice = candidates.subList(0, visibleTarget)
                resolveAuthorProfiles(
                    candidates = slice,
                    resolvedAuthors = resolvedAuthors,
                    useCacheOnlyProfiles = false
                )
                val hasRemoteCursor = rawBatch.nextPageToken != null
                val isFirstPageDone = if (cursorRepo != null) {
                    rawBatch.isComplete || hasRemoteCursor
                } else {
                    rawBatch.isComplete
                }
                send(
                    ProgressiveFeedBatch(
                        items = toFeedItems(slice, resolvedAuthors),
                        isFromCache = false,
                        isComplete = isFirstPageDone,
                        totalAvailableCount = candidates.size,
                        hasMoreRemote = hasRemoteCursor
                    )
                )
            }
        }
    }.flowOn(ioDispatcher)

    /**
     * Hidrata bajo demanda la siguiente página de 20 elementos desde memoria o avanzando el cursor
     * remoto en Firestore (`CursorPagedNearbyCapriceRepository`) en tramos rápidos de 5 en 5.
     */
    fun observeNextPage(
        currentUserId: String,
        currentLoadedCount: Int,
        initialBatchSize: Int = INITIAL_FAST_BATCH_SIZE,
        pageSize: Int = PAGE_SIZE
    ): Flow<ProgressiveFeedBatch> = flow {
        val initialSnapshot = cachedSnapshots[currentUserId]
        if (initialSnapshot == null) {
            emit(
                ProgressiveFeedBatch(
                    items = emptyList(),
                    isFromCache = false,
                    isComplete = true,
                    totalAvailableCount = 0
                )
            )
            return@flow
        }

        var workingSnapshot = initialSnapshot
        val cursorRepo = nearbyCapriceRepository as? CursorPagedNearbyCapriceRepository
        if (currentLoadedCount >= workingSnapshot.candidates.size) {
            val token = workingSnapshot.nextPageToken
            val location = workingSnapshot.location
            if (cursorRepo != null && token != null && location != null) {
                val remoteBatch = cursorRepo.loadNextDiscoverableCapricePage(token, pageSize).getOrThrow()
                val existingIds = workingSnapshot.rawCaprices.map { it.id }.toHashSet()
                val mergedRaw = workingSnapshot.rawCaprices + remoteBatch.caprices.filter { existingIds.add(it.id) }
                val mergedRawCandidates = buildSortedCandidates(
                    currentUserId = currentUserId,
                    location = location,
                    rawCaprices = mergedRaw,
                    blockedIds = workingSnapshot.serverBlockedIds
                )
                val mergedCandidates = filterBidirectionalBlocks(
                    currentUserId = currentUserId,
                    candidates = mergedRawCandidates,
                    useCacheOnly = false,
                    memoizedServerBlocks = workingSnapshot.checkedServerBlocks
                )
                workingSnapshot = workingSnapshot.copy(
                    candidates = mergedCandidates,
                    rawCaprices = mergedRaw,
                    nextPageToken = remoteBatch.nextPageToken
                )
                cachedSnapshots[currentUserId] = workingSnapshot
            }
        }

        if (currentLoadedCount >= workingSnapshot.candidates.size) {
            val currentList = toFeedItems(workingSnapshot.candidates, workingSnapshot.resolvedAuthors)
            emit(
                ProgressiveFeedBatch(
                    items = currentList,
                    isFromCache = false,
                    isComplete = true,
                    totalAvailableCount = currentList.size,
                    hasMoreRemote = workingSnapshot.nextPageToken != null
                )
            )
            return@flow
        }

        val stepSize = initialBatchSize.coerceAtLeast(1)
        val pageEnd = (currentLoadedCount + pageSize.coerceAtLeast(1)).coerceAtMost(workingSnapshot.candidates.size)
        var cursor = (currentLoadedCount + stepSize).coerceAtMost(pageEnd)
        while (true) {
            resolveAuthorProfiles(
                candidates = workingSnapshot.candidates.subList(0, cursor),
                resolvedAuthors = workingSnapshot.resolvedAuthors,
                useCacheOnlyProfiles = false
            )
            val isLast = cursor >= pageEnd
            emit(
                ProgressiveFeedBatch(
                    items = toFeedItems(workingSnapshot.candidates.subList(0, cursor), workingSnapshot.resolvedAuthors),
                    isFromCache = false,
                    isComplete = isLast,
                    totalAvailableCount = workingSnapshot.candidates.size,
                    hasMoreRemote = isLast && workingSnapshot.nextPageToken != null
                )
            )
            if (isLast) break
            cursor = (cursor + stepSize).coerceAtMost(pageEnd)
        }
    }.flowOn(ioDispatcher)

    private fun buildSortedCandidates(
        currentUserId: String,
        location: ApproximateLocation,
        rawCaprices: List<Caprice>,
        blockedIds: Set<String>
    ): List<Candidate> {
        val candidates = rawCaprices.mapNotNull { caprice ->
            val cell = caprice.nearbyCell?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (caprice.userId == currentUserId || caprice.userId.isBlank()) return@mapNotNull null
            if (caprice.userId in blockedIds) return@mapNotNull null
            val distance = NearbyCellGrid.distanceInMeters(location, cell) ?: return@mapNotNull null
            Candidate(caprice = caprice, distanceMeters = distance)
        }
        return sortedByNearbyFeedOrder(
            items = candidates,
            distanceOf = { it.distanceMeters },
            capriceOf = { it.caprice }
        )
    }

    private suspend fun filterBidirectionalBlocks(
        currentUserId: String,
        candidates: List<Candidate>,
        useCacheOnly: Boolean,
        memoizedServerBlocks: ConcurrentHashMap<String, Boolean>? = null
    ): List<Candidate> {
        val repo = friendshipRepository ?: return candidates
        if (candidates.isEmpty()) return candidates
        val authorIds = candidates.map { it.caprice.userId }.distinct()
        val semaphore = Semaphore(MAX_CONCURRENT_AUTHOR_QUERIES)
        val blockedAuthors = coroutineScope {
            authorIds.map { authorId ->
                async(ioDispatcher) {
                    if (!useCacheOnly && memoizedServerBlocks != null) {
                        val cached = memoizedServerBlocks[authorId]
                        if (cached != null) return@async if (cached) authorId else null
                    }
                    semaphore.withPermit {
                        val blocked = if (useCacheOnly) {
                            repo.isCachedBlocked(currentUserId, authorId)
                        } else {
                            repo.isBlocked(currentUserId, authorId).getOrDefault(true).also {
                                memoizedServerBlocks?.put(authorId, it)
                            }
                        }
                        if (blocked) authorId else null
                    }
                }
            }.awaitAll().filterNotNull().toSet()
        }
        return if (blockedAuthors.isEmpty()) candidates else candidates.filterNot { it.caprice.userId in blockedAuthors }
    }

    private suspend fun resolveAuthorProfiles(
        candidates: List<Candidate>,
        resolvedAuthors: ConcurrentHashMap<String, UserProfile>,
        useCacheOnlyProfiles: Boolean
    ) {
        val missingAuthorIds = candidates
            .map { it.caprice.userId }
            .distinct()
            .filter { !resolvedAuthors.containsKey(it) }
        if (missingAuthorIds.isEmpty()) return

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

    private fun toFeedItems(
        candidates: List<Candidate>,
        resolvedAuthors: ConcurrentHashMap<String, UserProfile>
    ): List<CapriceFeedItem> {
        return candidates.map { candidate ->
            CapriceFeedItem(
                caprice = candidate.caprice,
                author = resolvedAuthors[candidate.caprice.userId]
                    ?: UserProfile(uid = candidate.caprice.userId, username = "usuario"),
                canOpenAuthorProfile = false,
                approximateDistanceMeters = candidate.distanceMeters
            )
        }
    }
}
